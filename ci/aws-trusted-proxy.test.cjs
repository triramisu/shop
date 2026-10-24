/* Synthetic local configuration tests only; no AWS calls, traffic or credentials. */
"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { spawnSync } = require("node:child_process");
const { parseCfn } = require("./lib/cfn-yaml.cjs");
const root = path.resolve(__dirname, "..");
const source = fs.readFileSync(path.join(root, "deploy/aws/shop-prod.yaml"), "utf8").replaceAll("\r\n", "\n");
const template = parseCfn(source);
const resources = template.Resources;
const script = resources.ApplicationLaunchTemplate.Properties.LaunchTemplateData.UserData["Fn::Base64"]["Fn::Sub"];
const code = resources.ViewerRequestFunction.Properties.FunctionCode["Fn::Sub"];
const env = Object.fromEntries(fs.readFileSync(path.join(root, "src/test/resources/contracts/trusted-proxy.env"), "utf8")
  .trimEnd().split(/\r?\n/).map(line => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]));
function invoke(uri, ip = "203.0.113.42", access = "public", extra = {}) {
  const rendered = code.replaceAll("${AdminCidr}", "203.0.113.42/32")
    .replaceAll("${AdditionalClientIpv4}", "198.51.100.2").replaceAll("${ViewerAccessMode}", access);
  const context = vm.createContext({});
  vm.runInContext(rendered, context, { timeout: 1000 });
  const event = { viewer: { ip }, request: { uri, method: "GET", headers: {
    authorization: { value: "Bearer SYNTHETIC_TOKEN" },
    "x-shop-client-ip": { value: "198.51.100.99", multiValue: [{ value: "198.51.100.88" }] },
    forwarded: { value: "for=198.51.100.99;host=evil.example" },
    "x-forwarded-host": { value: "evil.example" }, "x-forwarded-prefix": { value: "/evil" },
    "x-forwarded-for": { value: "198.51.100.99" }, ...extra,
  }, querystring: { vnp_Test: { value: "SYNTHETIC_VALUE" } }, cookies: { fixture: { value: "fixture-cookie" } } } };
  // Serialize only synthetic results to remove the VM object's foreign prototypes.
  return JSON.parse(JSON.stringify(context.handler(event)));
}
for (const uri of ["/api", "/api/auth/token", "/api/payments/webhooks/vnpay", "/api/catalog/products"]) {
  test(`${uri} preserves API routing and replaces forged or duplicate IP headers`, () => {
    const result = invoke(uri);
    assert.equal(result.uri, uri);
    assert.deepEqual(result.headers["x-shop-client-ip"], { value: "203.0.113.42" });
    assert.equal(result.headers.authorization.value, "Bearer SYNTHETIC_TOKEN");
    assert.deepEqual(result.querystring, { vnp_Test: { value: "SYNTHETIC_VALUE" } });
    assert.deepEqual(result.cookies, { fixture: { value: "fixture-cookie" } });
    for (const key of ["forwarded", "x-forwarded-host", "x-forwarded-prefix"]) assert.ok(!Object.hasOwn(result.headers, key));
  });
}
test("literal IPv6 viewer address is not split or taken from XFF", () => {
  assert.equal(invoke("/api/auth/token", "2001:db8::42").headers["x-shop-client-ip"].value, "2001:db8::42");
});
for (const [uri, expected] of [["/", "/index.html"], ["/login", "/index.html"], ["/products/demo", "/index.html"],
  ["/apiary", "/index.html"], ["/assets/main.js", "/assets/main.js"]]) {
  test(`${uri} retains SPA/static behavior without sending canonical IP to S3`, () => {
    const result = invoke(uri);
    assert.equal(result.uri, expected);
    assert.ok(!Object.hasOwn(result.headers, "x-shop-client-ip"));
  });
}
test("restricted viewer remains fail-closed; both allowed addresses still work", () => {
  assert.equal(invoke("/api/auth/token", "192.0.2.1", "restricted").statusCode, 403);
  for (const ip of ["203.0.113.42", "198.51.100.2"]) {
    assert.equal(invoke("/api/auth/token", ip, "restricted").headers["x-shop-client-ip"].value, ip);
  }
});
test("native LT waits on canonical edge publisher, not a new resource", () => {
  assert.equal(resources.ApplicationLaunchTemplate.DependsOn, "ViewerRequestFunction");
  assert.equal(resources.ViewerRequestFunction.Properties.AutoPublish, true);
  assert.equal(Object.keys(resources).length, 57);
});
test("header is forwarded to API and not cached; all behavior associations preserved", () => {
  const policy = resources.ApiOriginRequestPolicy.Properties.OriginRequestPolicyConfig;
  assert.deepEqual(policy.HeadersConfig, { HeaderBehavior: "allExcept", Headers: ["host"] });
  assert.equal(policy.QueryStringsConfig.QueryStringBehavior, "all");
  assert.equal(policy.CookiesConfig.CookieBehavior, "all");
  for (const b of resources.UiDistribution.Properties.DistributionConfig.CacheBehaviors) {
    assert.equal(b.CachePolicyId, "4135ea2d-6df8-44a3-9df3-4b5a84be39ad");
    assert.equal(b.ViewerProtocolPolicy, "https-only");
    assert.deepEqual(b.FunctionAssociations[0].FunctionARN, { "Fn::GetAtt": ["ViewerRequestFunction", "FunctionARN"] });
  }
});
test("trusted peer pattern is coupled only to current two ALB subnet ranges", () => {
  assert.equal(resources.PublicSubnetA.Properties.CidrBlock, "10.0.1.0/24");
  assert.equal(resources.PublicSubnetB.Properties.CidrBlock, "10.0.3.0/24");
  const pattern = new RegExp(env.SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES);
  for (const peer of ["10.0.1.12", "10.0.3.45"]) assert.ok(pattern.test(peer));
  for (const peer of ["10.0.2.12", "10.0.4.12", "172.17.0.1", "127.0.0.1", "203.0.113.1", "::1"]) assert.ok(!pattern.test(peer));
  assert.equal(env.SERVER_TOMCAT_REMOTEIP_HOSTHEADER, "");
  assert.equal(env.SERVER_TOMCAT_REMOTEIP_PORTHEADER, "");
});
test("entire real Docker command yields exact native env contract, not a broken continuation", () => {
  assert.ok(process.env.SHOP_BASH, "Set SHOP_BASH to a trusted existing Bash executable");
  const begin = script.indexOf("docker run -d --name shop");
  const end = script.indexOf("\nrm -f /run/shop/application.env", begin);
  assert.ok(begin >= 0 && end > begin);
  const docker = script.slice(begin, end).replace(/\$\{[^}]+\}/g, "SYNTHETIC_RESOURCE");
  const body = 'docker() { printf \'%s\\n\' "$@"; }\npayment_provider=vnpay; order_payment_enabled=true; instance_id=fixture\n' + docker;
  const r = spawnSync(process.env.SHOP_BASH, ["--noprofile", "--norc", "-s"], { input: body, encoding: "utf8", timeout: 10000 });
  assert.equal(r.status, 0, "Docker shell continuation/quoting failed");
  const tokens = r.stdout.trimEnd().split("\n");
  assert.equal(tokens[0], "run");
  const flags = Object.fromEntries(tokens.filter((_, i) => tokens[i - 1] === "-e")
    .map(value => [value.slice(0, value.indexOf("=")), value.slice(value.indexOf("=") + 1)]));
  for (const [key, value] of Object.entries(env)) assert.equal(flags[key], value, key);
  assert.equal(flags.PAYMENT_PROVIDER_TYPE, "vnpay");
  assert.equal(tokens.at(-1), "SYNTHETIC_RESOURCE");
});
