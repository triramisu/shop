/* Run with Node >=20; provide js-yaml via SHOP_YAML_MODULE if not installed.
 * No AWS credentials, SDK calls, deployment or traffic generation are used.
 */
"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const os = require("node:os");
const vm = require("node:vm");
const { spawnSync } = require("node:child_process");
const yaml = require(process.env.SHOP_YAML_MODULE || "js-yaml");
const root = path.resolve(__dirname, "..");
const source = fs.readFileSync(
  path.join(root, "deploy/aws/shop-lab.yaml"),
  "utf8",
);
const intrinsic = {
  Ref: "Ref",
  Sub: "Fn::Sub",
  GetAtt: "Fn::GetAtt",
  GetAZs: "Fn::GetAZs",
  Select: "Fn::Select",
  Split: "Fn::Split",
  Join: "Fn::Join",
  If: "Fn::If",
  Equals: "Fn::Equals",
  Not: "Fn::Not",
};
const types = Object.entries(intrinsic).flatMap(([tag, key]) =>
  ["scalar", "sequence"].map(
    (kind) =>
      new yaml.Type(`!${tag}`, {
        kind,
        construct: (value) => ({
          [key]:
            tag === "GetAtt" && typeof value === "string"
              ? value.split(".")
              : value,
        }),
      }),
  ),
);
const template = yaml.load(source, {
  schema: yaml.DEFAULT_SCHEMA.extend(types),
});
const resources = template.Resources;
const props = (id) => resources[id].Properties;
const ref = (id) => ({ Ref: id });
const userData = props("ApplicationLaunchTemplate").LaunchTemplateData.UserData[
  "Fn::Base64"
]["Fn::Sub"];
const bastionUserData = props("BastionInstance").UserData["Fn::Base64"];
const ubuntuAmiParameter =
  "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id";
const bashCheck = {
  skip: !process.env.SHOP_BASH
    ? "Set SHOP_BASH to execute isolated Ubuntu bootstrap checks"
    : false,
};
function section(script, name) {
  return script.split(`# BEGIN ${name}\n`)[1].split(`# END ${name}`)[0];
}
function withAptFixtures(run) {
  const directory = fs.mkdtempSync(
    path.join(os.tmpdir(), "shop-ubuntu-check-"),
  );
  const files = {
    list: path.join(directory, "sources.list"),
    sources: path.join(directory, "ubuntu.sources"),
  };
  fs.writeFileSync(
    files.list,
    "# preserved comment\ndeb http://archive.ubuntu.com/ubuntu noble main universe\n",
  );
  fs.writeFileSync(
    files.sources,
    "Types: deb\nURIs: http://ap-southeast-1.ec2.archive.ubuntu.com/ubuntu/\nSuites: noble noble-updates\nComponents: main universe\nSigned-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\n\nTypes: deb\nURIs: http://security.ubuntu.com/ubuntu/\nSuites: noble-security\nComponents: main universe\nSigned-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\n",
  );
  try {
    return run(files);
  } finally {
    // Only remove this test's own unique temporary fixture directory.
    fs.rmSync(directory, { recursive: true });
  }
}
function isolateUbuntuScript(script) {
  return script
    .replaceAll(
      ". /etc/os-release",
      'ID="$SHOP_TEST_OS_ID"; VERSION_ID="$SHOP_TEST_OS_VERSION"',
    )
    .replaceAll(
      "for source_file in /etc/apt/sources.list /etc/apt/sources.list.d/ubuntu.sources; do",
      'for source_file in "$SHOP_TEST_APT_LIST" "$SHOP_TEST_APT_SOURCES"; do',
    );
}
function runIsolatedBash(script, files, extra = {}) {
  return spawnSync(process.env.SHOP_BASH, ["--noprofile", "--norc"], {
    input: isolateUbuntuScript(script),
    encoding: "utf8",
    timeout: 10000,
    env: {
      ...process.env,
      SHOP_TEST_OS_ID: "ubuntu",
      SHOP_TEST_OS_VERSION: "24.04",
      SHOP_TEST_APT_LIST: files.list.replaceAll("\\", "/"),
      SHOP_TEST_APT_SOURCES: files.sources.replaceAll("\\", "/"),
      ...extra,
    },
  });
}
const walk = (value, visit) => {
  if (!value || typeof value !== "object") return;
  visit(value);
  Object.values(value).forEach((child) => walk(child, visit));
};

