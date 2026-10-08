# Sphereon OpenAPI specifications

Single source of truth for every Sphereon-owned REST API specification across the three product
layers:

- **IDK**: Identity Development Kit (open-core SDK).
- **EDK**: Enterprise Development Kit.
- **VDX**: Verifiable Data Exchange platform.

Dependencies run `VDX -> EDK -> IDK`, and an API placed in a lower layer is reachable from the
higher ones. Each spec records which products include it via `targets` in `manifest-catalog.json`.
When `targets` is omitted the spec is included in all three. The default is inclusive (EDK consumes
IDK, VDX consumes EDK and IDK); a lower-layer API is excluded from a higher product only when that
product replaces it with its own (for example the IDK `oid4vp-universal` session spec is IDK-only,
because the EDK/VDX `oid4vp-verifier` spec reuses its endpoints by `$ref`).

Third-party reference specs (vendor APIs we integrate but do not own) live under `external/` and
are excluded from aggregation, linting, and release tags.

## Layout

A single flat folder (the repo root) holds every spec, every component bundle, and the one shared
`common-components.yml`. There are no product subfolders and no synced copies. Because every file
is in the same directory, each cross-file `$ref` (`./common-components.yml#/...`,
`./<domain>-components.yml#/...`) resolves with no tooling.

```
common-components.yml         the one canonical shared bundle (errors, pagination, security, tenant headers)
<domain>-openapi.yml          an API spec (a few use the .yaml extension)
<domain>-components.yml        externalized entity/enum/parameter bundle for a spec (e.g. did, kms, dcql)
external/                      vendor reference specs (not aggregated)
manifest-catalog.json         spec -> owning module, division label, and targets
docs-groups.json              capability grouping for the docs sidebar (independent of layout)
redocly.yaml                  lint config
```

A spec keeps its reusable models in a sibling `<domain>-components.yml` and `$ref`s them
(`./<domain>-components.yml#/components/schemas/X`); the main spec re-exports them as thin stubs. A
higher layer extends a lower layer's API by `$ref`ing the same component bundle plus the base
spec's path items, then adding its own, with no duplication. The dcql trio
(`dcql-openapi.yml` -> `dcql-edk-openapi.yml` -> `dcql-vdx-openapi.yml`) is the worked example.

> **Why one flat folder:** the OpenAPI generator used by the consuming Kotlin Multiplatform builds
> resolves cross-subdirectory parameter `$ref`s unreliably, and `common-components.yml` is
> referenced as parameters on nearly every path. A single folder keeps every `$ref`
> same-directory, the one layout the generator handles uniformly, and removes the sync step.

## Operation metadata

Two machine-readable fields on an operation drive the badges rendered in the documentation and the
live console. Both are the single source; the badges are derived at build time.

- `x-products`: the products an operation is available in, as `[idk, edk, vdx]`. An operation-level
  value travels with the operation when another spec reuses it by `$ref`. When absent, the
  operation falls back to the spec-level `info.x-products`, then to the spec's catalog `targets`.
- `x-license-protection`: the license descriptor that gates an operation. `entitlementKey` is the
  canonical `product.module.service.command` key. `deploymentShapePredicates`, `protectionMode`,
  `quotaKeys`, and `domainPolicyRef` describe where and how enforcement applies. `product`,
  `module`, `service`, and `command` may be supplied only as explicit consistency fields when a
  consumer needs them; otherwise they are derived from `entitlementKey`.

## Consuming this repo

Each consuming repository includes this repo as a git submodule and pins it by commit. Today that
is five checkouts: the IDK, EDK, and VDX product builds, the documentation site, and the wallet
frontend. After cloning a consuming repo:

```
git submodule update --init --recursive
```

A build reads the specs from its submodule checkout in place; there is no copy-into-module step.
When the consumers pick up new specs, advance every checkout to the same commit in lockstep so the
products, docs, and frontend stay on one version of the contracts.

`targets` governs which products' documentation include a spec and the default product-availability
badge. It does not, by itself, decide what a running service serves: the live `/openapi` console
advertises only the specs whose declared paths are actually mounted in that build, determined by
route-table introspection. When two specs describe the same mounted endpoints (a layered base and
the product spec that reuses it by `$ref`), the console keeps the more representative one.

## Versioning

