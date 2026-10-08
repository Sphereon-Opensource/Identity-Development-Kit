# Module lib-crypto-key-persistence-impl

Orchestration layer on top of the key-reference contracts in `lib-crypto-key-persistence-api`. It selects the appropriate backing store, registers newly managed keys, and resolves the operational mode (for example which store to read from when multiple are available).

Pair this module with a concrete backing such as `lib-crypto-key-persistence-sqlite` to get a working durable key-reference store.