test("valid template shape, bounded upload size and known intrinsic references", () => {
  assert.equal(template.AWSTemplateFormatVersion, "2010-09-09");
  const adminPattern = new RegExp(
    `^(?:${template.Parameters.AdminCidr.AllowedPattern})$`,
  );
  const labPattern = new RegExp(
    template.Parameters.LabClientIpv4.AllowedPattern,
  );
  assert.ok(adminPattern.test("203.0.113.10/32"));
  for (const bad of ["0.0.0.0/0", "0.0.0.0/32", "999.1.2.3/32", "1.2.3.4/24"])
    assert.equal(adminPattern.test(bad), false);
  assert.ok(labPattern.test("198.51.100.20"));
  for (const bad of ["0.0.0.0", "999.1.2.3", "1.2.3.4/32"])
    assert.equal(labPattern.test(bad), false);
  assert.ok(Buffer.byteLength(source) < 51200);
  const known = new Set([
    ...Object.keys(resources),
    ...Object.keys(template.Parameters),
  ]);
  walk(template, (value) => {
    if (value.Ref)
      assert.ok(
        known.has(value.Ref) || value.Ref.startsWith("AWS::"),
        `Unknown Ref ${value.Ref}`,
      );
    if (value["Fn::GetAtt"])
      assert.ok(resources[value["Fn::GetAtt"][0]], "Unknown GetAtt resource");
    if (value["Fn::If"])
      assert.ok(template.Conditions[value["Fn::If"][0]], "Unknown condition");
  });
  for (const resource of Object.values(resources)) {
    for (const dep of [resource.DependsOn || []].flat())
      assert.ok(resources[dep], `Unknown DependsOn ${dep}`);
  }
});

test("resource dependencies have no cycles, including Fn::Sub attributes", () => {
  const graph = {};
  for (const [id, resource] of Object.entries(resources)) {
    const dependencies = new Set([resource.DependsOn || []].flat());
    walk(resource.Properties, (value) => {
      if (resources[value.Ref]) dependencies.add(value.Ref);
      if (value["Fn::GetAtt"]) dependencies.add(value["Fn::GetAtt"][0]);
      if (typeof value["Fn::Sub"] === "string") {
        for (const match of value["Fn::Sub"].matchAll(
          /\$\{([A-Za-z][A-Za-z0-9]*)(?:\.[^}]+)?\}/g,
        )) {
          if (resources[match[1]]) dependencies.add(match[1]);
        }
      }
    });
    graph[id] = dependencies;
  }
  const done = new Set();
  function visit(id, chain = []) {
    assert.ok(
      !chain.includes(id),
      `Resource cycle: ${[...chain, id].join(" -> ")}`,
    );
    if (done.has(id)) return;
    for (const dep of graph[id]) visit(dep, [...chain, id]);
    done.add(id);
  }
  Object.keys(graph).forEach((id) => visit(id));
});

