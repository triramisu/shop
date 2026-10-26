"use strict";

const { execFileSync } = require("node:child_process");
const { createHash } = require("node:crypto");
const fs = require("node:fs");

const RESOURCES = {
  ApplicationLaunchTemplate: "AWS::EC2::LaunchTemplate",
  ApplicationAutoScalingGroup: "AWS::AutoScaling::AutoScalingGroup",
  CpuTargetTrackingPolicy: "AWS::AutoScaling::ScalingPolicy",
};
const TERMINAL_REFRESH = new Set(["Successful", "Failed", "Cancelled", "RollbackSuccessful", "RollbackFailed"]);
const stableStatuses = new Set(["CREATE_COMPLETE", "UPDATE_COMPLETE", "UPDATE_ROLLBACK_COMPLETE"]);
// YAML normalizes line breaks; allow Windows CRLF without ignoring any other source change.
const hash = value => createHash("sha256").update(value.toString().replace(/\r\n/g, "\n")).digest("hex");
function requireThat(value, code) { if (!value) throw new Error(code); }
function imageDigest(image) {
  const match = /^(?:docker\.io\/)?t1r4m15u\/shop(?::[A-Za-z0-9_.-]+)?@(sha256:[a-f0-9]{64})$/.exec(image || "");
  requireThat(match, "IMAGE_MUST_BE_PINNED_SHOP_DIGEST");
  return match[1];
}
function parameters(stack) {
  return Object.fromEntries((stack.Parameters || []).map(p => [p.ParameterKey, p.ParameterValue]).sort(([a], [b]) => a.localeCompare(b)));
}
function validateOptions(o) {
  requireThat(o.accountId === "233377400170" && o.region === "ap-southeast-1"
    && o.stackName === "shop-aydibro-prod", "WRONG_DEPLOYMENT_BOUNDARY");
  requireThat(["plan", "apply", "verify"].includes(o.mode), "INVALID_MODE");
  requireThat(/^[a-f0-9]{40}$/.test(o.sourceSha || ""), "INVALID_SOURCE_SHA");
  requireThat(/^[a-z0-9-]{1,60}$/.test(o.releaseId || ""), "INVALID_RELEASE_ID");
  imageDigest(o.image);
}
function assertFleet(asg, targets, expectedTemplateId, expectedAmi, instances) {
  requireThat(asg && asg.MinSize >= 2 && asg.DesiredCapacity >= 2
    && asg.DesiredCapacity <= asg.MaxSize && asg.MaxSize <= 4, "FLEET_OFF_OR_CAPACITY_UNEXPECTED");
  requireThat(asg.LaunchTemplate?.LaunchTemplateId === expectedTemplateId
    && /^[1-9][0-9]*$/.test(asg.LaunchTemplate.Version), "UNEXPECTED_LAUNCH_TEMPLATE");
  const members = asg.Instances || [];
  requireThat(members.length === asg.DesiredCapacity && new Set(members.map(i => i.InstanceId)).size === members.length,
    "FLEET_NOT_STABLE");
  requireThat(members.every(i => i.LifecycleState === "InService" && i.HealthStatus === "Healthy"
    && i.LaunchTemplate?.LaunchTemplateId === expectedTemplateId
    && i.LaunchTemplate.Version === asg.LaunchTemplate.Version), "FLEET_UNHEALTHY_OR_MIXED_VERSION");
  requireThat(new Set(members.map(i => i.AvailabilityZone)).size >= 2, "FLEET_NOT_TWO_AZ");
  const ids = members.map(i => i.InstanceId).sort();
  requireThat(targets.length === ids.length && targets.every(t => ids.includes(t.Target?.Id)
    && t.Target.Port === 8080 && t.TargetHealth?.State === "healthy")
    && new Set(targets.map(t => t.Target.Id)).size === ids.length, "TARGETS_INCOMPLETE_OR_UNHEALTHY");
  requireThat(instances.length === ids.length && instances.every(i => ids.includes(i.InstanceId)
    && i.State?.Name === "running" && i.ImageId === expectedAmi
    && i.Tags?.some(t => t.Key === "aws:ec2launchtemplate:id" && t.Value === expectedTemplateId)
    && i.Tags?.some(t => t.Key === "aws:ec2launchtemplate:version" && t.Value === asg.LaunchTemplate.Version)
    && i.Tags?.some(t => t.Key === "aws:autoscaling:groupName" && t.Value === asg.AutoScalingGroupName)), "INSTANCE_CONFIGURATION_MISMATCH");
  return ids;
}
function inspectPlan(plan, baseline, image) {
  requireThat(plan.Status === "CREATE_COMPLETE" && plan.ExecutionStatus === "AVAILABLE"
    && plan.StackId === baseline.stack.StackId && !plan.IncludeNestedStacks, "CHANGE_SET_NOT_READY");
  requireThat((plan.RoleARN || "") === (baseline.stack.RoleARN || "")
    && (!plan.DeploymentConfig?.Mode || plan.DeploymentConfig.Mode === "STANDARD")
    && plan.DeploymentConfig?.DisableRollback !== true, "CHANGE_SET_EXECUTION_MODE_CHANGED");
  const expected = { ...baseline.params, ApplicationImage: image };
  const proposed = parameters(plan);
  requireThat(JSON.stringify(Object.keys(proposed).sort()) === JSON.stringify(Object.keys(expected).sort())
    && Object.entries(expected).every(([k, v]) => proposed[k] === v), "PARAMETERS_NOT_IMAGE_ONLY");
  const changes = plan.Changes || [];
  requireThat(changes.length >= 1 && changes.some(c => c.ResourceChange?.LogicalResourceId === "ApplicationLaunchTemplate"),
    "MISSING_IMAGE_TEMPLATE_CHANGE");
  requireThat(changes.every(c => {
    const r = c.ResourceChange;
    return c.Type === "Resource" && r && r.Action === "Modify" && ["False", "Conditional"].includes(r.Replacement)
      && RESOURCES[r.LogicalResourceId] === r.ResourceType
      && r.PhysicalResourceId === baseline.resources[r.LogicalResourceId]?.PhysicalResourceId
      && r.Scope?.length > 0 && r.Scope.every(s => ["Properties", "Tags"].includes(s));
  }), "CHANGE_SET_OUTSIDE_APP_BOUNDARY");
  return changes.map(c => ({ id: c.ResourceChange.LogicalResourceId, replacement: c.ResourceChange.Replacement }));
}
function awsCli(region, profile) {
  return (service, operation, args = []) => {
    try {
      const command = [service, operation, ...args, "--region", region, "--output", "json", "--no-cli-pager"];
      if (profile) command.push("--profile", profile);
      const raw = execFileSync("aws", command, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], timeout: 60000,
        maxBuffer: 8 * 1024 * 1024, env: { ...process.env, AWS_PAGER: "", AWS_RETRY_MODE: "standard", AWS_MAX_ATTEMPTS: "3" } });
      return raw.trim() ? JSON.parse(raw) : {};
    } catch (error) {
      const code = /An error occurred \(([A-Za-z0-9.]+)\)/.exec(String(error.stderr || ""))?.[1];
      throw new Error(`AWS_${service}_${operation}_FAILED${code ? `_${code}` : ""}`);
    }
  };
}
function snapshot(aws, o) {
  const identity = aws("sts", "get-caller-identity");
  requireThat(identity.Account === o.accountId, "AWS_ACCOUNT_MISMATCH");
  const stack = aws("cloudformation", "describe-stacks", ["--stack-name", o.stackName]).Stacks?.[0];
  requireThat(stack && stableStatuses.has(stack.StackStatus) && !stack.ParentId, "STACK_NOT_STABLE_ROOT");
  requireThat(!stack.RoleARN, "STACK_SERVICE_ROLE_CHANGED_REVIEW_PERMISSIONS");
  const params = parameters(stack);
  requireThat(params.DeploymentMode === "application" && ["none", "vnpay-sandbox"].includes(params.PaymentMode), "STACK_MODE_UNEXPECTED");
  imageDigest(params.ApplicationImage);
  requireThat(/^ami-[a-f0-9]+$/.test(params.LatestAmiId), "AMI_NOT_PINNED");
  const template = aws("cloudformation", "get-template", ["--stack-name", o.stackName, "--template-stage", "Original"]).TemplateBody;
  requireThat(typeof template === "string" && hash(template) === hash(fs.readFileSync(o.templatePath)), "TEMPLATE_DRIFT");
  const rows = aws("cloudformation", "describe-stack-resources", ["--stack-name", o.stackName]).StackResources || [];
  const resources = Object.fromEntries(rows.map(r => [r.LogicalResourceId, r]));
  for (const [id, type] of Object.entries(RESOURCES)) requireThat(resources[id]?.ResourceType === type, "RESOURCE_MAP_INVALID");
  requireThat(resources.ApplicationTargetGroup?.ResourceType === "AWS::ElasticLoadBalancingV2::TargetGroup", "TARGET_GROUP_MISSING");
  const asgName = resources.ApplicationAutoScalingGroup.PhysicalResourceId;
  const ltId = resources.ApplicationLaunchTemplate.PhysicalResourceId;
  const asg = aws("autoscaling", "describe-auto-scaling-groups", ["--auto-scaling-group-names", asgName]).AutoScalingGroups?.[0];
  const refresh = (aws("autoscaling", "describe-instance-refreshes", ["--auto-scaling-group-name", asgName, "--max-records", "10"]).InstanceRefreshes || [])
    .sort((a, b) => Date.parse(b.StartTime || 0) - Date.parse(a.StartTime || 0));
  requireThat(refresh.every(r => TERMINAL_REFRESH.has(r.Status)), "REFRESH_ALREADY_ACTIVE");
  const targets = aws("elbv2", "describe-target-health", ["--target-group-arn", resources.ApplicationTargetGroup.PhysicalResourceId]).TargetHealthDescriptions || [];
  const members = asg?.Instances || [];
  requireThat(members.length > 0, "FLEET_OFF_OR_EMPTY");
  const instances = (aws("ec2", "describe-instances", ["--instance-ids", ...members.map(i => i.InstanceId)]).Reservations || [])
    .flatMap(r => r.Instances || []);
  const ids = assertFleet(asg, targets, ltId, params.LatestAmiId, instances);
  return { stack, params, resources, asgName, ltId, asg, ids, templateHash: hash(template), refresh };
}
function sameBaseline(a, b) {
  return a.stack.StackId === b.stack.StackId && a.stack.LastUpdatedTime === b.stack.LastUpdatedTime
    && a.templateHash === b.templateHash && JSON.stringify(a.params) === JSON.stringify(b.params)
    && a.asgName === b.asgName && a.ltId === b.ltId && a.asg.LaunchTemplate.Version === b.asg.LaunchTemplate.Version
    && ["MinSize", "DesiredCapacity", "MaxSize"].every(k => a.asg[k] === b.asg[k])
    && JSON.stringify(a.ids) === JSON.stringify(b.ids);
}
function validationEventArgs(changeSetId) { return ["--change-set-name", changeSetId]; }
function assessValidationEvents(response) {
  requireThat(Array.isArray(response.OperationEvents) && !response.NextToken, "AWS_VALIDATION_RESPONSE_INCOMPLETE");
  requireThat(response.OperationEvents.every(e => ["STACK_EVENT", "PROGRESS_EVENT"].includes(e.EventType)
    && e.OperationStatus !== "FAILED" && e.DetailedStatus !== "VALIDATION_FAILED"
    && !/_FAILED$/.test(e.ResourceStatus || "") && !["HOOK_COMPLETE_FAILED", "HOOK_FAILED"].includes(e.HookStatus)),
  "AWS_VALIDATION_FINDINGS_REVIEW_REQUIRED");
  return { complete: response.OperationEvents.some(e => e.EventType === "STACK_EVENT" && e.OperationStatus === "SUCCEEDED"),
    eventCount: response.OperationEvents.length };
}
async function waitFor(read, complete, { sleep, now, timeoutMs, label }) {
  const deadline = now() + timeoutMs;
  while (now() < deadline) {
    const value = read();
    if (complete(value)) return value;
    await sleep(5000);
  }
  throw new Error(`${label}_TIMEOUT_STATE_MUST_BE_INSPECTED`);
}
function probeScript(image) {
  return "python3 - <<'SHOP_CD_PROBE'\nimport json,subprocess\ntry:\n"
    + " r=subprocess.run(['docker','inspect','--format','{{json .Config.Image}}|{{json .State.Running}}|{{json .State.Health.Status}}|{{json .RestartCount}}|{{json .State.OOMKilled}}','shop'],capture_output=True,text=True,timeout=10,check=True)\n"
    + " image,running,health,restarts,oom=[json.loads(x) for x in r.stdout.strip().split('|')]\n"
    + ` ok=image.split('@')[-1]==${JSON.stringify(imageDigest(image))} and running is True and health=='healthy' and restarts==0 and oom is False\n`
    + " print(json.dumps({'imageMatches':ok,'healthy':ok}))\n if not ok: raise RuntimeError()\n"
    + "except Exception:\n print('RUNTIME_PROBE_FAILED')\n raise SystemExit(1)\nSHOP_CD_PROBE";
}
async function verifyRuntime(aws, state, o, timing) {
  const command = aws("ssm", "send-command", ["--document-name", "AWS-RunShellScript",
    "--instance-ids", ...state.ids, "--max-concurrency", "1", "--max-errors", "0",
    "--parameters", JSON.stringify({ commands: [probeScript(o.image)], executionTimeout: ["30"] }),
    "--timeout-seconds", "120", "--comment", `ASG CD read-only image verification ${o.releaseId}`]).Command;
  requireThat(command?.CommandId, "SSM_COMMAND_NOT_CREATED");
  const invocations = await waitFor(() => aws("ssm", "list-command-invocations", ["--command-id", command.CommandId, "--details"]).CommandInvocations || [],
    list => list.length === state.ids.length && list.every(i => !["Pending", "InProgress", "Delayed", "Cancelling"].includes(i.Status)),
    { ...timing, timeoutMs: 240000, label: "RUNTIME_PROBE" });
  requireThat(JSON.stringify(invocations.map(i => i.InstanceId).sort()) === JSON.stringify(state.ids), "SSM_TARGET_COVERAGE_MISMATCH");
  requireThat(invocations.every(i => i.Status === "Success" && i.CommandPlugins?.length === 1
    && i.CommandPlugins[0].ResponseCode === 0 && (() => {
      try { const p = JSON.parse(i.CommandPlugins[0].Output); return p.imageMatches === true && p.healthy === true; }
      catch { return false; }
    })()), "RUNTIME_PROBE_NOT_SUCCESSFUL");
  const finalState = snapshot(aws, o);
  requireThat(sameBaseline(state, finalState), "FLEET_CHANGED_DURING_PROBE_RETRY_VERIFICATION");
  return { verifiedInstances: state.ids.length, launchTemplateVersion: state.asg.LaunchTemplate.Version };
}
async function release(o, { aws, sleep = ms => new Promise(r => setTimeout(r, ms)), now = Date.now, report = console.log } = {}) {
  validateOptions(o);
  aws ||= awsCli(o.region, o.profile);
  const timing = { sleep, now };
  const baseline = snapshot(aws, o);
  const previousImage = baseline.params.ApplicationImage;
  const result = { sourceSha: o.sourceSha, mode: o.mode, templateHash: baseline.templateHash, previousImage, image: o.image };
  if (imageDigest(previousImage) === imageDigest(o.image)) {
    const proof = await verifyRuntime(aws, baseline, o, timing);
    return { ...result, outcome: "ALREADY_DEPLOYED", ...proof };
  }
  requireThat(o.mode !== "verify", "REQUESTED_IMAGE_NOT_DEPLOYED");
  const name = `asg-cd-${o.releaseId}`;
  const create = aws("cloudformation", "create-change-set", ["--stack-name", o.stackName, "--change-set-name", name,
    "--change-set-type", "UPDATE", "--use-previous-template", "--client-token", o.releaseId,
    "--description", `Image-only backend release ${o.sourceSha}`,
    "--parameters", JSON.stringify(Object.keys(baseline.params).map(ParameterKey => ParameterKey === "ApplicationImage"
      ? { ParameterKey, ParameterValue: o.image } : { ParameterKey, UsePreviousValue: true })),
    ...(baseline.stack.Capabilities?.length ? ["--capabilities", ...baseline.stack.Capabilities] : [])]);
  requireThat(create.Id?.startsWith(`arn:aws:cloudformation:${o.region}:${o.accountId}:changeSet/${name}/`), "UNEXPECTED_CHANGE_SET_ARN");
  const plan = await waitFor(() => aws("cloudformation", "describe-change-set", ["--stack-name", o.stackName, "--change-set-name", create.Id]),
    p => ["CREATE_COMPLETE", "FAILED"].includes(p.Status), { ...timing, timeoutMs: 360000, label: "CHANGE_SET" });
  result.changeSetId = create.Id;
  report(JSON.stringify({ phase: "PLAN_CREATED", ...result }));
  result.changes = inspectPlan(plan, baseline, o.image);
  result.validation = await waitFor(() => assessValidationEvents(aws("cloudformation", "describe-events", validationEventArgs(create.Id))),
    value => value.complete, { ...timing, timeoutMs: 90000, label: "AWS_VALIDATION_EVENTS" });
  report(JSON.stringify({ phase: "PLAN_READY", ...result }));
  if (o.mode === "plan") return { ...result, outcome: "PLAN_ONLY_NOT_EXECUTED" };
  requireThat(sameBaseline(baseline, snapshot(aws, o)), "STALE_PLAN_OR_FLEET_CHANGED");
  inspectPlan(aws("cloudformation", "describe-change-set", ["--stack-name", o.stackName, "--change-set-name", create.Id]), baseline, o.image);
  // Only CloudFormation executes the native refresh. Never start a second refresh or terminate nodes here.
  try {
    aws("cloudformation", "execute-change-set", ["--stack-name", o.stackName, "--change-set-name", create.Id,
      "--client-request-token", `execute-${o.releaseId}`]);
  } catch {
    const execution = aws("cloudformation", "describe-change-set", ["--stack-name", o.stackName, "--change-set-name", create.Id]);
    requireThat(["EXECUTE_IN_PROGRESS", "EXECUTE_COMPLETE"].includes(execution.ExecutionStatus), "EXECUTION_NOT_CONFIRMED_INSPECT_CHANGE_SET");
  }
  const complete = await waitFor(() => {
    const s = aws("cloudformation", "describe-stacks", ["--stack-name", o.stackName]).Stacks?.[0];
    requireThat(s?.StackId === baseline.stack.StackId, "STACK_ID_CHANGED");
    report(JSON.stringify({ phase: "WAIT_STACK", status: s.StackStatus }));
    return s;
  }, s => s.StackStatus === "UPDATE_COMPLETE" && parameters(s).ApplicationImage === o.image
    || ["UPDATE_ROLLBACK_COMPLETE", "UPDATE_ROLLBACK_FAILED", "UPDATE_FAILED"].includes(s.StackStatus),
  { ...timing, sleep: async () => sleep(15000), timeoutMs: 3600000, label: "STACK_ROLLOUT" });
  if (complete.StackStatus !== "UPDATE_COMPLETE") {
    if (complete.StackStatus === "UPDATE_ROLLBACK_COMPLETE") {
      const rolledBack = snapshot(aws, { ...o, image: previousImage });
      requireThat(imageDigest(rolledBack.params.ApplicationImage) === imageDigest(previousImage), "ROLLBACK_IMAGE_MISMATCH");
      await verifyRuntime(aws, rolledBack, { ...o, image: previousImage }, timing);
    }
    throw new Error(`ROLLOUT_${complete.StackStatus}_REQUIRES_REVIEW`);
  }
  const finalState = snapshot(aws, o);
  requireThat(finalState.templateHash === baseline.templateHash
    && Object.entries(baseline.params).every(([k, v]) => k === "ApplicationImage" || finalState.params[k] === v)
    && finalState.ltId === baseline.ltId && finalState.asgName === baseline.asgName, "POSTFLIGHT_BOUNDARY_CHANGED");
  requireThat(finalState.asg.LaunchTemplate.Version !== baseline.asg.LaunchTemplate.Version
    && finalState.refresh[0]?.Status === "Successful"
    && finalState.refresh[0].InstanceRefreshId !== baseline.refresh[0]?.InstanceRefreshId, "NEW_NATIVE_REFRESH_NOT_SUCCESSFUL");
  const proof = await verifyRuntime(aws, finalState, o, timing);
  return { ...result, outcome: "DEPLOYED_AND_FLEET_VERIFIED", ...proof };
}
if (require.main === module) {
  const options = { accountId: process.env.AWS_ACCOUNT_ID, region: process.env.AWS_REGION,
    stackName: process.env.CFN_STACK_NAME, image: process.env.DEPLOY_IMAGE, mode: process.env.DEPLOY_MODE || "plan",
    sourceSha: process.env.SOURCE_SHA, releaseId: process.env.RELEASE_ID, profile: process.env.AWS_PROFILE,
    templatePath: process.env.CFN_TEMPLATE_PATH || "deploy/aws/shop-prod.yaml" };
  const output = process.env.RELEASE_REPORT_PATH || "asg-release-result.json";
  let lastPlan = {};
  release(options, { report: event => {
    const value = JSON.parse(event);
    if (["PLAN_CREATED", "PLAN_READY"].includes(value.phase)) lastPlan = value;
    console.log(event);
  } }).then(result => {
    fs.writeFileSync(output, JSON.stringify(result, null, 2) + "\n");
    console.log(JSON.stringify(result));
  }).catch(error => {
    fs.writeFileSync(output, JSON.stringify({ ...lastPlan, outcome: "FAILED_REVIEW_REQUIRED", error: error.message }, null, 2) + "\n");
    console.error(error.message); process.exitCode = 1;
  });
}
module.exports = { imageDigest, validateOptions, assertFleet, inspectPlan, snapshot, sameBaseline, validationEventArgs,
  assessValidationEvents, probeScript, verifyRuntime, release };
