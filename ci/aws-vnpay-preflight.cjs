/* Node >=20. Read-only AWS diagnostic; never print CLI errors or secret values.
 * No SDK/package installation, deployment, SSM command or payment is performed.
 */
"use strict";
const { execFileSync } = require("node:child_process");
const { createHash } = require("node:crypto");

const READS = new Set([
  "sts/get-caller-identity",
  "cloudformation/describe-stacks",
  "cloudformation/get-template",
  "cloudformation/list-stack-resources",
  "autoscaling/describe-auto-scaling-groups",
  "autoscaling/describe-instance-refreshes",
  "ec2/describe-launch-template-versions",
  "elbv2/describe-target-health",
  "secretsmanager/describe-secret",
  "secretsmanager/get-secret-value",
]);
const TERMINAL_REFRESH = new Set(["Successful", "Failed", "Cancelled", "RollbackSuccessful", "RollbackFailed"]);
const check = (code, ok) => ({ code, ok: Boolean(ok) });
const fail = () => { throw new Error("PREFLIGHT_READ_FAILED"); };

function validateOptions(o) {
  if (!o || !/^[\w-]{1,64}$/.test(o.profile || "")
      || !/^[a-z]{2}(?:-[a-z]+)+-\d$/.test(o.region || "")
      || !/^[A-Za-z][A-Za-z0-9-]{0,127}$/.test(o.stack || "")
      || !/^\d{12}$/.test(o.account || "")) fail();
}

function createAwsReader(options, execute = execFileSync) {
  validateOptions(options);
  return (service, operation, args = []) => {
    if (!READS.has(`${service}/${operation}`)) fail();
    try {
      const raw = execute("aws", [service, operation, ...args,
        "--profile", options.profile, "--region", options.region,
        "--output", "json", "--no-cli-pager", "--no-cli-auto-prompt",
        "--cli-connect-timeout", "10", "--cli-read-timeout", "30"], {
        encoding: "utf8", timeout: 60000, maxBuffer: 4 * 1024 * 1024,
        shell: false, stdio: ["ignore", "pipe", "pipe"],
        env: { ...process.env, AWS_PAGER: "", AWS_CLI_AUTO_PROMPT: "off" },
      });
      return JSON.parse(raw);
    } catch { fail(); } // CLI stderr, parser excerpts and args may contain secrets.
  };
}

function parseEnv(text) {
  if (typeof text !== "string" || Buffer.byteLength(text) > 65536
      || /[\0\uFEFF]/.test(text)) throw new Error("ENV_FILE_FORMAT");
  const env = Object.create(null);
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim() || line.trimStart().startsWith("#")) continue;
    const match = /^([A-Z][A-Z0-9_]*)=([^\r\n]*)$/.exec(line);
    if (!match || Object.hasOwn(env, match[1])) throw new Error("ENV_FILE_FORMAT");
    // Docker --env-file preserves quotes, '$' and '#' literally; never eval/unquote.
    env[match[1]] = match[2];
  }
  return env;
}

function validTtl(value) {
  // Deliberately narrower than Spring Duration: the runbook uses 10m.
  const m = /^(\d+)(s|m)$/.exec(value);
  const seconds = m ? Number(m[1]) * (m[2] === "m" ? 60 : 1) : NaN;
  return Number.isSafeInteger(seconds) && seconds >= 60 && seconds <= 600;
}

function assessEnv(text, viewerDomain) {
  let env;
  try { env = parseEnv(text); } catch { return [check("ENV_FILE_FORMAT", false)]; }
  const secretValid = value => /^[!-~]{32,512}$/.test(value || "");
  const old = env.VNPAY_PREVIOUS_HASH_SECRETS ? env.VNPAY_PREVIOUS_HASH_SECRETS.split(",") : [];
  const checks = [
    check("ENV_FILE_FORMAT", true),
    check("MERCHANT_TMN_CODE", /^[A-Za-z0-9]{8}$/.test(env.VNPAY_TMN_CODE || "")),
    check("MERCHANT_HASH_SECRET", secretValid(env.VNPAY_HASH_SECRET)),
    check("MERCHANT_PREVIOUS_KEYS", old.length <= 2 && old.every(secretValid)
      && new Set([env.VNPAY_HASH_SECRET, ...old]).size === old.length + 1),
    check("PUBLIC_BASE_URL", env.VNPAY_PUBLIC_BASE_URL === `https://${viewerDomain}`),
    check("RETURN_HOST_ALLOWLIST", env.VNPAY_ALLOWED_HOSTS === viewerDomain),
    check("CHECKOUT_TTL", validTtl(env.VNPAY_CHECKOUT_TTL ?? "10m")),
    check("BANK_CODE", /^[A-Z0-9]{0,20}$/.test(env.VNPAY_BANK_CODE ?? "")),
    check("STAGED_PROVIDER_DISABLED", (env.PAYMENT_PROVIDER_TYPE ?? "none") === "none"),
    check("PROD_PROFILE", !Object.hasOwn(env, "SPRING_PROFILES_ACTIVE") || env.SPRING_PROFILES_ACTIVE === "prod"),
    check("ADMIN_BOOTSTRAP_DISABLED", (env.ADMIN_BOOTSTRAP_ENABLED ?? "false") === "false"
      && ["ADMIN_PASSWORD", "ADMIN_USERNAME", "ADMIN_EMAIL"].every(k => !Object.hasOwn(env, k))),
  ];
  const flags = {
    PAYMENT_REFUND_ENABLED: "false", VNPAY_QUERY_ENABLED: "false",
    PAYMENT_RECONCILIATION_ENABLED: "false",
    ORDER_PAYMENT_AUTO_INITIATION_ENABLED: "true",
    ORDER_PAYMENT_EVENT_CONSUMPTION_ENABLED: "true", ORDER_PAYMENT_RECOVERY_ENABLED: "true",
  };
  for (const [key, fallback] of Object.entries(flags)) {
    checks.push(check(`${key}_DISABLED`, (env[key] ?? fallback) === "false"));
  }
  // Prevent a Spring command/config override from bypassing the staged switches.
  checks.push(check("NO_ALTERNATE_SPRING_CONFIG", [
    "SPRING_APPLICATION_JSON", "SPRING_CONFIG_LOCATION", "SPRING_CONFIG_ADDITIONAL_LOCATION",
    "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS",
  ].every(k => !Object.hasOwn(env, k))));
  return checks;
}