test("both EC2 hosts use Canonical Ubuntu 24.04, correct root device and ubuntu SSH documentation", () => {
  assert.equal(template.Parameters.LatestAmiId.Default, ubuntuAmiParameter);
  const bastion = props("BastionInstance");
  const app = props("ApplicationLaunchTemplate").LaunchTemplateData;
  for (const instance of [bastion, app]) {
    assert.deepEqual(instance.ImageId, ref("LatestAmiId"));
    assert.equal(instance.BlockDeviceMappings[0].DeviceName, "/dev/sda1");
    assert.equal(instance.BlockDeviceMappings[0].Ebs.Encrypted, true);
  }
  assert.ok(!source.includes("al2023"));
  assert.ok(!source.includes("dnf install"));
  assert.ok(!source.includes("amazon-corretto"));
  assert.ok(!source.includes("ec2-user"));
  assert.ok(userData.includes("snap install aws-cli --classic"));
  assert.ok(userData.includes("openjdk-21-jre-headless"));
  assert.ok(userData.includes("DPkg::Lock::Timeout=180"));
  assert.ok(!userData.includes("usermod"));
  for (const id of ["BastionSecurityGroup", "AppSecurityGroup"]) {
    assert.ok(props(id).SecurityGroupEgress.every((r) => r.FromPort !== 80));
  }
  const guide = fs.readFileSync(
    path.join(root, "deploy/aws/README.md"),
    "utf8",
  );
  assert.ok(guide.includes("user **`ubuntu`**"));
  assert.ok(guide.includes("sudo apt-get install"));
  assert.ok(!guide.includes("sudo dnf"));
  const parameters = JSON.parse(
    fs.readFileSync(
      path.join(root, "deploy/aws/parameters.example.json"),
      "utf8",
    ),
  );
  assert.equal(
    parameters.find((p) => p.ParameterKey === "LatestAmiId").ParameterValue,
    ubuntuAmiParameter,
  );
});

test(
  "Ubuntu guard accepts 24.04 only and rejects wrong distro/release",
  bashCheck,
  () => {
    assert.equal(
      section(userData, "UBUNTU OS GUARD"),
      section(bastionUserData, "UBUNTU OS GUARD"),
    );
    withAptFixtures((files) => {
      const script = `set -Eeuo pipefail\n${section(userData, "UBUNTU OS GUARD")}`;
      assert.equal(runIsolatedBash(script, files).status, 0);
      for (const extra of [
        { SHOP_TEST_OS_ID: "amzn", SHOP_TEST_OS_VERSION: "2023" },
        { SHOP_TEST_OS_ID: "ubuntu", SHOP_TEST_OS_VERSION: "22.04" },
        { SHOP_TEST_OS_ID: "ubuntu", SHOP_TEST_OS_VERSION: "26.04" },
      ]) {
        const result = runIsolatedBash(script, files, extra);
        assert.equal(result.status, 1);
        assert.match(result.stderr, /requires Ubuntu Server 24\.04/);
      }
    });
  },
);

test(
  "APT migration handles regional/standard/security mirrors, is idempotent and rejects unknown HTTP",
  bashCheck,
  () => {
    assert.equal(
      section(userData, "UBUNTU APT HTTPS"),
      section(bastionUserData, "UBUNTU APT HTTPS"),
    );
    withAptFixtures((files) => {
      const script = `set -Eeuo pipefail\n${section(userData, "UBUNTU APT HTTPS")}`;
      const first = runIsolatedBash(script, files);
      assert.equal(first.status, 0, first.stderr);
      const list = fs.readFileSync(files.list, "utf8");
      const sources = fs.readFileSync(files.sources, "utf8");
      assert.ok(list.includes("https://archive.ubuntu.com/ubuntu"));
      assert.ok(sources.includes("https://archive.ubuntu.com/ubuntu/"));
      assert.ok(sources.includes("https://security.ubuntu.com/ubuntu/"));
      assert.ok(
        sources.includes(
          "Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg",
        ),
      );
      assert.ok(!sources.includes("http://"));
      assert.equal(runIsolatedBash(script, files).status, 0);
      assert.equal(fs.readFileSync(files.sources, "utf8"), sources);
      fs.writeFileSync(
        files.list,
        "deb http://unknown.example/ubuntu noble main\n",
      );
      const rejected = runIsolatedBash(script, files);
      assert.equal(rejected.status, 1);
      assert.match(rejected.stderr, /APT source still uses HTTP/);
    });
  },
);

