"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { release, imageDigest, probeScript, assessValidationEvents } = require("./asg-release.cjs");
const root = fs.mkdtempSync(path.join(os.tmpdir(), "shop-asg-release-test-"));
const template = "AWSTemplateFormatVersion: '2010-09-09'\nDescription: Fixture\n";
const templatePath = path.join(root, "template.yaml");
fs.writeFileSync(templatePath, template);
test.after(() => fs.rmSync(root, { recursive: true, force: true }));
const oldImage = `t1r4m15u/shop:0.1.0-rc.2@sha256:${"a".repeat(64)}`;
const newImage = `docker.io/t1r4m15u/shop@sha256:${"b".repeat(64)}`;
const options = { mode: "apply", accountId: "233377400170", region: "ap-southeast-1", stackName: "shop-aydibro-prod",
  sourceSha: "d".repeat(40), releaseId: "fixture-1", image: newImage, templatePath };
function fixture(settings = {}) {
  let phase = "before";
  let clock = 0;
  let sentIds = [];
  const calls = [];
  const stackId = "arn:aws:cloudformation:ap-southeast-1:233377400170:stack/shop-aydibro-prod/fixture";
  const changesetId = "arn:aws:cloudformation:ap-southeast-1:233377400170:changeSet/asg-cd-fixture-1/fixture";
  const resources = [
    { LogicalResourceId: "ApplicationLaunchTemplate", ResourceType: "AWS::EC2::LaunchTemplate", PhysicalResourceId: "lt-abc" },
    { LogicalResourceId: "ApplicationAutoScalingGroup", ResourceType: "AWS::AutoScaling::AutoScalingGroup", PhysicalResourceId: "shop-prod-asg" },
    { LogicalResourceId: "CpuTargetTrackingPolicy", ResourceType: "AWS::AutoScaling::ScalingPolicy", PhysicalResourceId: "cpu-policy" },
    { LogicalResourceId: "ApplicationTargetGroup", ResourceType: "AWS::ElasticLoadBalancingV2::TargetGroup", PhysicalResourceId: "arn:target-group" },
  ];
  function stack(after = phase !== "before") {
    return { StackId: stackId, StackStatus: after ? (settings.rolloutStatus || "UPDATE_COMPLETE") : "UPDATE_COMPLETE",
      LastUpdatedTime: after ? "2026-10-26T04:01:00Z" : "2026-10-26T04:00:00Z", Capabilities: ["CAPABILITY_IAM"],
      Parameters: Object.entries({ DeploymentMode: "application", PaymentMode: "vnpay-sandbox", LatestAmiId: "ami-abc",
        ApplicationImage: after && !settings.rolloutStatus ? newImage : oldImage,
        ConfigurationRevision: "unchanged", ApplicationEnvSecretArn: "reference-only-not-read" })
        .map(([ParameterKey, ParameterValue]) => ({ ParameterKey, ParameterValue })) };
  }
  const updated = () => phase === "after" && !settings.rolloutStatus;
  function fleet() {
    const n = updated() ? (settings.afterSize || settings.size || 2) : (settings.size || 2);
    const ids = Array.from({ length: n }, (_, i) => `i-${updated() ? "new" : "old"}${i}`);
    const lt = { LaunchTemplateId: "lt-abc", Version: updated() ? "9" : "8" };
    return { AutoScalingGroupName: "shop-prod-asg", MinSize: settings.off ? 0 : 2, MaxSize: 4,
      DesiredCapacity: settings.off ? 0 : n, LaunchTemplate: lt,
      Instances: ids.map((InstanceId, i) => ({ InstanceId, AvailabilityZone: `ap-southeast-1${i % 2 ? "b" : "a"}`,
        LifecycleState: "InService", HealthStatus: "Healthy", LaunchTemplate: { ...lt } })) };
  }
  function plan() {
    const p = { Status: "CREATE_COMPLETE", ExecutionStatus: "AVAILABLE", StackId: stackId,
      Parameters: stack(false).Parameters.map(v => v.ParameterKey === "ApplicationImage" ? { ...v, ParameterValue: newImage } : v),
      Changes: resources.slice(0, 3).map(r => ({ Type: "Resource", ResourceChange: { ...r, Action: "Modify", Scope: ["Properties"],
        Replacement: r.LogicalResourceId === "ApplicationLaunchTemplate" ? "False" : "Conditional" } })) };
    settings.editPlan?.(p);
    return p;
  }
  const aws = (service, operation, args = []) => {
    calls.push({ service, operation, args });
    if (operation === "get-caller-identity") return { Account: settings.wrongAccount ? "000000000000" : options.accountId };
    if (operation === "describe-stacks") {
      const s = stack();
      if (settings.stackBusy && phase === "before") s.StackStatus = "UPDATE_IN_PROGRESS";
      settings.editStack?.(s, phase, calls);
      return { Stacks: [s] };
    }
    if (operation === "get-template") return { TemplateBody: settings.drift ? template + "# changed\n" : template };
    if (operation === "describe-stack-resources") return { StackResources: resources };
    if (operation === "describe-auto-scaling-groups") {
      const f = fleet(); settings.editFleet?.(f, phase); return { AutoScalingGroups: [f] };
    }
    if (operation === "describe-instance-refreshes") return { InstanceRefreshes: [{ Status: settings.activeRefresh ? "InProgress" : "Successful",
      InstanceRefreshId: updated() && !settings.noNewRefresh ? "refresh-new" : "refresh-old" }] };
    if (operation === "describe-target-health") {
      const list = fleet().Instances.map(i => ({ Target: { Id: i.InstanceId, Port: 8080 }, TargetHealth: { State: "healthy" } }));
      settings.editTargets?.(list, phase); return { TargetHealthDescriptions: list };
    }
    if (operation === "describe-instances") {
      const f = fleet();
      const instances = f.Instances.map(i => ({ InstanceId: i.InstanceId,
        ImageId: settings.wrongAmi ? "ami-def" : "ami-abc", State: { Name: "running" },
        Tags: [{ Key: "aws:ec2launchtemplate:id", Value: i.LaunchTemplate.LaunchTemplateId },
          { Key: "aws:ec2launchtemplate:version", Value: i.LaunchTemplate.Version },
          { Key: "aws:autoscaling:groupName", Value: f.AutoScalingGroupName }] }));
      settings.editInstances?.(instances, phase);
      return { Reservations: [{ Instances: instances }] };
    }
    if (operation === "create-change-set") return { Id: changesetId };
    if (operation === "describe-change-set") return settings.executeNetworkError && phase === "after"
      ? { ...plan(), ExecutionStatus: "EXECUTE_IN_PROGRESS" } : plan();
    if (operation === "describe-events") return { OperationEvents: settings.validationFinding ? [{ EventType: "VALIDATION_ERROR", StackId: stackId }]
      : [{ EventType: "STACK_EVENT", StackId: stackId, OperationStatus: settings.eventsPending ? "IN_PROGRESS" : "SUCCEEDED" }] };
    if (operation === "execute-change-set") { phase = "after"; if (settings.executeNetworkError) throw Error("NETWORK"); return {}; }
    if (operation === "send-command") {
      const start = args.indexOf("--instance-ids") + 1;
      sentIds = args.slice(start, args.indexOf("--max-concurrency"));
      return { Command: { CommandId: "probe-1" } };
    }
    if (operation === "list-command-invocations") {
      let ids = [...sentIds];
      if (settings.partialProbe) ids.pop();
      if (settings.extraProbe) ids.push("i-outside");
      return { CommandInvocations: ids.map(InstanceId => ({ InstanceId, Status: settings.failedProbe ? "Failed" : "Success",
        CommandPlugins: [{ ResponseCode: settings.failedProbe ? 1 : 0,
          Output: settings.badOutput ? "not-json" : JSON.stringify({ imageMatches: !settings.badImage, healthy: true }) }] })) };
    }
    throw Error(`UNEXPECTED_CALL_${service}_${operation}`);
  };
  return { aws, calls, sleep: async ms => { clock += ms; }, now: () => clock, report: () => {},
    options: { ...options, ...(settings.options || {}) } };
}
test("updates only ApplicationImage using the original template, then verifies both replacement nodes", async () => {
  const f = fixture(); const result = await release(f.options, f);
  assert.equal(result.outcome, "DEPLOYED_AND_FLEET_VERIFIED"); assert.equal(result.verifiedInstances, 2);
  const create = f.calls.find(c => c.operation === "create-change-set");
  assert.ok(create.args.includes("--use-previous-template"));
  const params = JSON.parse(create.args[create.args.indexOf("--parameters") + 1]);
  assert.deepEqual(params.filter(p => !p.UsePreviousValue), [{ ParameterKey: "ApplicationImage", ParameterValue: newImage }]);
  assert.equal(f.calls.filter(c => c.operation === "execute-change-set").length, 1);
  const probe = f.calls.find(c => c.operation === "send-command");
  assert.deepEqual(probe.args.slice(probe.args.indexOf("--instance-ids") + 1, probe.args.indexOf("--max-concurrency")), ["i-new0", "i-new1"]);
  assert.ok(!f.calls.some(c => /terminate|start-instance-refresh|put-secret|update-auto-scaling-group/.test(c.operation)));
});
test("all four current ASG nodes are verified after scale-out, without a stored EC2 ID", async () => {
  const f = fixture({ afterSize: 4 }); const result = await release(f.options, f);
  assert.equal(result.verifiedInstances, 4);
  assert.equal(f.calls.find(c => c.operation === "send-command").args.filter(a => /^i-new/.test(a)).length, 4);
});
test("plan mode validates the preview without executing a rollout", async () => {
  const f = fixture({ options: { mode: "plan" } }); assert.equal((await release(f.options, f)).outcome, "PLAN_ONLY_NOT_EXECUTED");
  assert.ok(!f.calls.some(c => ["execute-change-set", "send-command"].includes(c.operation)));
});
test("same immutable digest is a whole-fleet verification, not another refresh", async () => {
  const f = fixture({ options: { image: oldImage.replace('t1r4m15u/shop:0.1.0-rc.2', 'docker.io/t1r4m15u/shop') } });
  assert.equal((await release(f.options, f)).outcome, "ALREADY_DEPLOYED");
  assert.ok(!f.calls.some(c => c.operation === "create-change-set"));
});
test("Windows CRLF source matches the deployed LF YAML while content drift remains rejected", async () => {
  const crlfPath = path.join(root, "crlf.yaml"); fs.writeFileSync(crlfPath, template.replace(/\n/g, "\r\n"));
  const f = fixture({ options: { mode: "plan", templatePath: crlfPath } });
  assert.equal((await release(f.options, f)).outcome, "PLAN_ONLY_NOT_EXECUTED");
  const changed = fixture({ drift: true, options: { templatePath: crlfPath } });
  await assert.rejects(release(changed.options, changed), /TEMPLATE_DRIFT/);
});
for (const [name, settings, message] of [
  ["wrong AWS account", { wrongAccount: true }, /ACCOUNT_MISMATCH/],
  ["stopped lab", { off: true }, /CAPACITY_UNEXPECTED/],
  ["busy stack", { stackBusy: true }, /STACK_NOT_STABLE/],
  ["new stack service role", { editStack: s => { s.RoleARN = "unapproved-role"; } }, /STACK_SERVICE_ROLE/],
  ["active refresh", { activeRefresh: true }, /REFRESH_ALREADY_ACTIVE/],
  ["template drift", { drift: true }, /TEMPLATE_DRIFT/],
  ["AMI drift", { wrongAmi: true }, /INSTANCE_CONFIGURATION/],
  ["missing system LT tags", { editInstances: a => { a[0].Tags = []; } }, /INSTANCE_CONFIGURATION/],
  ["different system ASG tag", { editInstances: a => { a[0].Tags[2].Value = "another-asg"; } }, /INSTANCE_CONFIGURATION/],
  ["missing target", { editTargets: a => a.pop() }, /TARGETS_INCOMPLETE/],
  ["unhealthy target", { editTargets: a => { a[0].TargetHealth.State = "unhealthy"; } }, /TARGETS_INCOMPLETE/],
  ["mixed LT versions", { editFleet: a => { a.Instances[0].LaunchTemplate.Version = "7"; } }, /MIXED_VERSION/],
  ["single AZ", { editFleet: a => a.Instances.forEach(i => { i.AvailabilityZone = "ap-southeast-1a"; }) }, /NOT_TWO_AZ/],
]) test(`rejects ${name} before any AWS write`, async () => {
  const f = fixture(settings); await assert.rejects(release(f.options, f), message);
  assert.ok(!f.calls.some(c => ["create-change-set", "execute-change-set", "send-command"].includes(c.operation)));
});
for (const [name, editPlan] of [
  ["DB modification", p => p.Changes.push({ Type: "Resource", ResourceChange: { LogicalResourceId: "Database", Action: "Modify" } })],
  ["resource replacement", p => { p.Changes[0].ResourceChange.Replacement = "True"; }],
  ["resource addition", p => { p.Changes[0].ResourceChange.Action = "Add"; }],
  ["resource removal", p => { p.Changes[0].ResourceChange.Action = "Remove"; }],
  ["different secret reference", p => { p.Parameters.find(v => v.ParameterKey === "ApplicationEnvSecretArn").ParameterValue = "another-secret"; }],
  ["changed configuration revision", p => { p.Parameters.find(v => v.ParameterKey === "ConfigurationRevision").ParameterValue = "new-revision"; }],
  ["new CloudFormation service role", p => { p.RoleARN = "arn:aws:iam::233377400170:role/unapproved"; }],
  ["Express deployment", p => { p.DeploymentConfig = { Mode: "REVERT_DRIFT" }; }],
  ["disabled rollback", p => { p.DeploymentConfig = { DisableRollback: true }; }],
  ["nested stack update", p => { p.IncludeNestedStacks = true; }],
  ["replacement scope", p => { p.Changes[0].ResourceChange.Scope = ["CreationPolicy"]; }],
]) test(`rejects plan with ${name} without Execute`, async () => {
  const f = fixture({ editPlan }); await assert.rejects(release(f.options, f));
  assert.ok(!f.calls.some(c => c.operation === "execute-change-set"));
});
test("AWS validation warnings/errors require review, not automatic execution", async () => {
  const f = fixture({ validationFinding: true }); await assert.rejects(release(f.options, f), /VALIDATION_FINDINGS/);
  assert.ok(!f.calls.some(c => c.operation === "execute-change-set"));
});
test("waits for a successful change-set event instead of accepting an empty/pending response", async () => {
  const f = fixture({ eventsPending: true }); await assert.rejects(release(f.options, f), /AWS_VALIDATION_EVENTS_TIMEOUT/);
  assert.ok(!f.calls.some(c => c.operation === "execute-change-set"));
});
for (const event of [
  { EventType: "VALIDATION_ERROR", ValidationFailureMode: "WARN", ValidationStatus: "FAILED" },
  { EventType: "PROVISIONING_ERROR" }, { EventType: "HOOK_INVOCATION_ERROR", HookFailureMode: "WARN" },
  { EventType: "STACK_EVENT", OperationStatus: "FAILED" }, { EventType: "PROGRESS_EVENT", DetailedStatus: "VALIDATION_FAILED" },
  { EventType: "UNRECOGNIZED" },
]) test(`rejects validation event ${JSON.stringify(event)}`, () => {
  assert.throws(() => assessValidationEvents({ OperationEvents: [event] }), /VALIDATION_FINDINGS/);
});
test("requires a complete DescribeEvents response and keeps normal progress events", () => {
  assert.throws(() => assessValidationEvents({}), /RESPONSE_INCOMPLETE/);
  assert.throws(() => assessValidationEvents({ OperationEvents: [], NextToken: "not-consumed" }), /RESPONSE_INCOMPLETE/);
  assert.deepEqual(assessValidationEvents({ OperationEvents: [{ EventType: "STACK_EVENT", OperationStatus: "IN_PROGRESS" },
    { EventType: "STACK_EVENT", OperationStatus: "SUCCEEDED" }] }), { complete: true, eventCount: 2 });
});
test("does not accept events from another stack as the current plan validation", () => {
  assert.throws(() => assessValidationEvents({ OperationEvents: [
    { EventType: "STACK_EVENT", StackId: "another-stack", OperationStatus: "SUCCEEDED" },
  ] }, "expected-stack"), /STACK_SCOPE_MISMATCH/);
});
test("rechecks last update time before execution and rejects a stale plan", async () => {
  let reads = 0; const f = fixture({ editStack: s => { if (++reads >= 2) s.LastUpdatedTime = "changed"; } });
  await assert.rejects(release(f.options, f), /STALE_PLAN/);
  assert.ok(!f.calls.some(c => c.operation === "execute-change-set"));
});
test("Execute reply loss is recovered by reading its status, not sending a second Execute", async () => {
  const f = fixture({ executeNetworkError: true }); assert.equal((await release(f.options, f)).outcome, "DEPLOYED_AND_FLEET_VERIFIED");
  assert.equal(f.calls.filter(c => c.operation === "execute-change-set").length, 1);
});
test("CloudFormation rollback is verified and still fails the release", async () => {
  const f = fixture({ rolloutStatus: "UPDATE_ROLLBACK_COMPLETE" });
  await assert.rejects(release(f.options, f), /ROLLOUT_UPDATE_ROLLBACK_COMPLETE/);
  assert.ok(f.calls.some(c => c.operation === "send-command"));
});
test("rollback failure never causes manual termination or forced refresh", async () => {
  const f = fixture({ rolloutStatus: "UPDATE_ROLLBACK_FAILED" }); await assert.rejects(release(f.options, f), /ROLLBACK_FAILED/);
  assert.ok(!f.calls.some(c => /terminate|start-instance-refresh/.test(c.operation)));
});
test("rollout timeout fails without claiming completion", async () => {
  const f = fixture({ rolloutStatus: "UPDATE_IN_PROGRESS" }); await assert.rejects(release(f.options, f), /STACK_ROLLOUT_TIMEOUT/);
});
for (const [key, message] of [["partialProbe", /RUNTIME_PROBE_TIMEOUT/], ["extraProbe", /RUNTIME_PROBE_TIMEOUT/],
  ["failedProbe", /PROBE_NOT_SUCCESSFUL/], ["badOutput", /PROBE_NOT_SUCCESSFUL/], ["badImage", /PROBE_NOT_SUCCESSFUL/]]) {
  test(`does not accept ${key} as fleet success`, async () => {
    const f = fixture({ [key]: true }); await assert.rejects(release(f.options, f), message);
  });
}
test("requires a new successful native refresh, not an older successful history item", async () => {
  const f = fixture({ noNewRefresh: true }); await assert.rejects(release(f.options, f), /NEW_NATIVE_REFRESH/);
});
for (const image of ["t1r4m15u/shop:latest", `someone/shop@sha256:${"a".repeat(64)}`, "x;rm", newImage + "\n"]) {
  test(`rejects unpinned/foreign/malformed image ${JSON.stringify(image.slice(0,28))}`, () => assert.throws(() => imageDigest(image)));
}
test("runtime probe contains no env read or application mutation", () => {
  const script = probeScript(newImage);
  assert.match(script, /docker','inspect/); assert.doesNotMatch(script, /\.Config\.Env|printenv|secretsmanager|docker','run|docker','stop/);
});