function currentVersion(metadata) {
  const ids = Object.entries(metadata.VersionIdsToStages || {})
    .filter(([, stages]) => stages.includes("AWSCURRENT")).map(([id]) => id);
  if (metadata.DeletedDate || ids.length !== 1) fail();
  return ids[0];
}

function assessFleet(asg, targets, refreshes) {
  const instances = asg.Instances || [];
  const targetList = targets.TargetHealthDescriptions || [];
  const lt = asg.LaunchTemplate;
  return [
    check("FLEET_BASELINE_2_2_4", asg.MinSize === 2 && asg.DesiredCapacity === 2 && asg.MaxSize === 4),
    check("FLEET_STEADY", instances.length === 2 && new Set(instances.map(i => i.InstanceId)).size === 2
      && instances.every(i => i.LifecycleState === "InService" && i.HealthStatus === "Healthy")),
    check("FLEET_TWO_AZS", new Set(instances.map(i => i.AvailabilityZone)).size === 2),
    check("FLEET_PINNED_LAUNCH_VERSION", lt && /^[1-9]\d*$/.test(lt.Version)
      && instances.every(i => i.LaunchTemplate?.LaunchTemplateId === lt.LaunchTemplateId && i.LaunchTemplate.Version === lt.Version)),
    check("ALB_TARGETS_HEALTHY", targetList.length === 2 && new Set(targetList.map(t => t.Target.Id)).size === 2
      && targetList.every(t => t.Target.Port === 8080
      && t.TargetHealth.State === "healthy" && instances.some(i => i.InstanceId === t.Target.Id))),
    check("NO_REFRESH_IN_PROGRESS", refreshes.every(r => TERMINAL_REFRESH.has(r.Status))),
  ];
}