function runFoundationMock(files, failApt = false) {
  // Stub every network/privileged command. Real commands are only Bash,
  // sed/grep against this test's own temporary files. No AWS/EC2 mutations.
  const mocks = `
    exec 3>&1
    aws_ready=0
    aws() {
      printf 'CALL aws %s\\n' "$*" >&3
      if [ "$1" = --version ]; then
        if [ "$aws_ready" -ne 1 ]; then return 1; fi
        printf 'aws-cli/2.0.0 mock\\n'
      fi
    }
    snap() { printf 'CALL snap %s\\n' "$*" >&3; aws_ready=1; }
    curl() {
      case "$*" in
        *169.254.169.254/latest/api/token*) printf 'test-token';;
        *169.254.169.254/latest/meta-data/instance-id*) printf 'i-test-only';;
        *) printf 'Unexpected outbound curl' >&2; return 99;;
      esac
    }
    apt-get() {
      printf 'CALL apt-get %s\\n' "$*" >&3
      if [ "$SHOP_TEST_APT_FAIL" = yes ]; then return 42; fi
    }
    systemctl() { printf 'CALL systemctl %s\\n' "$*" >&3; }
    docker() { printf 'Unexpected Docker operation' >&2; return 99; }
  `;
  const script = userData
    .replaceAll("${DeploymentMode}", "foundation")
    .replaceAll("${AWS::Region}", "ap-southeast-1")
    .replaceAll("${AWS::StackName}", "shop-lab-test")
    .replaceAll("${ConfigurationRevision}", "test");
  return runIsolatedBash(`${mocks}\n${script}`, files, {
    SHOP_TEST_APT_FAIL: failApt ? "yes" : "no",
  });
}

test(
  "Ubuntu foundation boot installs CLI before APT and signals success without starting Shop",
  bashCheck,
  () => {
    withAptFixtures((files) => {
      const result = runFoundationMock(files);
      assert.equal(result.status, 0, result.stderr);
      assert.match(result.stdout, /CALL snap install aws-cli --classic/);
      assert.match(
        result.stdout,
        /CALL apt-get .*install -y --no-install-recommends docker\.io/,
      );
      assert.ok(
        result.stdout.indexOf("CALL snap install") <
          result.stdout.indexOf("CALL apt-get"),
      );
      assert.match(result.stdout, /CALL systemctl enable --now docker/);
      assert.match(result.stdout, /signal-resource .*--status SUCCESS/);
      assert.ok(!result.stdout.includes("openjdk-21"));
      assert.match(result.stdout, /no Shop application or fake health server/);
    });
  },
);

test(
  "Ubuntu bootstrap package failure emits failure signal and preserves exit code",
  bashCheck,
  () => {
    withAptFixtures((files) => {
      const result = runFoundationMock(files, true);
      assert.equal(result.status, 42, result.stderr);
      assert.match(result.stdout, /signal-resource .*--status FAILURE/);
      assert.ok(!result.stdout.includes("--status SUCCESS"));
      assert.ok(!result.stdout.includes("CALL systemctl"));
    });
  },
);

test("one NAT; application subnets private; database subnets isolated", () => {
  assert.equal(
    Object.values(resources).filter((r) => r.Type === "AWS::EC2::NatGateway")
      .length,
    1,
  );
  assert.deepEqual(props("AppDefaultRouteA").NatGatewayId, ref("NatGateway"));
  assert.deepEqual(props("AppDefaultRouteB").NatGatewayId, ref("NatGateway"));
  for (const id of ["AppSubnetA", "AppSubnetB", "DbSubnetA", "DbSubnetB"])
    assert.equal(props(id).MapPublicIpOnLaunch, false);
  for (const r of Object.values(resources).filter(
    (r) => r.Type === "AWS::EC2::Route",
  )) {
    assert.notDeepEqual(r.Properties.RouteTableId, ref("DbRouteTable"));
  }
  assert.deepEqual(props("S3GatewayEndpoint").RouteTableIds, [
    ref("AppRouteTableA"),
    ref("AppRouteTableB"),
    ref("PublicRouteTable"),
  ]);
});

