/* Local only: no AWS calls, real env-files, payments, Docker, apt or SSM.
 * Set SHOP_YAML_MODULE, SHOP_BASH and SHOP_PYTHON to existing trusted tools.
 */
"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { createHash } = require("node:crypto");
const { spawnSync } = require("node:child_process");
const { parseCfn } = require("./lib/cfn-yaml.cjs");
const source = fs.readFileSync(path.resolve(__dirname, "../deploy/aws/shop-prod.yaml"), "utf8").replaceAll("\r\n", "\n");
const template = parseCfn(source);
const lt = template.Resources.ApplicationLaunchTemplate;
const script = lt.Properties.LaunchTemplateData.UserData["Fn::Base64"]["Fn::Sub"];
const bash = process.env.SHOP_BASH;
const python = process.env.SHOP_PYTHON;

function replaceOnce(text, from, to) {
  const parts = text.split(from);
  assert.equal(parts.length, 2, "Expected exactly one reviewed fragment");
  return parts[0] + to + parts[1];
}
function restoreOriginal() {
  let text = source;
  text = replaceOnce(text, "DeploymentMode, PaymentMode, ApplicationImage", "DeploymentMode, ApplicationImage");
  const mode = /^  PaymentMode:\n(?:    .*\n)+/m.exec(text)?.[0];
  assert.ok(mode); text = replaceOnce(text, mode, "");
  const rule = text.slice(text.indexOf("  SandboxRequiresApplication:"), text.indexOf("  ViewerCertificatePair:"));
  assert.ok(rule.startsWith("  SandboxRequiresApplication:")); text = replaceOnce(text, rule, "");
  const start = text.indexOf("    Metadata:", text.indexOf("  ApplicationLaunchTemplate:"));
  const end = text.indexOf("    Properties:", start);
  assert.ok(start > 0 && end > start); text = replaceOnce(text, text.slice(start, end), "");
  const pin = text.slice(text.indexOf("  LatestAmiId:"), text.indexOf("  AppInstanceType:"));
  text = replaceOnce(text, pin, [
    "  LatestAmiId:", "    Type: AWS::SSM::Parameter::Value<AWS::EC2::Image::Id>",
    "    Default: /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id",
    "    Description: Canonical Ubuntu Server 24.04 LTS amd64 (not minimal or Ubuntu Pro).", "",
  ].join("\n"));
  text = replaceOnce(text,
    '        # Desired revision only; verify actual LT version and health after rollout.\n        - { Key: DesiredConfigurationRevision, Value: !Ref ConfigurationRevision, PropagateAtLaunch: true }\n', "");
  text = replaceOnce(text,
    "                optional_empty = key in ('VNPAY_BANK_CODE', 'VNPAY_PREVIOUS_HASH_SECRETS')\n                if not sep or key in values or (not value and not optional_empty) or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):",
    "                if not sep or key in values or not value or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):");
  const block = /^            # BEGIN PAYMENT MODE\n[\s\S]*?            # END PAYMENT MODE\n/m.exec(text)?.[0];
  assert.ok(block); text = replaceOnce(text, block, "");
  const flags = text.slice(text.indexOf("              -e SPRING_PROFILES_ACTIVE=prod"), text.indexOf("              -e OBJECT_STORAGE_TYPE=s3", text.indexOf("  ApplicationLaunchTemplate:")));
  text = replaceOnce(text, flags,
    "              -e SPRING_PROFILES_ACTIVE=prod -e PAYMENT_PROVIDER_TYPE=none \\\n              -e PAYMENT_REFUND_ENABLED=false -e ORDER_PAYMENT_AUTO_INITIATION_ENABLED=false \\\n");
  return text;
}

