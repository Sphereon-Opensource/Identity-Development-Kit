# CBOR Refactor Plan

## Goal

Finish the CBOR refactor by leaving:

- `lib-cbor/public` as low-level CBOR primitives plus stable public contracts
- `lib-cbor/impl` as the concrete CBOR runtime and DI wiring
- `crypto` and `mdoc` public modules as domain-facing APIs, not CBOR runtimes

## Current State

Already done:

- `lib-cbor/impl` owns the generic CBOR runtime and default bindings
- the old public `Cbor` / `CborDecoder` facade is gone
- deprecated builder APIs are gone
- `CborEncodedItem.fromData(...)` is gone
- the temporary `cborSerializer` alias is gone
- the active mdoc engagement/session/transfer runtime no longer depends on public-model CBOR byte assembly
- the public engagement wrappers no longer parse or emit reader-engagement URIs through model-local fallback helpers
- the public mdoc document/issuer and retrieval-model wrappers no longer own the previous CBOR helper APIs
- the remaining session/engagement-manager default paths no longer assemble protocol bytes through public-model fallback code
- the public COSE model layer no longer exposes raw CBOR parsing or byte-encoding helpers; impl-side codecs/helpers own that behavior now
- the last public mdoc helper cleanup is done: `DrivingPrivileges` no longer parses raw CBOR publicly, and the experimental OID4VP helper path now goes through a typed codec
- the old public `CborStructure` / `HasToCbor` / `HasFromCbor` compatibility layer is gone; `lib-cbor/public` now stays on explicit `CborItem` primitives and value-specific conversion methods

The planned refactor work is complete.

## Remaining Work

There is no remaining planned work in this refactor.

## High-Level Phases

### Phase 1: Broad Validation And Cleanup

Run the cross-module verification sweep and clean up any temporary compatibility scaffolding that is no longer needed.

Target outcome:

- the typed-codec boundary is verified end-to-end
- temporary compatibility helpers do not linger without a reason

### Phase 2: Final Boundary Lock

Delete any now-dead compatibility helpers and update docs/tests to the final shape.

Target outcome:

- no accidental public fallback path remains
- tests and examples reflect the typed-codec end state

## Done When

This refactor is complete when:

- `crypto/core/public` and `mdoc/core/public` no longer expose public-model CBOR runtime behavior
- `lib-cbor/public` no longer exposes the old `CborStructure` / `HasToCbor` / `HasFromCbor` abstraction layer
- public model code does not directly call `CborSupport.serializer` for protocol parsing
- public helper/builders do not assemble encoded COSE or CBOR bytes directly
- the broad verification pass below is green:

```text
./gradlew --rerun-tasks :lib-cbor-public:jvmTest :lib-crypto-core-impl:jvmTest :lib-mdoc-core-public:jvmTest :lib-mdoc-core-impl:jvmTest :lib-mdoc-reader:compileKotlinJvm :lib-mdoc-transport-restapi:jvmTest --console=plain
```

Status: complete. The verification command above is green, the temporary compatibility helpers were removed, and the old `CborStructure` / `HasToCbor` / `HasFromCbor` boundary has been deleted.
