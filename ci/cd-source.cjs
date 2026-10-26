"use strict";

const fs = require("node:fs");
async function verifiedSource({ event, eventName, ref, repository, token, sha, freshOnly = false }, request = fetch) {
  if (repository !== "triramisu/shop") throw new Error("WRONG_SOURCE_REPOSITORY");
  let sourceSha;
  if (eventName === "workflow_run") {
    const run = event.workflow_run;
    if (run?.name !== "CI" || run.event !== "push" || run.conclusion !== "success"
      || run.head_branch !== "main" || run.head_repository?.full_name !== repository) throw new Error("UNTRUSTED_CI_TRIGGER");
    sourceSha = run.head_sha;
  } else if (eventName === "workflow_dispatch" && ref === "refs/heads/main") sourceSha = sha;
  else throw new Error("SOURCE_MUST_BE_MAIN_PUSH_OR_MANUAL_MAIN");
  if (!/^[a-f0-9]{40}$/.test(sourceSha || "") || !token) throw new Error("INVALID_SOURCE_CONTEXT");
  const get = async path => {
    const response = await request(`https://api.github.com/repos/${repository}/${path}`, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28" },
      signal: AbortSignal.timeout(15000), redirect: "error",
    });
    if (!response.ok) throw new Error("SOURCE_API_CHECK_FAILED");
    return response.json();
  };
  if ((await get("git/ref/heads/main")).object?.sha !== sourceSha) throw new Error("SOURCE_NO_LONGER_MAIN");
  if (!freshOnly) {
    const runs = await get(`actions/workflows/ci.yml/runs?branch=main&event=push&head_sha=${sourceSha}&per_page=30`);
    if (!(runs.workflow_runs || []).some(r => r.head_sha === sourceSha && r.head_branch === "main"
      && r.event === "push" && r.status === "completed" && r.conclusion === "success"
      && r.head_repository?.full_name === repository)) throw new Error("MAIN_CI_NOT_SUCCESSFUL");
  }
  return sourceSha;
}
if (require.main === module) {
  verifiedSource({ event: JSON.parse(fs.readFileSync(process.env.GITHUB_EVENT_PATH, "utf8")),
    eventName: process.env.GITHUB_EVENT_NAME, ref: process.env.GITHUB_REF, repository: process.env.GITHUB_REPOSITORY,
    token: process.env.GITHUB_TOKEN, sha: process.env.GITHUB_SHA, freshOnly: process.argv.includes("--fresh-only") })
    .then(sha => { if (process.env.GITHUB_OUTPUT) fs.appendFileSync(process.env.GITHUB_OUTPUT, `source_sha=${sha}\n`); console.log(`Verified main source ${sha}`); })
    .catch(error => { console.error(error.message); process.exitCode = 1; });
}
module.exports = { verifiedSource };
