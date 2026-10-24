"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { assessEnv, assessFleet, createAwsReader, currentVersion, main, parseArgs, parseEnv, runPreflight, validTtl } = require("./aws-vnpay-preflight.cjs");

// Synthetic fixtures only. Never load AWS credentials or real merchant data in tests.
const options = { profile: "test", region: "ap-southeast-1", stack: "shop-test", account: "111111111111" };
const args = Object.entries(options).flatMap(([k, v]) => [`--${k}`, v]);
const env = {
  VNPAY_TMN_CODE: "TEST0001", VNPAY_HASH_SECRET: "SYNTHETIC_NOT_A_MERCHANT_KEY_000000",
  VNPAY_PUBLIC_BASE_URL: "https://shop.example.com", VNPAY_ALLOWED_HOSTS: "shop.example.com",
  ORDER_PAYMENT_AUTO_INITIATION_ENABLED: "false", ORDER_PAYMENT_EVENT_CONSUMPTION_ENABLED: "false",
  ORDER_PAYMENT_RECOVERY_ENABLED: "false",
};
const envFile = data => Object.entries(data).map(([k, v]) => `${k}=${v}`).join("\n");
const failedEnv = data => assessEnv(envFile(data), "shop.example.com").filter(c => !c.ok).map(c => c.code);
const lt = { LaunchTemplateId: "lt-test", Version: "5" };
function fleet() {
  return { MinSize: 2, DesiredCapacity: 2, MaxSize: 4, LaunchTemplate: lt,
    Instances: ["a", "b"].map(x => ({ InstanceId: `i-${x}`, AvailabilityZone: `ap-southeast-1${x}`,
      HealthStatus: "Healthy", LifecycleState: "InService", LaunchTemplate: lt })) };
}
const targets = () => ({ TargetHealthDescriptions: ["a", "b"].map(x => ({ Target: { Id: `i-${x}`, Port: 8080 }, TargetHealth: { State: "healthy" } })) });

function fixtures({ provider = "none" } = {}) {
  const secretArn = "arn:aws:secretsmanager:ap-southeast-1:111111111111:secret:shop/test/application-Abc123";
  const stack = { StackId: "arn:aws:cloudformation:ap-southeast-1:111111111111:stack/shop-test/test",
    StackStatus: "UPDATE_COMPLETE", LastUpdatedTime: "2026-10-24T01:00:00Z",
    Parameters: Object.entries({ DeploymentMode: "application", ViewerDomainName: "shop.example.com",
      ApplicationImage: `example/shop@sha256:${"a".repeat(64)}`, ApplicationEnvSecretArn: secretArn })
      .map(([ParameterKey, ParameterValue]) => ({ ParameterKey, ParameterValue })) };
  return {
    "sts/get-caller-identity": { Account: options.account },
    "cloudformation/describe-stacks": { Stacks: [stack] },
    "cloudformation/list-stack-resources": { StackResourceSummaries: [
      { LogicalResourceId: "ApplicationAutoScalingGroup", PhysicalResourceId: "asg-test" },
      { LogicalResourceId: "ApplicationTargetGroup", PhysicalResourceId: "tg-test" },
    ] },
    "autoscaling/describe-auto-scaling-groups": { AutoScalingGroups: [fleet()] },
    "autoscaling/describe-instance-refreshes": { InstanceRefreshes: [{ Status: "Successful" }] },
    "elbv2/describe-target-health": targets(),
    "ec2/describe-launch-template-versions": { LaunchTemplateVersions: [{ LaunchTemplateData: {
      UserData: Buffer.from(`docker run -e SPRING_PROFILES_ACTIVE=prod -e PAYMENT_PROVIDER_TYPE=${provider} image`).toString("base64"),
    } }] },
    "cloudformation/get-template": { TemplateBody: "AWSTemplateFormatVersion: '2010-09-09'" },
    "secretsmanager/describe-secret": { VersionIdsToStages: { fixtureVersion: ["AWSCURRENT"] } },
    "secretsmanager/get-secret-value": { VersionId: "fixtureVersion", VersionStages: ["AWSCURRENT"], SecretString: envFile(env) },
  };
}
function reader(data, calls = []) {
  return (service, operation, params) => {
    const key = `${service}/${operation}`;
    calls.push({ key, params });
    assert.ok(Object.hasOwn(data, key), `Unexpected operation: ${key}`);
    return structuredClone(data[key]);
  };
}