This repo defines its own release-tag namespace (`v0.25`, `v0.26`, and so on), independent of any
product repository's tags. A tag is a release coordinate; an individual spec's `info.version` is
independent of it. Documentation snapshots and product builds reference these tags.

## Linting

`node verify-command-ids.mjs` must pass. It rejects every `x-command-id` that is not exactly three
dot-separated `module.service.command` segments.

`npx @redocly/cli lint` must pass. The `no-unresolved-refs` gate proves every same-directory `$ref`
resolves. Remaining stylistic warnings are not release blockers.

## Vendored checkouts and the bump procedure

This repository is vendored as a git submodule in five places. Every checkout tracks branch
`feature/semantics` of this repository and must sit on the same commit:

| Checkout | Superproject | Gitlink path |
|---|---|---|
| `VDX-infra/vdx/edk/idk/openapi` | `vdx/edk/idk` (IDK) | `openapi` |
| `VDX-infra/vdx/edk/openapi` | `vdx/edk` (EDK) | `openapi` |
| `VDX-infra/vdx/openapi` | `vdx` (VDX) | `openapi` |
| docs site `openapi` | docs site root | `openapi` |
| `VDX-frontend/packages/wallet/packages/vdx-shared/openapi-canonical` | `VDX-frontend/packages/wallet` | `packages/vdx-shared/openapi-canonical` |

Bumping is ordered. Do it in exactly this sequence so no superproject records a commit that a
lower layer does not yet carry:

1. Commit the spec change here, in `VDX-infra/vdx/openapi`, on `feature/semantics`, and push.
2. `git pull --ff-only` in the other four checkouts so all five working trees are on that commit.
3. Bump the gitlinks bottom-up: commit the new `openapi` gitlink in `vdx/edk/idk`, then in
   `vdx/edk` (which also picks up the new `idk` gitlink), then in `vdx` (which also picks up the
   new `edk` gitlink), then the `vdx` gitlink in the VDX-infra root.
4. Bump the docs site and `VDX-frontend/packages/wallet` gitlinks separately; they are
   independent superprojects.
5. Never commit anything under `VDX-infra/customer/edk`; that tree is edited in place only.
6. Run `node deploy/edk/e2e/scripts/verify-openapi-checkouts.mjs` from the VDX-infra root. It
   prints one row per checkout and fails when the five HEADs differ; gitlink drift is a warning,
   or an error with `--strict`. The docs site and frontend locations are taken from
   `DOCS_SITE_DIR` and `VDX_FRONTEND_DIR` when they are not at their default paths.

## Vendored checkouts and the bump procedure

This repository is vendored as a git submodule in five places. Every checkout tracks branch
`feature/semantics` of this repository and must sit on the same commit:

| Checkout | Superproject | Gitlink path |
|---|---|---|
| `VDX-infra/vdx/edk/idk/openapi` | `vdx/edk/idk` (IDK) | `openapi` |
| `VDX-infra/vdx/edk/openapi` | `vdx/edk` (EDK) | `openapi` |
| `VDX-infra/vdx/openapi` | `vdx` (VDX) | `openapi` |
| docs site `openapi` | docs site root | `openapi` |
| `VDX-frontend/packages/wallet/packages/vdx-shared/openapi-canonical` | `VDX-frontend/packages/wallet` | `packages/vdx-shared/openapi-canonical` |

Bumping is ordered. Do it in exactly this sequence so no superproject records a commit that a
lower layer does not yet carry:

1. Commit the spec change here, in `VDX-infra/vdx/openapi`, on `feature/semantics`, and push.
2. `git pull --ff-only` in the other four checkouts so all five working trees are on that commit.
3. Bump the gitlinks bottom-up: commit the new `openapi` gitlink in `vdx/edk/idk`, then in
   `vdx/edk` (which also picks up the new `idk` gitlink), then in `vdx` (which also picks up the
   new `edk` gitlink), then the `vdx` gitlink in the VDX-infra root.
4. Bump the docs site and `VDX-frontend/packages/wallet` gitlinks separately; they are
   independent superprojects.
