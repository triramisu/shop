"use strict";
const yaml = require(process.env.SHOP_YAML_MODULE || "js-yaml");
const tags = { Ref: "Ref", Sub: "Fn::Sub", GetAtt: "Fn::GetAtt", GetAZs: "Fn::GetAZs",
  Select: "Fn::Select", Split: "Fn::Split", Join: "Fn::Join", If: "Fn::If",
  Equals: "Fn::Equals", Not: "Fn::Not", And: "Fn::And", Or: "Fn::Or", Base64: "Fn::Base64" };
const schema = yaml.DEFAULT_SCHEMA.extend(Object.entries(tags).flatMap(([tag, key]) =>
  ["scalar", "sequence"].map(kind => new yaml.Type(`!${tag}`, {
    kind, construct: value => ({ [key]: tag === "GetAtt" && typeof value === "string" ? value.split(".") : value }),
  })),
));
module.exports = { parseCfn: source => yaml.load(source, { schema }) };
