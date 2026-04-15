# Module lib-core-api-default

Default runtime realising the contracts declared in `lib-core-api-public`. It is the baseline application plumbing every IDK-based app needs before any domain modules are layered on: command execution wiring, scoped caching, a streaming codec registry (with the JSON codec registered), and the standard startup graph that brings the `AppScope` to life.

You almost always want this on the classpath: `lib-core-api-public` alone gives you only interfaces; `lib-core-api-default` is what makes an IDK application actually boot.