function runPreflight(options, read) {
  validateOptions(options);
  const checks = [];
  if (read("sts", "get-caller-identity").Account !== options.account) {
    return report([check("EXPECTED_ACCOUNT", false)]);
  }
  checks.push(check("EXPECTED_ACCOUNT", true));
  const stack = read("cloudformation", "describe-stacks", ["--stack-name", options.stack]).Stacks?.[0];
  if (!stack || stack.StackId?.split(":")[3] !== options.region
      || stack.StackId?.split(":")[4] !== options.account) fail();
  const p = Object.fromEntries((stack.Parameters || []).map(x => [x.ParameterKey, x.ParameterValue]));
  checks.push(check("STACK_APPLICATION_STABLE", ["CREATE_COMPLETE", "UPDATE_COMPLETE"].includes(stack.StackStatus) && p.DeploymentMode === "application"));
  // A variable provider override alone does not prove that sandbox is selected.
  if (Object.hasOwn(p, "PaymentMode")) {
    checks.push(check("STACK_PAYMENT_MODE_SANDBOX", p.PaymentMode === "vnpay-sandbox"));
  }
  checks.push(check("IMAGE_PINNED", /^[\w./:-]+@sha256:[a-f0-9]{64}$/.test(p.ApplicationImage || "")));
  if (!/^(?=.{1,200}$)[a-z0-9]+(?:[.-][a-z0-9]+)+$/.test(p.ViewerDomainName || "")) fail();
  // The secret must belong to this deployed stack, region and account; no arbitrary ARN input.
  if (!new RegExp(`^arn:aws:secretsmanager:${options.region}:${options.account}:secret:[A-Za-z0-9/_+=.@-]+$`).test(p.ApplicationEnvSecretArn || "")) fail();
  const resources = read("cloudformation", "list-stack-resources", ["--stack-name", options.stack]);
  const physical = id => {
    const rows = (resources.StackResourceSummaries || []).filter(x => x.LogicalResourceId === id);
    if (rows.length !== 1 || !rows[0].PhysicalResourceId) fail();
    return rows[0].PhysicalResourceId;
  };
  if (resources.NextToken) fail(); // Do not silently assess an incomplete resource page.
  const asgId = physical("ApplicationAutoScalingGroup");
  const asg = read("autoscaling", "describe-auto-scaling-groups", ["--auto-scaling-group-names", asgId]).AutoScalingGroups?.[0];
  if (!asg || !asg.LaunchTemplate) fail();
  const targets = read("elbv2", "describe-target-health", ["--target-group-arn", physical("ApplicationTargetGroup")]);
  const refreshes = read("autoscaling", "describe-instance-refreshes", ["--auto-scaling-group-name", asgId, "--max-records", "10"]).InstanceRefreshes || [];
  checks.push(...assessFleet(asg, targets, refreshes));
  const version = read("ec2", "describe-launch-template-versions", ["--launch-template-id", asg.LaunchTemplate.LaunchTemplateId, "--versions", asg.LaunchTemplate.Version]).LaunchTemplateVersions?.[0];
  const script = Buffer.from(version?.LaunchTemplateData?.UserData || "", "base64").toString("utf8");
  checks.push(check("LAUNCH_PROD_PROFILE", /-e\s+SPRING_PROFILES_ACTIVE=prod(?:\s|\\|$)/.test(script)));
  const paymentForcedOff = /-e\s+PAYMENT_PROVIDER_TYPE=none(?:\s|\\|$)/.test(script);
  checks.push(check("RUNTIME_PAYMENT_NOT_FORCED_OFF", !paymentForcedOff));
  const body = read("cloudformation", "get-template", ["--stack-name", options.stack, "--template-stage", "Original"]).TemplateBody;
  if (!body) fail();
  const source = typeof body === "string" ? body : JSON.stringify(body);
  const provenance = { templateSha256: createHash("sha256").update(source).digest("hex"), templateBytes: Buffer.byteLength(source) };
  const secretArgs = ["--secret-id", p.ApplicationEnvSecretArn];
  const versionId = currentVersion(read("secretsmanager", "describe-secret", secretArgs));
  const secret = read("secretsmanager", "get-secret-value", [...secretArgs, "--version-id", versionId, "--version-stage", "AWSCURRENT"]);
  if (secret.VersionId !== versionId || !(secret.VersionStages || []).includes("AWSCURRENT")) fail();
  checks.push(...assessEnv(secret.SecretString, p.ViewerDomainName));
  // JS strings cannot be securely zeroed. Keep this process short-lived; never serialize it.
  delete secret.SecretString;
  checks.push(check("SECRET_VERSION_STABLE", currentVersion(read("secretsmanager", "describe-secret", secretArgs)) === versionId));
  const end = read("cloudformation", "describe-stacks", ["--stack-name", options.stack]).Stacks?.[0];
  checks.push(check("STACK_SNAPSHOT_STABLE", end && end.StackId === stack.StackId && end.StackStatus === stack.StackStatus
    && JSON.stringify(end.Parameters) === JSON.stringify(stack.Parameters) && end.LastUpdatedTime === stack.LastUpdatedTime));
  return report(checks, provenance);
}

function report(checks, provenance) {
  const blockers = checks.filter(c => !c.ok).map(c => c.code);
  return { task: "PAY-SIT-01A", status: blockers.length ? "BLOCKED" : "PREFLIGHT_ONLY",
    readyForSit: false, checks, blockers, ...(provenance ? { provenance } : {}),
    notValidated: ["RUNNING_CONTAINER_ENV", "MERCHANT_KEY_ROTATION", "MERCHANT_IPN_REGISTRATION", "UI_HANDOFF", "REAL_SANDBOX_TRANSACTION"] };
}

function parseArgs(args) {
  const options = {};
  for (let i = 0; i < args.length; i += 2) {
    const key = args[i]?.replace(/^--/, "");
    if (!args[i]?.startsWith("--") || !["profile", "region", "stack", "account"].includes(key)
        || Object.hasOwn(options, key) || !args[i + 1]) fail();
    options[key] = args[i + 1];
  }
  validateOptions(options);
  return options;
}

function main(args, execute, print = console.log) {
  try {
    const options = parseArgs(args);
    const result = runPreflight(options, createAwsReader(options, execute));
    print(JSON.stringify(result, null, 2));
    return result.blockers.length ? 2 : 0;
  } catch {
    print(JSON.stringify({ task: "PAY-SIT-01A", status: "ERROR", code: "PREFLIGHT_READ_FAILED", readyForSit: false }));
    return 1;
  }
}

module.exports = { assessEnv, assessFleet, createAwsReader, currentVersion, main, parseArgs, parseEnv, runPreflight, validTtl };
if (require.main === module) process.exitCode = main(process.argv.slice(2));
