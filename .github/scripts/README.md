# Standalone pack CI

`pack_ci.py` runs from the IDK checkout alone. It reads the six pack settings and
their declared module build dependencies, detects production pack cycles, and
orders their preparation. No source composite is needed for cross-pack inputs.

Each pack first publishes its production variants into a Maven repository private
to the current CI job. Other packs resolve IDK coordinates exclusively from this
repository; published build plugins and third-party inputs use the ordinary
configured repositories. The repository is removed when the helper exits.

Checks run for all six packs and the IDK root after every production pack is
available. This supports test dependencies that point back to a later pack while
keeping production dependencies acyclic. Existing public/private test exclusions
are scoped to the pack that owns the named module. Runtime settings come from
Gradle properties and the owning CI environment; this helper does not set worker
or heap limits. The full public target selection and private JVM selection remain.

PR and manual builds prepare only job-private Maven inputs. Remote publication
requires the existing public repository, `push` event and `main` or `develop`
branch, and starts only after every check passes. The init script uses the
existing IDK Nexus repository names, URLs, version policy and credential variables.
Pushing a feature branch to the private repository does not publish public Maven
prerequisites. The existing private-to-public mirror/release workflow must still
deliver an approved main/develop push before other standalone repositories can
consume the new version remotely.

Inspect the plan without executing Gradle:

```sh
python3 -B .github/scripts/pack_ci.py --mode private --plan
python3 -B -m unittest discover -s .github/scripts -p test_pack_ci.py -v
```

Execution through this helper requires GitHub Actions. It does not provide a
manual artifact-upload command.

Explicit public-repository workflow dispatch accepts `publish: true` only on `main`,
`develop`, or `codex/infra-owned-workspaces`. The default remains validation only.
The runner refuses remote publication outside approved owning repository/branch routes,
and tag refs before starting Gradle. Owning-repository Nexus credentials are required.

The private owning integration branch also supports explicit `publish: true`
dispatch for JVM Maven bootstrap. It prepares all packs and runs their checks
before `publishCiMavenRemote` selects only Maven publication tasks for the
existing opensource Nexus target. It never requests npm publication or Git
source mirroring. Normal private PR validation and public CI are unchanged.

After bootstrap, private CI `remote_only: true` skips every pack preparation
stage and removes file Maven repositories before settings/catalog/plugin
resolution. It runs normal fresh pack checks using actual remote publications.
It cannot be combined with publication. Local source remains confined to each
standalone pack; cross-pack inputs must resolve remotely.