test("no world-open SSH, application or database ingress; ALB only CloudFront HTTPS", () => {
  for (const r of Object.values(resources).filter(
    (r) => r.Type === "AWS::EC2::SecurityGroup",
  )) {
    for (const rule of r.Properties.SecurityGroupIngress || []) {
      assert.notEqual(rule.CidrIp, "0.0.0.0/0");
      assert.notEqual(rule.CidrIpv6, "::/0");
    }
  }
  assert.deepEqual(
    props("BastionSecurityGroup").SecurityGroupIngress[0].CidrIp,
    ref("AdminCidr"),
  );
  const alb = props("AlbSecurityGroup").SecurityGroupIngress;
  assert.equal(alb.length, 1);
  assert.equal(alb[0].FromPort, 443);
  assert.deepEqual(alb[0].SourcePrefixListId, ref("CloudFrontPrefixListId"));
  assert.ok(
    props("DbSecurityGroup").SecurityGroupIngress.every(
      (r) => r.SourceSecurityGroupId && r.FromPort === 3306,
    ),
  );
});

test("ASG bounded 2–4, CPU target 60%, real app health and immutable artifact requirements", () => {
  const asg = props("ApplicationAutoScalingGroup");
  assert.equal(asg.MinSize, "2");
  assert.deepEqual(
    template.Parameters.MaxAppInstances.AllowedValues,
    [2, 3, 4],
  );
  assert.equal(template.Parameters.CpuTargetPercent.Default, 60);
  assert.deepEqual(asg.VPCZoneIdentifier, [
    ref("AppSubnetA"),
    ref("AppSubnetB"),
  ]);
  assert.deepEqual(asg.HealthCheckType, {
    "Fn::If": ["StartApplication", "ELB", "EC2"],
  });
  assert.equal(resources.CpuTargetTrackingPolicy.Condition, "StartApplication");
  assert.equal(
    props("ApplicationTargetGroup").HealthCheckPath,
    "/actuator/health",
  );
  assert.equal(
    template.Rules.ApplicationRequiresArtifacts.Assertions.length,
    2,
  );
  const pattern = new RegExp(
    template.Parameters.ApplicationImage.AllowedPattern,
  );
  assert.equal(pattern.test("t1r4m15u/shop:latest"), false);
  assert.equal(pattern.test(`t1r4m15u/shop@sha256:${"a".repeat(64)}`), true);
  const launch = props("ApplicationLaunchTemplate").LaunchTemplateData;
  assert.equal(launch.NetworkInterfaces[0].AssociatePublicIpAddress, false);
  assert.equal(launch.MetadataOptions.HttpTokens, "required");
  assert.equal(launch.BlockDeviceMappings[0].Ebs.Encrypted, true);
  assert.equal(launch.CreditSpecification.CpuCredits, "standard");
});

test("RDS lab keeps one-day automated backups and snapshots on deletion or replacement", () => {
  assert.equal(props("Database").BackupRetentionPeriod, 1);
  assert.equal(props("Database").CopyTagsToSnapshot, true);
  assert.equal(resources.Database.DeletionPolicy, "Snapshot");
  assert.equal(resources.Database.UpdateReplacePolicy, "Snapshot");
});

test("RDS encrypted, private, TLS-only, managed master and separate app secret access", () => {
  const db = props("Database");
  assert.equal(db.Engine, "mysql");
  assert.equal(db.MultiAZ, false);
  assert.equal(db.PubliclyAccessible, false);
  assert.equal(db.StorageEncrypted, true);
  assert.equal(db.ManageMasterUserPassword, true);
  assert.equal(
    db.EngineLifecycleSupport,
    "open-source-rds-extended-support-disabled",
  );
  assert.equal(
    props("DbParameterGroup").Parameters.require_secure_transport,
    "1",
  );
  assert.equal(resources.Database.DeletionPolicy, "Snapshot");
  const appPolicies = JSON.stringify(props("ApplicationRole"));
  assert.ok(!appPolicies.includes("MasterUserSecret"));
  assert.ok(!appPolicies.includes("secretsmanager:*"));
  assert.ok(appPolicies.includes("ApplicationEnvSecretArn"));
});

