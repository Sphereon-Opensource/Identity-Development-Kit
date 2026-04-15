# Module lib-core-benchmarks

JMH microbenchmarks for the IDK core runtime. Guards against regressions on the hot paths that matter most across the platform: serialization, command dispatch, and scoped cache access. Not part of the runtime API surface; only relevant when working on the core runtime itself.
