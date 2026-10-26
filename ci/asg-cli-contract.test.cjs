"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");
const path = require("node:path");
const os = require("node:os");
const { validationEventArgs } = require("./asg-release.cjs");

test("installed AWS CLI accepts the real DescribeEvents arguments without an AWS request", () => {
  const env = { ...process.env, AWS_ACCESS_KEY_ID: "test", AWS_SECRET_ACCESS_KEY: "test", AWS_SESSION_TOKEN: "test",
    AWS_REGION: "ap-southeast-1", AWS_DEFAULT_REGION: "ap-southeast-1", AWS_EC2_METADATA_DISABLED: "true", AWS_PAGER: "",
    AWS_CONFIG_FILE: path.join(os.tmpdir(), "shop-cli-contract-no-config"),
    AWS_SHARED_CREDENTIALS_FILE: path.join(os.tmpdir(), "shop-cli-contract-no-credentials") };
  delete env.AWS_PROFILE; delete env.AWS_DEFAULT_PROFILE;
  const arn = "arn:aws:cloudformation:ap-southeast-1:111111111111:changeSet/asg-cd-fixture/id";
  // AWS CLI validates input against its own installed service model; no real credentials/network call.
  const result = execFileSync("aws", ["cloudformation", "describe-events", ...validationEventArgs(arn),
    "--generate-cli-skeleton", "output", "--output", "json"], { env, encoding: "utf8", timeout: 30000,
    stdio: ["ignore", "pipe", "pipe"] });
  assert.ok(Array.isArray(JSON.parse(result).OperationEvents));
});