test("S3 is private, retained, encrypted and readable only through this distribution policy", () => {
  assert.ok(
    Object.values(props("UiBucket").PublicAccessBlockConfiguration).every(
      (v) => v === true,
    ),
  );
  assert.equal(resources.UiBucket.DeletionPolicy, "Retain");
  assert.ok(props("UiBucket").BucketEncryption);
  const statement = props("UiBucketPolicy").PolicyDocument.Statement.find(
    (s) => s.Effect === "Allow",
  );
  assert.equal(statement.Principal.Service, "cloudfront.amazonaws.com");
  assert.ok(statement.Condition.StringEquals["AWS:SourceArn"]);
  assert.equal(
    props("UiOriginAccessControl").OriginAccessControlConfig.SigningBehavior,
    "always",
  );
});

test("product images use a separate retained private bucket and least-privilege EC2 IAM", () => {
  const bucket = props("ProductImagesBucket");
  assert.ok(Object.values(bucket.PublicAccessBlockConfiguration).every(v => v === true));
  assert.equal(resources.ProductImagesBucket.DeletionPolicy, "Retain");
  assert.equal(resources.ProductImagesBucket.UpdateReplacePolicy, "Retain");
  assert.equal(bucket.VersioningConfiguration.Status, "Enabled");
  assert.ok(bucket.BucketEncryption);
  const statements = props("ProductImagesBucketPolicy").PolicyDocument.Statement;
  assert.equal(statements.length, 1);
  assert.equal(statements[0].Condition.Bool["aws:SecureTransport"], "false");
  assert.ok(!JSON.stringify(statements).includes("SourceVpce"));
  const policy = props("ApplicationRole").Policies.find(p => p.PolicyName === "ProductImagesOnly");
  const rules = policy.PolicyDocument.Statement;
  assert.equal(rules[0].Action, "s3:ListBucket");
  assert.deepEqual(rules[0].Resource, {"Fn::GetAtt":["ProductImagesBucket","Arn"]});
  assert.deepEqual(rules[1].Action, ["s3:GetObject","s3:PutObject","s3:DeleteObject"]);
  assert.deepEqual(rules[1].Resource, {"Fn::Sub":"${ProductImagesBucket.Arn}/catalog/products/*"});
  assert.ok(!JSON.stringify(policy).includes("UiBucket"));
  assert.ok(userData.includes("OBJECT_STORAGE_TYPE=s3"));
  assert.ok(userData.includes("S3_BUCKET='${ProductImagesBucket}'"));
  assert.ok(!userData.includes("OBJECT_STORAGE_TYPE=minio"));
  assert.ok(resources.ApplicationAutoScalingGroup.DependsOn.includes("ProductImagesBucketPolicy"));
  assert.deepEqual(template.Outputs.ProductImagesBucketName.Value, ref("ProductImagesBucket"));
});

test("API is not cached, all methods/auth/query/cookies forwarded, no global SPA error override", () => {
  const distribution = props("UiDistribution").DistributionConfig;
  assert.equal(distribution.CustomErrorResponses, undefined);
  assert.deepEqual(
    distribution.CacheBehaviors.map((b) => b.PathPattern),
    ["/api", "/api/*"],
  );
  for (const behavior of distribution.CacheBehaviors) {
    assert.equal(
      behavior.CachePolicyId,
      "4135ea2d-6df8-44a3-9df3-4b5a84be39ad",
    );
    assert.equal(behavior.AllowedMethods.length, 7);
    assert.equal(behavior.ViewerProtocolPolicy, "https-only");
    assert.equal(behavior.FunctionAssociations[0].EventType, "viewer-request");
  }
  const origin = distribution.Origins.find((o) => o.Id === "shop-api");
  assert.equal(origin.CustomOriginConfig.OriginProtocolPolicy, "https-only");
  const policy = props("ApiOriginRequestPolicy").OriginRequestPolicyConfig;
  assert.deepEqual(policy.HeadersConfig, {
    HeaderBehavior: "allExcept",
    Headers: ["host"],
  });
  assert.equal(policy.CookiesConfig.CookieBehavior, "all");
  assert.equal(policy.QueryStringsConfig.QueryStringBehavior, "all");
  assert.equal(props("UiCachePolicy").CachePolicyConfig.MinTTL, 0);
  assert.equal(
    props("HttpsListener").DefaultActions[0].FixedResponseConfig.StatusCode,
    "403",
  );
});

