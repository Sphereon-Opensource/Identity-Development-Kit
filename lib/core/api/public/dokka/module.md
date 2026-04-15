# Module lib-core-api-public

The foundational contract layer that every other IDK module depends on. It defines the primitives the whole SDK is built around: the `IdkResult` monad for explicit success and error flow, the `Command` hierarchy (including chained, pipelined, and compensatable forms) that every domain module exposes business logic through, execution context and session abstractions, the configuration and logging surfaces, the HTTP client adapter contract, caching interfaces, and the service identity and auth header primitives services use to talk to each other.

This module is interface-only. Nothing in it runs on its own; implementations are supplied by `lib-core-api-default`, which is the baseline runtime most applications also pull in. Depend on `-public` from modules that want to stay policy-free (library modules, feature APIs) and let the application compose in the default runtime at the edge.
