# TS 119 602 wire fixtures

`pid-wire-fixture.json` was hand-authored from the published TS 119 602 V1.1.1
schema, not produced by the library serializer. It is an unsigned structural
fixture: its certificate value is deliberately dummy base64, not signing or
certificate validation evidence. Its commonTest literal permits the same checks
on JVM, JS and Wasm without filesystem APIs.

Source: https://forge.etsi.org/rep/esi/x19_60201_lists_of_trusted_entities/-/raw/e84f427f0cde99513b574ef4b5a155ac4a38eab6/1960201_json_schema/1960201_json_schema.json

Original SHA-256: `f16d60477359b936cefe0c74d5f1c598e3346daf84a6bad1846c712381ca36b4`.

The original schema is preserved byte-for-byte. It contains a misplaced draft-07 keyword:
`/definitions/ServiceDigitalIdentity/properties/additionalProperties` is `false`,
which declares a property, although
this appears to be the intended additional-properties keyword misplaced inside
the property map. Boolean schemas are legal draft-07, so mechanically this defines
a forbidden property named additionalProperties and leaves other members open.
The corrected oracle moves only that keyword one level up:

```diff
- /definitions/ServiceDigitalIdentity/properties/additionalProperties: false
+ /definitions/ServiceDigitalIdentity/additionalProperties: false
```

Tests against the corrected oracle must not be reported as validation against
an unmodified official schema. Runtime checks mirror the corrected structural
binding, while extension payloads remain open. Profile semantics, signatures,
certificate validity, and scheme admission are separate validation steps.
