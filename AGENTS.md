# AGENTS.md

The IDK (Identity Development Kit) is an open-source Kotlin Multiplatform SDK for digital identity: DIDs, key management, OAuth2 and OpenID (OID4VCI, OID4VP), SD-JWT, mdoc, status lists, trust and wallet support. Libraries are in `lib/<area>/`, runnable services in `services/`, samples in `examples/`, and guides in `docs/`.

## Rules

- This is a public repository. Keep it self-contained: no references to downstream products, private repositories or internal hostnames in code, comments or docs.
- Targets are JVM, Android, iOS, JS, wasmJs and Linux. Put code in `commonMain` and use `expect`/`actual` only when a platform really needs it. Keep public types JS-safe (plain data classes and enums).
- Public API goes in `-public` modules and implementations in `-impl`. Inject interfaces, never implementations.
- DI is Metro (`dev.zacsweers.metro`). Ship safe defaults (no-op, in-memory, fail-closed) that downstream modules can replace with `@ContributesBinding(replaces = [...])`.
- API-facing functions return `IdkResult<V, E>` with `IdkError`, not `kotlin.Result`. `isOk` and `isErr` are properties. Use `ParsedDid.tryParse()`, never `parse()`.
- Business logic lives in commands (`ServiceCommand`). HTTP is `HttpEndpointCommand` through `CommandBackedHttpAdapter`, not hand-written Ktor routes.
- OpenAPI first: edit specs under `openapi/`, never generated code.
- Use the session `LogService`, `HttpClientFactory` for HTTP, and the `Encoding` extensions for base64.
- Test names must not use backtick style (JS targets). Prefer composing the real DI graph over hand-written fakes.
- Do not commit, push, stash or change branches unless the user asks.
