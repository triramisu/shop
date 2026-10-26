"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { verifiedSource } = require("./cd-source.cjs");
const sha = "c".repeat(40);
function fixture() {
  const source = { eventName: "workflow_run", repository: "triramisu/shop", token: "fixture-not-real", event: {
    workflow_run: { name: "CI", event: "push", conclusion: "success", head_branch: "main", head_sha: sha,
      head_repository: { full_name: "triramisu/shop" } } } };
  const main = { object: { sha } };
  const runs = { workflow_runs: [{ head_sha: sha, head_branch: "main", event: "push", status: "completed",
    conclusion: "success", head_repository: { full_name: "triramisu/shop" } }] };
  const request = async url => ({ ok: true, json: async () => url.includes("git/ref") ? main : runs });
  return { source, main, runs, request };
}
test("accepts only current main with a successful push CI run", async () => {
  const f = fixture(); assert.equal(await verifiedSource(f.source, f.request), sha);
});
for (const [name, change] of [
  ["fork", f => { f.source.event.workflow_run.head_repository.full_name = "outside/shop"; }],
  ["pull request CI", f => { f.source.event.workflow_run.event = "pull_request"; }],
  ["failed CI", f => { f.source.event.workflow_run.conclusion = "failure"; }],
  ["other workflow", f => { f.source.event.workflow_run.name = "Unrelated"; }],
  ["non-main", f => { f.source.event.workflow_run.head_branch = "feature"; }],
  ["stale main SHA", f => { f.main.object.sha = "a".repeat(40); }],
  ["no successful main CI", f => { f.runs.workflow_runs[0].conclusion = "failure"; }],
  ["another repository", f => { f.source.repository = "outside/shop"; }],
]) test(`rejects ${name}`, async () => { const f = fixture(); change(f); await assert.rejects(verifiedSource(f.source, f.request)); });
test("manual main still requires successful main CI", async () => {
  const f = fixture(); f.source = { ...f.source, eventName: "workflow_dispatch", ref: "refs/heads/main", sha };
  assert.equal(await verifiedSource(f.source, f.request), sha);
  f.runs.workflow_runs = []; await assert.rejects(verifiedSource(f.source, f.request), /MAIN_CI_NOT_SUCCESSFUL/);
});
test("freshness recheck rejects a source superseded during image build", async () => {
  const f = fixture(); f.source.freshOnly = true; f.main.object.sha = "a".repeat(40);
  await assert.rejects(verifiedSource(f.source, f.request), /SOURCE_NO_LONGER_MAIN/);
});