function edge(uri, ip = "203.0.113.10") {
  const code = props("LabViewerRequestFunction")
    .FunctionCode["Fn::Sub"].replaceAll("${AdminCidr}", "203.0.113.10/32")
    .replaceAll("${LabClientIpv4}", "198.51.100.20");
  const context = {
    event: {
      viewer: { ip },
      request: {
        uri,
        method: "GET",
        headers: {},
        querystring: { page: { value: "2" } },
      },
    },
  };
  return vm.runInNewContext(`${code}\nhandler(event)`, context, {
    timeout: 1000,
  });
}

test("edge rejects outsider, accepts configured admin and lab source using real viewer IP", () => {
  assert.equal(edge("/api/products", "192.0.2.30").statusCode, 403);
  assert.equal(edge("/api/products", "198.51.100.20").uri, "/api/products");
  assert.equal(edge("/api/products").uri, "/api/products");
});

test("SPA fallback covers nested routes but never rewrites API or actual asset filenames", () => {
  for (const uri of ["/", "/products", "/orders/123", "/products/"])
    assert.equal(edge(uri).uri, "/index.html");
  for (const uri of [
    "/api",
    "/api/",
    "/api/products",
    "/assets/app.abc.js",
    "/favicon.ico",
  ])
    assert.equal(edge(uri).uri, uri);
  assert.equal(edge("/products").querystring.page.value, "2");
});

test("bootstrap never logs secret text; lab blocks real payment/refund and supports safe rolling update", () => {
  assert.ok(!userData.includes("set -x"));
  for (const fragment of [
    "PAYMENT_PROVIDER_TYPE=none",
    "PAYMENT_REFUND_ENABLED=false",
    "ORDER_PAYMENT_AUTO_INITIATION_ENABLED=false",
    "--read-only",
    "--cap-drop ALL",
    "--security-opt no-new-privileges:true",
    "--memory 1536m",
    "sslMode",
    "VERIFY_IDENTITY",
    "cloudformation signal-resource",
  ])
    assert.ok(userData.includes(fragment), fragment);
  assert.equal(
    resources.ApplicationAutoScalingGroup.CreationPolicy.ResourceSignal.Count,
    2,
  );
  assert.equal(
    resources.ApplicationAutoScalingGroup.UpdatePolicy.AutoScalingRollingUpdate
      .WaitOnResourceSignals,
    true,
  );
});

test(
  "bootstrap shell syntax",
  {
    skip: !process.env.SHOP_BASH
      ? "Set SHOP_BASH to a Bash executable to enable syntax verification"
      : false,
  },
  () => {
    for (const script of [userData, bastionUserData]) {
      const result = spawnSync(
        process.env.SHOP_BASH,
        ["--noprofile", "--norc", "-n"],
        { input: script, encoding: "utf8" },
      );
      assert.equal(result.status, 0, result.stderr || String(result.error));
    }
  },
);

