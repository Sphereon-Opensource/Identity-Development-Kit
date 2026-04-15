# Module lib-core-test

Test fixtures for modules that sit on top of `lib-core-api-public`. It provides a minimal application graph stub so unit tests can exercise commands, configuration, and caching against a working runtime without booting the full IDK stack. Reach for it in library tests; it is not intended for production wiring.
