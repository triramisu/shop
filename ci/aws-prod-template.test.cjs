/* Production infrastructure contracts only; no AWS calls or cloud-init execution. */
"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { parseCfn } = require("./lib/cfn-yaml.cjs");

const source = fs.readFileSync(path.join(__dirname, "../deploy/aws/shop-prod.yaml"), "utf8");
const template = parseCfn(source);
const resources = template.Resources;
const props = id => resources[id].Properties;
const ref = id => ({ Ref: id });
const ofType = type => Object.values(resources).filter(r => r.Type === type);

test("application subnets use one NAT and isolated DB subnets have no Internet route", () => {
  assert.equal(ofType("AWS::EC2::NatGateway").length, 1);
  for (const id of ["AppDefaultRouteA", "AppDefaultRouteB"])
    assert.deepEqual(props(id).NatGatewayId, ref("NatGateway"));
  for (const id of ["AppSubnetA", "AppSubnetB", "DbSubnetA", "DbSubnetB"])
    assert.equal(props(id).MapPublicIpOnLaunch, false);
  for (const route of ofType("AWS::EC2::Route"))
    assert.notDeepEqual(route.Properties.RouteTableId, ref("DbRouteTable"));
  assert.deepEqual(props("S3GatewayEndpoint").RouteTableIds,
    [ref("AppRouteTableA"), ref("AppRouteTableB"), ref("PublicRouteTable")]);
});

test("security groups have no world-open ingress and app/DB trust only intended groups", () => {
  for (const group of ofType("AWS::EC2::SecurityGroup")) {
    for (const rule of group.Properties.SecurityGroupIngress || []) {
      assert.notEqual(rule.CidrIp, "0.0.0.0/0");
      assert.notEqual(rule.CidrIpv6, "::/0");
    }
  }
  assert.deepEqual(props("AppSecurityGroup").SecurityGroupIngress.map(r => [r.FromPort, r.SourceSecurityGroupId]),
    [[8080, ref("AlbSecurityGroup")], [22, ref("BastionSecurityGroup")]]);
  assert.deepEqual(props("DbSecurityGroup").SecurityGroupIngress.map(r => [r.FromPort, r.SourceSecurityGroupId]),
    [[3306, ref("AppSecurityGroup")], [3306, ref("BastionSecurityGroup")]]);
  assert.deepEqual(props("BastionSecurityGroup").SecurityGroupIngress[0].CidrIp, ref("AdminCidr"));
});

test("ALB accepts only CloudFront HTTPS and a verified origin header", () => {
  const inbound = props("AlbSecurityGroup").SecurityGroupIngress;
  assert.equal(inbound.length, 1);
  assert.equal(inbound[0].FromPort, 443);
  assert.equal(inbound[0].ToPort, 443);
  assert.deepEqual(inbound[0].SourcePrefixListId, ref("CloudFrontPrefixListId"));
  const listener = props("HttpsListener");
  assert.equal(listener.Protocol, "HTTPS");
  assert.deepEqual(listener.Certificates, [{ CertificateArn: ref("AlbCertificateArn") }]);
  assert.equal(listener.DefaultActions[0].FixedResponseConfig.StatusCode, "403");
  const rule = props("VerifiedOriginListenerRule");
  assert.equal(rule.Conditions[0].HttpHeaderConfig.HttpHeaderName, "X-Shop-Origin-Verify");
  assert.match(JSON.stringify(rule.Conditions[0].HttpHeaderConfig.Values), /resolve:secretsmanager/);
  assert.deepEqual(rule.Actions[0].TargetGroupArn, ref("ApplicationTargetGroup"));
});

test("ASG spans both private app AZs, bounds capacity and uses real app readiness", () => {
  const asg = props("ApplicationAutoScalingGroup");
  assert.equal(Number(asg.MinSize), 2);
  assert.equal(Number(asg.DesiredCapacity), 2);
  assert.deepEqual(asg.VPCZoneIdentifier, [ref("AppSubnetA"), ref("AppSubnetB")]);
  assert.deepEqual(template.Parameters.MaxAppInstances.AllowedValues, [2, 3, 4]);
  assert.deepEqual(asg.MaxSize, ref("MaxAppInstances"));
  assert.equal(template.Parameters.CpuTargetPercent.Default, 60);
  assert.deepEqual(asg.TargetGroupARNs, [ref("ApplicationTargetGroup")]);
  assert.deepEqual(asg.HealthCheckType, { "Fn::If": ["StartApplication", "ELB", "EC2"] });
  assert.equal(props("ApplicationTargetGroup").HealthCheckPath, "/actuator/health");
  assert.equal(props("ApplicationLaunchTemplate").LaunchTemplateData.NetworkInterfaces[0].AssociatePublicIpAddress, false);
});