test("env-file preserves Docker literals and CRLF without evaluating shell text", () => {
  const parsed = parseEnv("# comment\r\nKEY=a=b#$HOME\r\nOTHER='quoted'\r\nEMPTY=\r\n");
  assert.equal(parsed.KEY, "a=b#$HOME");
  assert.equal(parsed.OTHER, "'quoted'");
  assert.equal(parsed.EMPTY, "");
});
for (const malformed of ["{\"KEY\":\"PRIVATE_CANARY\"}", "KEY=one\nKEY=PRIVATE_CANARY", "export KEY=x", "KEY", " KEY=x", "KEY=x\0", "\uFEFFKEY=x", "KEY=x\rKEY=y", "A=" + "x".repeat(65536)]) {
  test("malformed env-file gives fixed error without line/value", () => {
    assert.throws(() => parseEnv(malformed), { message: "ENV_FILE_FORMAT" });
    assert.deepEqual(assessEnv(malformed, "shop.example.com"), [{ code: "ENV_FILE_FORMAT", ok: false }]);
  });
}
test("complete dormant merchant config passes field checks", () => assert.deepEqual(failedEnv(env), []));
test("missing Order flags are not assumed false (Spring defaults true)", () => {
  const { ORDER_PAYMENT_AUTO_INITIATION_ENABLED, ORDER_PAYMENT_EVENT_CONSUMPTION_ENABLED, ORDER_PAYMENT_RECOVERY_ENABLED, ...rest } = env;
  assert.deepEqual(failedEnv(rest), ["ORDER_PAYMENT_AUTO_INITIATION_ENABLED_DISABLED", "ORDER_PAYMENT_EVENT_CONSUMPTION_ENABLED_DISABLED", "ORDER_PAYMENT_RECOVERY_ENABLED_DISABLED"]);
});
for (const [key, value, code] of [
  ["VNPAY_TMN_CODE", "BAD", "MERCHANT_TMN_CODE"],
  ["VNPAY_HASH_SECRET", "short", "MERCHANT_HASH_SECRET"],
  ["VNPAY_HASH_SECRET", " with spaces ".repeat(3), "MERCHANT_HASH_SECRET"],
  ["VNPAY_PUBLIC_BASE_URL", "http://shop.example.com", "PUBLIC_BASE_URL"],
  ["VNPAY_PUBLIC_BASE_URL", "https://shop.example.com/", "PUBLIC_BASE_URL"],
  ["VNPAY_PUBLIC_BASE_URL", "https://shop.example.com?secret=PRIVATE_CANARY", "PUBLIC_BASE_URL"],
  ["VNPAY_PUBLIC_BASE_URL", "https://user:PRIVATE_CANARY@shop.example.com", "PUBLIC_BASE_URL"],
  ["VNPAY_ALLOWED_HOSTS", "shop.example.com,evil.test", "RETURN_HOST_ALLOWLIST"],
  ["VNPAY_CHECKOUT_TTL", "11m", "CHECKOUT_TTL"],
  ["VNPAY_BANK_CODE", "ncb", "BANK_CODE"],
  ["PAYMENT_PROVIDER_TYPE", "vnpay", "STAGED_PROVIDER_DISABLED"],
  ["SPRING_PROFILES_ACTIVE", "dev", "PROD_PROFILE"],
  ["ADMIN_BOOTSTRAP_ENABLED", "true", "ADMIN_BOOTSTRAP_DISABLED"],
  ["ADMIN_PASSWORD", "PRIVATE_CANARY", "ADMIN_BOOTSTRAP_DISABLED"],
  ["SPRING_APPLICATION_JSON", '{"secret":"PRIVATE_CANARY"}', "NO_ALTERNATE_SPRING_CONFIG"],
  ["JAVA_TOOL_OPTIONS", "-Dapp.payment.provider.type=vnpay", "NO_ALTERNATE_SPRING_CONFIG"],
]) {
  test(`${key} rejects unsupported/unsafe value without echo`, () => {
    const result = assessEnv(envFile({ ...env, [key]: value }), "shop.example.com");
    assert.ok(result.some(c => c.code === code && !c.ok));
    assert.ok(!JSON.stringify(result).includes("PRIVATE_CANARY"));
  });
}
for (const key of ["PAYMENT_REFUND_ENABLED", "VNPAY_QUERY_ENABLED", "PAYMENT_RECONCILIATION_ENABLED", "ORDER_PAYMENT_AUTO_INITIATION_ENABLED", "ORDER_PAYMENT_EVENT_CONSUMPTION_ENABLED", "ORDER_PAYMENT_RECOVERY_ENABLED"]) {
  test(`${key} must remain off in staging secret`, () => assert.ok(failedEnv({ ...env, [key]: "true" }).includes(`${key}_DISABLED`)));
}
test("previous keys must be bounded, valid and distinct", () => {
  assert.deepEqual(failedEnv({ ...env, VNPAY_PREVIOUS_HASH_SECRETS: "P".repeat(32) }), []);
  for (const keys of ["bad", ["P".repeat(32), "Q".repeat(32), "R".repeat(32)].join(","), env.VNPAY_HASH_SECRET]) {
    assert.ok(failedEnv({ ...env, VNPAY_PREVIOUS_HASH_SECRETS: keys }).includes("MERCHANT_PREVIOUS_KEYS"));
  }
});
test("TTL boundaries use a conservative documented syntax", () => {
  for (const value of ["60s", "600s", "1m", "10m"]) assert.equal(validTtl(value), true);
  for (const value of ["59s", "601s", "0m", "-1m", "1.5m", "PT10M", "Infinitym"]) assert.equal(validTtl(value), false);
});
test("fleet requires exactly the reviewed baseline and healthy matching targets", () => {
  assert.ok(assessFleet(fleet(), targets(), []).every(c => c.ok));
  const bad = fleet(); bad.DesiredCapacity = 0; bad.Instances[0].LifecycleState = "Pending"; bad.Instances[0].LaunchTemplate = { ...lt, Version: "4" };
  const result = assessFleet(bad, { TargetHealthDescriptions: [] }, [{ Status: "InProgress" }]);
  assert.ok(result.filter(c => !c.ok).length >= 5);
});
test("duplicate or foreign target never counts as two healthy app instances", () => {
  for (const id of ["i-a", "i-foreign"]) {
    const list = targets(); list.TargetHealthDescriptions[1].Target.Id = id;
    assert.ok(assessFleet(fleet(), list, []).some(c => c.code === "ALB_TARGETS_HEALTHY" && !c.ok));
  }
});
test("absent/ambiguous/deleted current secret fails closed", () => {
  for (const metadata of [{}, { VersionIdsToStages: { a: ["AWSCURRENT"], b: ["AWSCURRENT"] } }, { DeletedDate: "date", VersionIdsToStages: { a: ["AWSCURRENT"] } }]) {
    assert.throws(() => currentVersion(metadata), { message: "PREFLIGHT_READ_FAILED" });
  }
});
test("wrong account stops before stack or secret reads", () => {
  const data = fixtures(); data["sts/get-caller-identity"].Account = "222222222222";
  const calls = [];
  const result = runPreflight(options, reader(data, calls));
  assert.deepEqual(result.blockers, ["EXPECTED_ACCOUNT"]);
  assert.equal(calls.length, 1);
});
test("wrong stack region, incomplete resources and missing launch metadata fail closed", () => {
  for (const mutate of [
    data => { data["cloudformation/describe-stacks"].Stacks[0].StackId = "arn:aws:cloudformation:us-east-1:111111111111:stack/shop-test/test"; },
    data => { data["cloudformation/list-stack-resources"].NextToken = "next"; },
    data => { data["cloudformation/list-stack-resources"].StackResourceSummaries.pop(); },
    data => { delete data["autoscaling/describe-auto-scaling-groups"].AutoScalingGroups[0].LaunchTemplate; },
  ]) {
    const data = fixtures(); mutate(data);
    assert.throws(() => runPreflight(options, reader(data)), { message: "PREFLIGHT_READ_FAILED" });
  }
});
test("updating stack and mutable image are blockers, not readiness", () => {
  const data = fixtures(), stack = data["cloudformation/describe-stacks"].Stacks[0];
  stack.StackStatus = "UPDATE_IN_PROGRESS";
  stack.Parameters.find(p => p.ParameterKey === "ApplicationImage").ParameterValue = "example/shop:latest";
  const result = runPreflight(options, reader(data));
  assert.ok(result.blockers.includes("STACK_APPLICATION_STABLE"));
  assert.ok(result.blockers.includes("IMAGE_PINNED"));
  assert.equal(result.readyForSit, false);
});
test("forced-none deployed bootstrap is a blocker, never claims SIT ready", () => {
  const calls = [];
  const result = runPreflight(options, reader(fixtures(), calls));
  assert.deepEqual(result.blockers, ["RUNTIME_PAYMENT_NOT_FORCED_OFF"]);
  assert.equal(result.readyForSit, false);
  assert.match(result.provenance.templateSha256, /^[a-f0-9]{64}$/);
  const secretRead = calls.find(c => c.key === "secretsmanager/get-secret-value");
  assert.ok(secretRead.params.includes("AWSCURRENT") && secretRead.params.includes("fixtureVersion"));
  assert.ok(!JSON.stringify(result).includes(env.VNPAY_HASH_SECRET));
});
test("even passing metadata is not a real sandbox transaction or runtime proof", () => {
  const result = runPreflight(options, reader(fixtures({ provider: "vnpay" })));
  assert.equal(result.status, "PREFLIGHT_ONLY");
  assert.equal(result.readyForSit, false);
  assert.ok(result.notValidated.includes("RUNNING_CONTAINER_ENV"));
});
test("foreign secret ARN is rejected before any secret read", () => {
  const data = fixtures(), calls = [];
  data["cloudformation/describe-stacks"].Stacks[0].Parameters.find(p => p.ParameterKey === "ApplicationEnvSecretArn").ParameterValue = "arn:aws:secretsmanager:ap-southeast-1:222222222222:secret:other";
  assert.throws(() => runPreflight(options, reader(data, calls)));
  assert.ok(calls.every(c => !c.key.startsWith("secretsmanager/")));
});
test("stale version and binary secret cannot pass field assessment", () => {
  const data = fixtures(); data["secretsmanager/get-secret-value"].VersionId = "different";
  assert.throws(() => runPreflight(options, reader(data)));
  data["secretsmanager/get-secret-value"] = { VersionId: "fixtureVersion", VersionStages: ["AWSCURRENT"], SecretBinary: "PRIVATE_CANARY" };
  assert.ok(runPreflight(options, reader(data)).blockers.includes("ENV_FILE_FORMAT"));
});
test("secret rotation during preflight is detected", () => {
  const read = reader(fixtures()); let n = 0;
  const result = runPreflight(options, (s, o, a) => s === "secretsmanager" && o === "describe-secret" && ++n === 2
    ? { VersionIdsToStages: { changed: ["AWSCURRENT"] } } : read(s, o, a));
  assert.ok(result.blockers.includes("SECRET_VERSION_STABLE"));
});
test("stack parameter update during preflight is detected", () => {
  const read = reader(fixtures()); let n = 0;
  const result = runPreflight(options, (s, o, a) => {
    const value = read(s, o, a);
    if (s === "cloudformation" && o === "describe-stacks" && ++n === 2) value.Stacks[0].Parameters.push({ ParameterKey: "ConfigurationRevision", ParameterValue: "changed" });
    return value;
  });
  assert.ok(result.blockers.includes("STACK_SNAPSHOT_STABLE"));
});
test("reader refuses all writes, shell disabled and output/errors stay captured", () => {
  const read = createAwsReader(options, (file, a, opts) => {
    assert.equal(file, "aws"); assert.equal(opts.shell, false); assert.equal(opts.timeout, 60000);
    assert.deepEqual(opts.stdio, ["ignore", "pipe", "pipe"]);
    assert.ok(a.includes("--no-cli-auto-prompt"));
    return '{}';
  });
  assert.deepEqual(read("sts", "get-caller-identity"), {});
  for (const [s, o] of [["cloudformation", "execute-change-set"], ["ssm", "send-command"], ["secretsmanager", "put-secret-value"], ["s3", "sync"]]) assert.throws(() => read(s, o));
});
test("CLI failure and malformed AWS JSON never leak raw payloads/stack traces", () => {
  for (const execute of [() => { const e = new Error("PRIVATE_CANARY"); e.stderr = "PRIVATE_CANARY"; throw e; }, () => '{"PRIVATE_CANARY"']) {
    const out = []; assert.equal(main(args, execute, x => out.push(x)), 1);
    assert.ok(!out.join("").includes("PRIVATE_CANARY"));
    assert.equal(JSON.parse(out[0]).code, "PREFLIGHT_READ_FAILED");
  }
});
test("CLI successful reads serialize only the finite report, never secret/template data", () => {
  const data = fixtures(), output = [];
  data["secretsmanager/get-secret-value"].SecretString += "\nJWT_SIGNER_KEY=PRIVATE_CANARY\nDATABASE_PASSWORD=PRIVATE_CANARY";
  const execute = (file, a) => JSON.stringify(data[`${a[0]}/${a[1]}`]);
  assert.equal(main(args, execute, line => output.push(line)), 2);
  const result = JSON.parse(output[0]);
  assert.equal(result.status, "BLOCKED");
  assert.ok(!output[0].includes("PRIVATE_CANARY"));
  assert.ok(!output[0].includes(env.VNPAY_HASH_SECRET));
  assert.ok(!output[0].includes("SecretString"));
});
test("CLI validates explicit options and does not accept unknown/duplicate/input files", () => {
  assert.deepEqual(parseArgs(args), options);
  for (const invalid of [[], [...args, "--execute", "true"], [...args, "--account", options.account], [...args.slice(0, -1), "PRIVATE_CANARY"], ["--profile", "$(bad)"]]) assert.throws(() => parseArgs(invalid));
});