test("reversing only reviewed edits reconstructs exact deployed Original SHA256", () => {
  assert.equal(createHash("sha256").update(restoreOriginal()).digest("hex"),
    "ce6b948680b10693eb807cac5d42ce2c7caeff506ed42733fb06e7a96642a8cb");
});
test("inline size, resource count and attribution stay bounded", () => {
  assert.ok(Buffer.byteLength(source) <= 51200);
  assert.ok(Buffer.byteLength(script) < 16384);
  assert.equal(Object.keys(template.Resources).length, 57);
  assert.equal(template.Metadata.AWSToolsMetrics.AWSAgentToolkit, "aws-cloudformation@3");
});
test("only none or sandbox, default none and application required", () => {
  assert.deepEqual(template.Parameters.PaymentMode.AllowedValues, ["none", "vnpay-sandbox"]);
  assert.equal(template.Parameters.PaymentMode.Default, "none");
  assert.deepEqual(template.Rules.SandboxRequiresApplication.Assertions[0].Assert,
    { "Fn::Equals": [{ Ref: "DeploymentMode" }, "application"] });
});
test("AMI is explicit, both instances reference same unchanged parameter, no latest lookup", () => {
  assert.equal(template.Parameters.LatestAmiId.Type, "AWS::EC2::Image::Id");
  assert.ok(!Object.hasOwn(template.Parameters.LatestAmiId, "Default"));
  assert.deepEqual(lt.Properties.LaunchTemplateData.ImageId, { Ref: "LatestAmiId" });
  assert.deepEqual(template.Resources.BastionInstance.Properties.ImageId, { Ref: "LatestAmiId" });
});
test("guardrails remain: private instance, IMDSv2, sandbox context, ELB readiness", () => {
  const d = lt.Properties.LaunchTemplateData;
  assert.equal(d.NetworkInterfaces[0].AssociatePublicIpAddress, false);
  assert.equal(d.MetadataOptions.HttpTokens, "required");
  assert.equal(d.MetadataOptions.HttpPutResponseHopLimit, 2);
  assert.ok(lt.Metadata["com.aws.cloudformation.Context"].must.length >= 3);
  const asg = template.Resources.ApplicationAutoScalingGroup;
  assert.deepEqual(asg.Properties.LaunchTemplate.Version,
    { "Fn::GetAtt": ["ApplicationLaunchTemplate", "LatestVersionNumber"] });
  assert.deepEqual(asg.Properties.Tags.find(t => t.Key === "DesiredConfigurationRevision"),
    { Key: "DesiredConfigurationRevision", Value: { Ref: "ConfigurationRevision" }, PropagateAtLaunch: true });
  assert.equal(asg.Properties.HealthCheckGracePeriod, 1200);
  assert.equal(asg.UpdatePolicy.AutoScalingInstanceRefresh.Preferences.MinHealthyPercentage, 100);
  assert.equal(asg.UpdatePolicy.AutoScalingInstanceRefresh.Preferences.MaxHealthyPercentage, 150);
});
test("entire bootstrap parses as Bash, no execution of cloud-init", () => {
  assert.ok(bash, "Set SHOP_BASH to an existing trusted Bash executable");
  const result = spawnSync(bash, ["--noprofile", "--norc", "-n"], { input: script, encoding: "utf8", timeout: 10000 });
  assert.equal(result.status, 0, "Bash syntax failed");
});
const modeBlock = script.split("# BEGIN PAYMENT MODE\n")[1].split("# END PAYMENT MODE")[0];
const dockerFlags = script.slice(script.indexOf("  -e SPRING_PROFILES_ACTIVE=prod"), script.indexOf("  -e OBJECT_STORAGE_TYPE=s3"));
for (const [mode, provider, orderEnabled] of [["none", "none", "false"], ["vnpay-sandbox", "vnpay", "true"]]) {
  test(`${mode} yields exact effective Docker flags without changing env-file`, () => {
    assert.ok(bash);
    const body = modeBlock.replaceAll("${PaymentMode}", mode)
      + '\nprintf \'%s\\n\' ' + dockerFlags.trim().replace(/\\\n\s*/g, " ") + "\n";
    const result = spawnSync(bash, ["--noprofile", "--norc", "-s"], { input: body, encoding: "utf8", timeout: 10000 });
    assert.equal(result.status, 0);
    const tokens = result.stdout.trim().split("\n");
    const effective = tokens.filter((_, i) => tokens[i - 1] === "-e");
    assert.deepEqual(effective, ["SPRING_PROFILES_ACTIVE=prod", `PAYMENT_PROVIDER_TYPE=${provider}`,
      "PAYMENT_REFUND_ENABLED=false", "VNPAY_QUERY_ENABLED=false", "PAYMENT_RECONCILIATION_ENABLED=false",
      "ORDER_PAYMENT_RECOVERY_ENABLED=false", `ORDER_PAYMENT_AUTO_INITIATION_ENABLED=${orderEnabled}`,
      `ORDER_PAYMENT_EVENT_CONSUMPTION_ENABLED=${orderEnabled}`]);
  });
}
for (const mode of ["fake", "stripe", "vnpay", "live"]) {
  test(`unsupported ${mode} fails before docker pull/run`, () => {
    assert.ok(bash);
    const result = spawnSync(bash, ["--noprofile", "--norc", "-s"], { input: modeBlock.replaceAll("${PaymentMode}", mode), encoding: "utf8", timeout: 10000 });
    assert.equal(result.status, 1);
    assert.ok(result.stderr.includes("Unsupported payment mode"));
  });
}

