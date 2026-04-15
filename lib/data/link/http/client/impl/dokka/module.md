# Module lib-data-link-http-client-impl

Runtime for the legacy HTTP client contracts in `lib-data-link-http-client-public`, supplying the command bodies that let the rest of the platform fetch remote URIs and parse URI queries through the IDK command system rather than calling a raw HTTP client directly.

Pull this module in alongside its `-public` counterpart when you depend on the legacy commands; for greenfield code, prefer `HttpClientFactory` from the core API.
