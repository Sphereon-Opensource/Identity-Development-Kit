# Module lib-data-link-http-client-public

Legacy HTTP client contracts retained for modules that still consume the older `LegacyHttpClientFactory` and the legacy TLS / SSL configuration surface. The associated command bindings let older code fetch URIs and parse URI queries through the IDK command system.

New code should prefer `HttpClientFactory` from the core API surface. Runtime command bodies for this module live in `lib-data-link-http-client-impl`.