test(
  "embedded Python environment guard rejects missing users and TLS bypass",
  {
    skip: !process.env.SHOP_PYTHON
      ? "Set SHOP_PYTHON to enable environment guard execution"
      : false,
  },
  () => {
    const python = userData
      .split("python3 - <<'PY'\n")[1]
      .split("\nPY\n")[0]
      .replace(
        "Path('/run/shop/application.env').read_text()",
        "__import__('os').environ['SHOP_TEST_ENV']",
      )
      .replaceAll(
        "${Database.Endpoint.Address}",
        "shop.example.rds.amazonaws.com",
      );
    let envText = fs
      .readFileSync(
        path.join(root, "deploy/aws/application.env.example"),
        "utf8",
      )
      .replaceAll("RDS_ENDPOINT", "shop.example.rds.amazonaws.com")
      .replace(
        "REPLACE_WITH_RANDOM_SHARED_SIGNING_KEY_AT_LEAST_64_CHARACTERS",
        "a".repeat(96),
      );
    function run(text) {
      return spawnSync(process.env.SHOP_PYTHON, ["-c", python], {
        encoding: "utf8",
        env: { ...process.env, SHOP_TEST_ENV: text },
      });
    }
    assert.equal(run(envText).status, 0, run(envText).stderr);
    assert.match(envText, /^PAYMENT_PROVIDER_TYPE=none$/m);
    assert.match(envText, /^PAYMENT_REFUND_ENABLED=false$/m);
    assert.doesNotMatch(envText, /^VNPAY_[A-Z_]*=/m);
    // Keep the bootstrap fail-closed: optional empty entries must be omitted,
    // not accepted by weakening the secret parser.
    assert.notEqual(run(envText + "\nVNPAY_HASH_SECRET=\n").status, 0);
    assert.notEqual(
      run(envText.replace("shop_runtime", "shopadmin")).status,
      0,
    );
    assert.notEqual(
      run(envText.replace("shop_migration", "shop_runtime")).status,
      0,
    );
    assert.notEqual(
      run(envText.replaceAll("sslMode=VERIFY_IDENTITY", "sslMode=DISABLED"))
        .status,
      0,
    );
    assert.notEqual(
      run(
        envText.replaceAll(
          "sslMode=VERIFY_IDENTITY",
          "sslMode=VERIFY_IDENTITY&sslMode=DISABLED",
        ),
      ).status,
      0,
    );
    for (const override of ["AWS_ACCESS_KEY_ID=not-real", "AWS_ENDPOINT_URL=https://evil.example",
      "AWS_EC2_METADATA_SERVICE_ENDPOINT=http://evil.example", "S3_BUCKET=wrong-bucket",
      "OBJECT_STORAGE_TYPE=minio", "APP_CATALOG_IMAGES_STORAGE_TYPE=memory", "MINIO_ENDPOINT=https://old.example"]) {
      assert.notEqual(run(envText + "\n" + override + "\n").status, 0);
    }
    assert.notEqual(
      run(envText + "\nJAVA_TOOL_OPTIONS=-javaagent:bad\n").status,
      0,
    );
    assert.notEqual(run("JWT_SIGNER_KEY=missing-required-config\n").status, 0);
  },
);

// Opt-in read-only schema check: fetch AWS's public resource specification in memory.
test(
  "properties and required fields match the current official AWS resource specification",
  {
    skip:
      process.env.SHOP_FETCH_CFN_SPEC !== "1"
        ? "Set SHOP_FETCH_CFN_SPEC=1 for a read-only AWS schema download"
        : false,
  },
  async () => {
    const response = await fetch(
      "https://d1uauaxba7bl26.cloudfront.net/latest/gzip/CloudFormationResourceSpecification.json",
    );
    assert.equal(response.status, 200);
    const spec = await response.json();
    function validate(value, schema, owner, at) {
      if (
        !value ||
        typeof value !== "object" ||
        Array.isArray(value) ||
        Object.keys(value).some((k) => k === "Ref" || k.startsWith("Fn::"))
      )
        return;
      for (const [key, def] of Object.entries(schema.Properties || {})) {
        if (def.Required)
          assert.ok(Object.hasOwn(value, key), `${at}.${key} is required`);
      }
      for (const [key, item] of Object.entries(value)) {
        const def = schema.Properties?.[key];
        assert.ok(def, `Unknown AWS property ${at}.${key}`);
        const type = def.Type === "List" ? def.ItemType : def.Type;
        if (type && type !== "Map") {
          const nested =
            spec.PropertyTypes[`${owner}.${type}`] || spec.PropertyTypes[type];
          if (nested)
            (Array.isArray(item) ? item : [item]).forEach((child, i) =>
              validate(child, nested, owner, `${at}.${key}[${i}]`),
            );
        }
      }
    }
    for (const [id, resource] of Object.entries(resources)) {
      const schema = spec.ResourceTypes[resource.Type];
      assert.ok(schema, `Unknown resource type ${resource.Type}`);
      validate(resource.Properties, schema, resource.Type, id);
    }
  },
);