// Run only the real embedded Python validator against synthetic stdin, no files.
const validator = "import sys\n" + script.split("python3 - <<'PY'\n")[1].split("\nPY\n")[0]
  .replace("Path('/run/shop/application.env').read_text()", "sys.stdin.read()")
  .replaceAll("${Database.Endpoint.Address}", "db.internal");
const jdbc = "jdbc:mysql://db.internal:3306/shop?sslMode=VERIFY_IDENTITY&trustCertificateKeyStoreUrl=file:/run/rds-truststore.p12&trustCertificateKeyStorePassword=changeit&trustCertificateKeyStoreType=PKCS12";
const fixture = {
  SPRING_DATASOURCE_URL: jdbc, SPRING_FLYWAY_URL: jdbc,
  SPRING_DATASOURCE_USERNAME: "runtime_fixture", SPRING_FLYWAY_USERNAME: "migration_fixture",
  SPRING_DATASOURCE_PASSWORD: "SYNTHETIC_PASSWORD", SPRING_FLYWAY_PASSWORD: "SYNTHETIC_OTHER_PASSWORD",
  JWT_SIGNER_KEY: "S".repeat(64), CORS_ALLOWED_ORIGINS: "https://shop.example.com",
};
function validate(env, append = "") {
  assert.ok(python, "Set SHOP_PYTHON to an existing trusted Python executable");
  return spawnSync(python, ["-c", validator], { input: Object.entries(env).map(([k, v]) => `${k}=${v}`).join("\n") + append,
    encoding: "utf8", timeout: 10000 });
}
test("validator accepts absent or explicitly empty optional VNPay fields", () => {
  for (const extra of [{}, { VNPAY_BANK_CODE: "" }, { VNPAY_PREVIOUS_HASH_SECRETS: "" }, { VNPAY_BANK_CODE: "NCB" }]) {
    const result = validate({ ...fixture, ...extra });
    assert.equal(result.status, 0, "Valid synthetic configuration was rejected");
    assert.equal(result.stdout, "");
  }
});
for (const [key, value] of [
  ["SPRING_DATASOURCE_PASSWORD", ""], ["OTHER_FIELD", ""], ["SPRING_DATASOURCE_USERNAME", "shopadmin"],
  ["SPRING_FLYWAY_USERNAME", "runtime_fixture"], ["JWT_SIGNER_KEY", "short"],
  ["SPRING_DATASOURCE_URL", "jdbc:mysql://evil.internal:3306/shop?sslMode=VERIFY_IDENTITY"],
  ["SPRING_FLYWAY_URL", jdbc.replace("VERIFY_IDENTITY", "DISABLED")],
  ["JAVA_OPTS", "-Dapp.payment.provider.type=fake"], ["AWS_ACCESS_KEY_ID", "PRIVATE_CANARY"],
]) {
  test(`validator still rejects invalid ${key} without echoing values`, () => {
    const result = validate({ ...fixture, [key]: value });
    assert.equal(result.status, 1);
    assert.ok(!result.stderr.includes("PRIVATE_CANARY"));
    assert.equal(result.stdout, "");
  });
}
test("duplicate entries and JSON are still rejected", () => {
  assert.equal(validate(fixture, "\nJWT_SIGNER_KEY=PRIVATE_CANARY").status, 1);
  const result = spawnSync(python, ["-c", validator], { input: '{"PRIVATE_CANARY":"value"}', encoding: "utf8", timeout: 10000 });
  assert.equal(result.status, 1);
  assert.ok(!result.stderr.includes("PRIVATE_CANARY"));
});
