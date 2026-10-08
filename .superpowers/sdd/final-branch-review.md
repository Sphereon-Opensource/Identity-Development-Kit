# Final whole-branch review — IDK thin-root hybrid packs

Date: 2026-09-19

## Scope

Reviewed the option A thin IDK root + option C hybrid selective-pack pilot against:

- `docs/superpowers/specs/2026-09-19-idk-thin-root-hybrid-packs-design.md`
- `docs/superpowers/plans/2026-09-19-idk-thin-root-hybrid-packs.md`
- IDK `pilot/artifact-mode` at `9432d129ab27ec2db10b2fb3def434067fb1eb8f`
- VDX-infra `68f95f8e5132fc02d356c5dbbe659f6cc51b16d9`
- VDX `d25df73234cbaafc5f359bd1d9c804e8967f10d0`
- EDK `23befb1efeb67210d0133f38ca04f58bba6f76be`
- Phase/task reports and the M1-M7 evidence

The review covered the thin-root cutover, pack ownership/settings produced by the preceding layout work, selective substitution wiring, artifact-default behavior, environment parsing, cross-repository parity, and disclosed pilot limitations.

## Verdict

**APPROVE (confidence 95%)**

No Critical, Important, or Minor issues meeting the review confidence threshold were found.

## Design and integration findings

### Thin root and hybrid behavior

- The IDK root no longer registers the production modules under `core/`, `platform/`, `infra/`, `identity-security/`, `protocols/`, `wallet-lib/`, or `wallet/`.
- Every pack remains a standalone Gradle root and sets its cross-pack consumption flags to artifact mode. Selecting one pack therefore does not implicitly expand the other five packs.
- Empty or unset `IDK_LOCAL_PACKS` creates no pack `includeBuild`. The VDX-infra, VDX, and EDK settings all retain `USE_LOCAL_IDK=false` as an explicit artifact-only override.
- Listed packs are allow-listed, deduplicated, and included with stable `idk-<pack>` names. Substitution is limited to module names declared by that pack's `includeLocal`/`includeMapped` entries.

### Parsing and security

- Parsing trims entries, ignores empty comma segments, remains case-sensitive, rejects wildcard/unknown values, and throws `GradleException` with an allow-list message.
- Pack names cannot inject paths because only exact allow-listed tokens reach `idkRoot.resolve(pack)`.
- Pack settings existence and non-empty extracted module lists fail closed before `includeBuild`.
- `IDK_CHECKOUT` is an explicit developer checkout override. It can select another local Gradle checkout, but this is trusted build-environment input and does not introduce an additional remote-input execution boundary.

### Cross-repository parity

- VDX-infra, VDX, and EDK implement the same pack allow-list, parser, extractor, checkout override, and artifact-default semantics.
- No production monolith IDK `includeBuild` remains in those settings; the remaining IDK-path `includeBuild` references are for Gradle Build Support.
- EDK preserves its parent-composite substitution behavior and forms-profile filtering while applying selection at pack granularity.

## Verification

The documented M1-M7 evidence is internally consistent and maps to the implementation:

- M1 validates extraction of the core pack.
- M2 demonstrates that production pack projects are absent from the thin root.
- M3-M4 compile the standalone core and protocols packs successfully.
- M5 verifies an artifact-only VDX-infra configuration.
- M6 verifies selective `core` inclusion.
- M7 verifies invalid-token failure in all three consumer settings.

This final review also ran two fresh checks against the final cross-repository settings state:

1. VDX-infra `:shared:tasks --all` with `USE_LOCAL_IDK=true` and empty `IDK_LOCAL_PACKS`: **BUILD SUCCESSFUL**; no IDK pack was included.
2. The same nested VDX-infra → VDX → EDK configuration with `IDK_LOCAL_PACKS=core` and `IDK_CHECKOUT=<artifact-pilot>`: **BUILD SUCCESSFUL**; only `core` was reported as an IDK pack source build.

These checks close the earlier evidence gap where the original infra selective smoke preceded the final VDX and EDK settings commits.

## Accepted pilot gaps

The following are disclosed and do not invalidate the pilot contract:

- Kept root examples/tests still use removed `projects.*` accessors, so the thin-root `projects` invocation does not complete after proving pack projects are absent.
- The helper is duplicated across settings scripts.
- Parent gitlinks and the main IDK submodule pointer have not been advanced; selective source use currently needs `IDK_CHECKOUT` to target the pilot worktree.
- Gate logs are worktree evidence rather than committed artifacts.
- Selected packs can appear at multiple levels of the nested composite. The final integrated selective configure check succeeds, so this is an optimization/follow-up rather than a demonstrated correctness defect.

## Conclusion

The implementation satisfies the pilot's thin-root and hybrid-pack contract: artifact resolution is the no-pack default, source coupling is explicit and selective, invalid selection fails closed, and all participating settings roots have matching behavior. The known follow-ups are accurately documented and do not conceal a broken default or missing critical wiring.
