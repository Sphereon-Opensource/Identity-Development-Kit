# W3C JSON-LD 1.1 remote-document vectors

Source repository: https://github.com/w3c/json-ld-api
Pinned source commit: `ffdb326121ea89b7b8280e76a5caea923834bcef`
Source license: W3C Software and Document License (see `LICENSE.md`).

The files under `remote-doc/` are vendored test inputs from that commit. They
are intentionally local test resources: production code never downloads test
vectors at runtime. The HTTP behavior exercised by `HttpLinkedDataDocumentLoaderTest`
corresponds to the pinned manifest cases for JSON-LD media types, extension
`+json` media types, redirects, alternate links, and context links.