5. Never commit anything under `VDX-infra/customer/edk`; that tree is edited in place only.
6. Run `node deploy/edk/e2e/scripts/verify-openapi-checkouts.mjs` from the VDX-infra root. It
   prints one row per checkout and fails when the five HEADs differ; gitlink drift is a warning,
   or an error with `--strict`. The docs site and frontend locations are taken from
   `DOCS_SITE_DIR` and `VDX_FRONTEND_DIR` when they are not at their default paths.

## Vendored checkouts and the bump procedure

This repository is vendored as a git submodule in five places. Every checkout tracks branch
`feature/semantics` of this repository and must sit on the same commit:

| Checkout | Superproject | Gitlink path |
|---|---|---|
| `VDX-infra/vdx/edk/idk/openapi` | `vdx/edk/idk` (IDK) | `openapi` |
| `VDX-infra/vdx/edk/openapi` | `vdx/edk` (EDK) | `openapi` |
| `VDX-infra/vdx/openapi` | `vdx` (VDX) | `openapi` |
| docs site `openapi` | docs site root | `openapi` |
| `VDX-frontend/packages/wallet/packages/vdx-shared/openapi-canonical` | `VDX-frontend/packages/wallet` | `packages/vdx-shared/openapi-canonical` |

Bumping is ordered. Do it in exactly this sequence so no superproject records a commit that a
lower layer does not yet carry:

1. Commit the spec change here, in `VDX-infra/vdx/openapi`, on `feature/semantics`, and push.
2. `git pull --ff-only` in the other four checkouts so all five working trees are on that commit.
3. Bump the gitlinks bottom-up: commit the new `openapi` gitlink in `vdx/edk/idk`, then in
   `vdx/edk` (which also picks up the new `idk` gitlink), then in `vdx` (which also picks up the
   new `edk` gitlink), then the `vdx` gitlink in the VDX-infra root.
4. Bump the docs site and `VDX-frontend/packages/wallet` gitlinks separately; they are
   independent superprojects.
5. Never commit anything under `VDX-infra/customer/edk`; that tree is edited in place only.
6. Run `node deploy/edk/e2e/scripts/verify-openapi-checkouts.mjs` from the VDX-infra root. It
   prints one row per checkout and fails when the five HEADs differ; gitlink drift is a warning,
   or an error with `--strict`. The docs site and frontend locations are taken from
   `DOCS_SITE_DIR` and `VDX_FRONTEND_DIR` when they are not at their default paths.

## Vendored checkouts and the bump procedure

This repository is vendored as a git submodule in five places. Every checkout tracks branch
`feature/semantics` of this repository and must sit on the same commit:

| Checkout | Superproject | Gitlink path |
|---|---|---|
| `VDX-infra/vdx/edk/idk/openapi` | `vdx/edk/idk` (IDK) | `openapi` |
| `VDX-infra/vdx/edk/openapi` | `vdx/edk` (EDK) | `openapi` |
| `VDX-infra/vdx/openapi` | `vdx` (VDX) | `openapi` |
| docs site `openapi` | docs site root | `openapi` |
| `VDX-frontend/packages/wallet/packages/vdx-shared/openapi-canonical` | `VDX-frontend/packages/wallet` | `packages/vdx-shared/openapi-canonical` |

Bumping is ordered. Do it in exactly this sequence so no superproject records a commit that a
lower layer does not yet carry:

1. Commit the spec change here, in `VDX-infra/vdx/openapi`, on `feature/semantics`, and push.
2. `git pull --ff-only` in the other four checkouts so all five working trees are on that commit.
3. Bump the gitlinks bottom-up: commit the new `openapi` gitlink in `vdx/edk/idk`, then in
   `vdx/edk` (which also picks up the new `idk` gitlink), then in `vdx` (which also picks up the
   new `edk` gitlink), then the `vdx` gitlink in the VDX-infra root.
4. Bump the docs site and `VDX-frontend/packages/wallet` gitlinks separately; they are
   independent superprojects.
5. Never commit anything under `VDX-infra/customer/edk`; that tree is edited in place only.
6. Run `node deploy/edk/e2e/scripts/verify-openapi-checkouts.mjs` from the VDX-infra root. It
   prints one row per checkout and fails when the five HEADs differ; gitlink drift is a warning,
   or an error with `--strict`. The docs site and frontend locations are taken from
   `DOCS_SITE_DIR` and `VDX_FRONTEND_DIR` when they are not at their default paths.
