import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const canonicalRoot = path.dirname(fileURLToPath(import.meta.url));
const mirrorRoot = path.join(canonicalRoot, "..", "idk", "openapi");
const repositoryRoot = path.join(canonicalRoot, "..", "..", "openapi");
// The frontend checkout owns the canonical OpenAPI inputs but does not contain the backend's
// nested IDK/EDK mirror directories. Validate every mirror available in the current checkout,
// while keeping the canonical contract mandatory. Backend checkouts with all mirrors still get
// the complete parity check; frontend-only checkouts no longer fail before validating anything.
const openApiRoots = [
  [canonicalRoot, "canonical frontend"],
  ...(fs.existsSync(mirrorRoot) ? [[mirrorRoot, "IDK"]] : []),
  ...(fs.existsSync(repositoryRoot) ? [[repositoryRoot, "VDX repository"]] : []),
];

const credentialFormats = [
  "dc+sd-jwt",
  "vc+sd-jwt",
  "mso_mdoc",
  "jwt_vc_json",
  "jwt_vc_json-ld",
  "ldp_vc",
];

function read(root, name) {
  return fs.readFileSync(path.join(root, name), "utf8");
}

function credentialFormatEnum(document, source) {
  const schema = document.match(/    CredentialFormat:\r?\n([\s\S]*?)(?=\r?\n    [A-Za-z])/);
  assert(schema, `${source}: CredentialFormat schema is missing`);
  const values = schema[1].match(/enum:\s*\[([^\]]+)\]/)?.[1]
    ?.match(/['"]([^'"]+)['"]/g)
    ?.map((value) => value.slice(1, -1));
  assert.deepEqual(values, credentialFormats, `${source}: credential enum changed`);
  assert(!values.includes("jwt_vp_json"), `${source}: VP format leaked into credential enum`);
  assert(!values.includes("ldp_vp"), `${source}: VP format leaked into credential enum`);
}

const canonicalCredential = read(canonicalRoot, "wallet-credential-components.yml");
credentialFormatEnum(canonicalCredential, "canonical wallet-credential-components.yml");
for (const [root, label] of openApiRoots.slice(1)) {
  const credential = read(root, "wallet-credential-components.yml");
  credentialFormatEnum(credential, `${label} wallet-credential-components.yml`);
  assert.equal(canonicalCredential, credential, `canonical and ${label} credential schemas diverged`);
}

for (const [root, label] of openApiRoots) {
  const dcql = read(root, "dcql-components.yml");
  assert(
    dcql.includes('DcqlJwtVcJsonLdCredentialQuery:') &&
      dcql.includes('enum: [jwt_vc_json-ld]'),
    `${label} DCQL must model jwt_vc_json-ld as a typed VCDM credential query`,
  );
  assert(
    dcql.includes('enum: [dc+sd-jwt, mso_mdoc, jwt_vc_json, jwt_vc_json-ld, ldp_vc]'),
    `${label} DCQL extension exclusion list must reserve jwt_vc_json-ld`,
  );
  assert(!dcql.includes('enum: [jwt_vp_json]'), `${label} DCQL must not admit jwt_vp_json`);
  assert(!/pattern:\s*['"]\^\(\?!jwt_vp_json\$\)\./.test(dcql), `${label} DCQL extension must exclude reserved VP formats`);

  const forms = read(root, "forms-components.yml");
  assert(
    (forms.match(/\$ref: '\.\/wallet-credential-components\.yml#\/components\/schemas\/CredentialFormat'/g) ?? []).length >= 4,
    `${label} forms credentialFormat fields must use shared CredentialFormat`,
  );
  const credentialRef = forms.match(/    FieldValueCredentialRef:[\s\S]*?(?=\r?\n    [A-Za-z])/);
  assert(
    credentialRef?.[0].includes("$ref: './wallet-credential-components.yml#/components/schemas/CredentialFormat'"),
    `${label} FieldValueCredentialRef.format must use shared CredentialFormat`,
  );

  const issuer = read(root, "oid4vci-issuer-openapi.yaml");
  const summary = issuer.match(/    Oid4vciTestingCredentialConfigurationSummary:[\s\S]*?(?=\r?\n    [A-Za-z])/);
  assert(
    summary?.[0].includes("format: { $ref: './wallet-credential-components.yml#/components/schemas/CredentialFormat' }"),
    `${label} issuer testing summary.format must use shared CredentialFormat`,
  );

  const provenance = dcql.match(/    DcqlCredentialProvenance:[\s\S]*?(?=\r?\n    [A-Za-z])/);
  assert(
    provenance?.[0].includes("$ref: './wallet-credential-components.yml#/components/schemas/CredentialFormat'"),
    `${label} DCQL provenance.format must use shared CredentialFormat`,
  );

  const semantic = read(root, "semantic-model-authoring-openapi.yml");
  assert(
    semantic.includes("credentialFormat:\n          $ref: './wallet-credential-components.yml#/components/schemas/CredentialFormat'"),
    `${label} semantic VC channel credentialFormat must use shared CredentialFormat`,
  );
}

for (const [root, label] of openApiRoots) {
  const interaction = read(root, "wallet-interaction-components.yml");
  const credentialRef = "./wallet-credential-components.yml#/components/schemas/CredentialFormat";
  assert.equal(
    interaction.split(credentialRef).length - 1,
    2,
    `${label} wallet interaction format fields must use CredentialFormat`,
  );
  assert(!interaction.includes("PresentationFormat"), `${label} wallet interaction must not use PresentationFormat`);
}

for (const [root, label] of openApiRoots) {
  const verifier = read(root, "oid4vp-verifier-openapi.yaml");
  assert(
    verifier.includes("Credential Format Identifiers from the OID4VP vp_formats_supported object keys"),
    `${label} verifier vpFormats semantics are undocumented`,
  );
  assert(
    verifier.includes("PresentationFormat:") && verifier.includes("enum: [jwt_vp_json, ldp_vp]"),
    `${label} verifier PresentationFormat schema is missing or changed`,
  );
  assert(
    verifier.includes("required: [clientMetadataId, clientId, name, vpFormats, presentationFormats, encryptedResponseEncValues]"),
    `${label} verifier summary must require presentationFormats`,
  );
  assert(
    verifier.includes("$ref: '#/components/schemas/PresentationFormat'"),
    `${label} verifier summary presentationFormats must use PresentationFormat`,
  );
}

console.log(`Credential and presentation format contract guard passed (${openApiRoots.map(([, label]) => label).join(" + ")}).`);