test("prod requires an immutable image and separates bootstrap phases from Spring profiles", () => {
  const pattern = new RegExp(template.Parameters.ApplicationImage.AllowedPattern);
  assert.equal(pattern.test("t1r4m15u/shop:latest"), false);
  assert.equal(pattern.test(`t1r4m15u/shop@sha256:${"a".repeat(64)}`), true);
  assert.deepEqual(template.Parameters.DeploymentMode.AllowedValues, ["foundation", "application"]);
  assert.equal(template.Rules.ApplicationRequiresArtifacts.Assertions.length, 2);
  const bootstrap = props("ApplicationLaunchTemplate").LaunchTemplateData.UserData["Fn::Base64"]["Fn::Sub"];
  assert.ok(bootstrap.includes("SPRING_PROFILES_ACTIVE=prod"));
  assert.ok(bootstrap.includes("OBJECT_STORAGE_TYPE=s3"));
  assert.ok(!bootstrap.includes("OBJECT_STORAGE_TYPE=minio"));
});

test("RDS stays private, encrypted, TLS-only and protected by snapshots/backups", () => {
  const db = props("Database");
  assert.equal(db.Engine, "mysql");
  assert.equal(db.PubliclyAccessible, false);
  assert.equal(db.StorageEncrypted, true);
  assert.equal(db.ManageMasterUserPassword, true);
  assert.equal(db.DeletionProtection, true);
  assert.equal(db.BackupRetentionPeriod, 1);
  assert.equal(db.CopyTagsToSnapshot, true);
  assert.equal(resources.Database.DeletionPolicy, "Snapshot");
  assert.equal(resources.Database.UpdateReplacePolicy, "Snapshot");
  assert.equal(props("DbParameterGroup").Parameters.require_secure_transport, "1");
  const appRole = JSON.stringify(props("ApplicationRole"));
  assert.ok(!appRole.includes("MasterUserSecret"));
  assert.ok(!appRole.includes("secretsmanager:*"));
});

for (const id of ["UiBucket", "ProductImagesBucket"]) {
  test(`${id} remains private, encrypted, versioned and retained`, () => {
    assert.ok(Object.values(props(id).PublicAccessBlockConfiguration).every(v => v === true));
    assert.ok(props(id).BucketEncryption);
    assert.equal(props(id).VersioningConfiguration.Status, "Enabled");
    assert.equal(resources[id].DeletionPolicy, "Retain");
    assert.equal(resources[id].UpdateReplacePolicy, "Retain");
  });
}

test("UI origin uses signed OAC access scoped to this CloudFront distribution", () => {
  const allow = props("UiBucketPolicy").PolicyDocument.Statement.find(s => s.Effect === "Allow");
  assert.equal(allow.Principal.Service, "cloudfront.amazonaws.com");
  assert.match(JSON.stringify(allow.Condition.StringEquals["AWS:SourceArn"]), /UiDistribution/);
  assert.equal(props("UiOriginAccessControl").OriginAccessControlConfig.SigningBehavior, "always");
});

test("product image permissions remain scoped to the product prefix", () => {
  const policy = props("ApplicationRole").Policies.find(p => p.PolicyName === "ProductImagesOnly");
  const rules = policy.PolicyDocument.Statement;
  assert.equal(rules[0].Action, "s3:ListBucket");
  assert.deepEqual(rules[0].Resource, { "Fn::GetAtt": ["ProductImagesBucket", "Arn"] });
  assert.deepEqual(rules[1].Action, ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]);
  assert.deepEqual(rules[1].Resource, { "Fn::Sub": "${ProductImagesBucket.Arn}/catalog/products/*" });
  assert.ok(!JSON.stringify(policy).includes("UiBucket"));
});

test("CloudFront keeps separate private UI and HTTPS API origins without caching APIs", () => {
  const distribution = props("UiDistribution").DistributionConfig;
  assert.equal(distribution.Origins.length, 2);
  const api = distribution.Origins.find(o => o.Id === "shop-api");
  assert.equal(api.CustomOriginConfig.OriginProtocolPolicy, "https-only");
  assert.equal(api.OriginCustomHeaders[0].HeaderName, "X-Shop-Origin-Verify");
  assert.equal(distribution.CustomErrorResponses, undefined);
  assert.deepEqual(distribution.CacheBehaviors.map(b => b.PathPattern), ["/api", "/api/*"]);
  for (const behavior of distribution.CacheBehaviors) {
    assert.equal(behavior.CachePolicyId, "4135ea2d-6df8-44a3-9df3-4b5a84be39ad");
    assert.equal(behavior.ViewerProtocolPolicy, "https-only");
    assert.equal(behavior.AllowedMethods.length, 7);
    assert.deepEqual(behavior.OriginRequestPolicyId, ref("ApiOriginRequestPolicy"));
  }
  const requestPolicy = props("ApiOriginRequestPolicy").OriginRequestPolicyConfig;
  assert.equal(requestPolicy.CookiesConfig.CookieBehavior, "all");
  assert.equal(requestPolicy.QueryStringsConfig.QueryStringBehavior, "all");
  assert.deepEqual(requestPolicy.HeadersConfig, { HeaderBehavior: "allExcept", Headers: ["host"] });
});
