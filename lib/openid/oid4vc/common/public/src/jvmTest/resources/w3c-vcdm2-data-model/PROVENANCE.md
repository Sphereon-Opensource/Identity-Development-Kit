# W3C Verifiable Credentials Data Model 2.0 vectors

These JSON resources are copied byte-for-byte from the official W3C test suite
at the pinned commit below. The resource names flatten the upstream
`tests/input/names-and-descriptions/` directory; the original path is recorded
for every flattened resource.

- Repository: https://github.com/w3c/vc-data-model-2.0-test-suite
- Pinned commit: `704eafe8d50dd0c7cad88ed9101b05f43c62a7fb`
- Commit URL: https://github.com/w3c/vc-data-model-2.0-test-suite/commit/704eafe8d50dd0c7cad88ed9101b05f43c62a7fb
- Raw base URL: https://raw.githubusercontent.com/w3c/vc-data-model-2.0-test-suite/704eafe8d50dd0c7cad88ed9101b05f43c62a7fb/
- License: [W3C test suite license](https://www.w3.org/copyright/test-suite-license-2023/) and [W3C 3-clause BSD license](https://www.w3.org/copyright/3-clause-bsd-license-2008/); see `LICENSE.md`.

## Source paths

All paths below are under the pinned raw base URL.

- `credential-ok.json` <- `tests/input/credential-ok.json`
- `credential-context-combo2-ok.json` <- `tests/input/credential-context-combo2-ok.json`
- `credential-type-url-ok.json` <- `tests/input/credential-type-url-ok.json`
- `credential-type-mapped-url-ok.json` <- `tests/input/credential-type-mapped-url-ok.json`
- `credential-validuntil-ok.json` <- `tests/input/credential-validuntil-ok.json`
- `credential-status-ok.json` <- `tests/input/credential-status-ok.json`
- `credential-schema-ok.json` <- `tests/input/credential-schema-ok.json`
- `credential-multi-language-name-ok.json` <- `tests/input/names-and-descriptions/credential-multi-language-name-ok.json`
- `credential-name-extra-prop-en-fail.json` <- `tests/input/names-and-descriptions/credential-name-extra-prop-en-fail.json`
- `credential-no-context-fail-or-inject.json` <- `tests/input/credential-no-context-fail-or-inject.json`
- `credential-context-combo3-fail.json` <- `tests/input/credential-context-combo3-fail.json`
- `credential-no-type-fail.json` <- `tests/input/credential-no-type-fail.json`
- `credential-issuer-no-url-fail.json` <- `tests/input/credential-issuer-no-url-fail.json`
- `credential-subject-no-claims-fail.json` <- `tests/input/credential-subject-no-claims-fail.json`
- `credential-validuntil-invalid-fail.json` <- `tests/input/credential-validuntil-invalid-fail.json`
- `credential-status-missing-type-fail.json` <- `tests/input/credential-status-missing-type-fail.json`
- `credential-schema-no-id-fail.json` <- `tests/input/credential-schema-no-id-fail.json`
- `presentation-ok.json` <- `tests/input/presentation-ok.json`
- `presentation-holder-object-ok.json` <- `tests/input/presentation-holder-object-ok.json`
- `presentation-multiple-vc-ok.json` <- `tests/input/presentation-multiple-vc-ok.json`
- `presentation-context-combo2-ok.json` <- `tests/input/presentation-context-combo2-ok.json`
- `presentation-enveloped-vc-ok.json` <- `tests/input/presentation-enveloped-vc-ok.json`
- `presentation-no-type-fail.json` <- `tests/input/presentation-no-type-fail.json`
- `presentation-context-order-fail.json` <- `tests/input/presentation-context-order-fail.json`
- `presentation-vc-as-string-fail.json` <- `tests/input/presentation-vc-as-string-fail.json`

## Coverage mapping

The selected vectors cover the official suite sections for contexts (`tests/4.03-contexts.js`),
types (`tests/4.05-types.js`), issuer (`tests/4.07-issuer.js`), credential subjects
(`tests/4.08-credential-subject.js`), validity (`tests/4.09-validity-period.js`), status
(`tests/4.10-status.js`), schemas (`tests/4.11-data-schemas.js`), presentations
(`tests/4.13-verifiable-presentations.js`), and envelopes (`tests/4.13.2-envelopes.js`).
Each is available at the pinned raw base URL above.
- `presentation-enveloped-vc-missing-type-fail.json` <- `tests/input/presentation-enveloped-vc-missing-type-fail.json`

The official suite has no static nested `EnvelopedVerifiablePresentation`
input at this commit. The test's nested-presentation case is explicitly a
derived shape-only object and is not claimed as an official vector.

The suite's positive credential inputs are issuer request inputs and are
intentionally sparse (for example, `credential-ok.json` has no issuer). The
test therefore uses `VcdmClassifier` for positive credential shape/version
coverage, while applying `VcdmProfiles.v2_0.validateCredential` to the
complete negative inputs. Positive presentation inputs are validated directly
because their data-model rules do not require issuer enrichment.
