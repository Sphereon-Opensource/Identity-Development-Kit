# Bundled Public Suffix List

`public_suffix_list.dat` is the complete list retrieved from the authoritative
`https://publicsuffix.org/list/public_suffix_list.dat` endpoint on 2026-07-13.
It includes both the ICANN and PRIVATE sections.

- Last-Modified: `Mon, 13 Jul 2026 17:13:16 GMT`
- ETag: `d27c9bb250c91b673c598e3f05174932`
- SHA-256: `348c18cc9cf86866917b50133c01dced82a329e29566337205ac816d36e1a72f`
- License: Mozilla Public License 2.0, as declared in the source file header

The build verifies the digest and generates common Kotlin rule data. Runtime
domain resolution is offline and never fetches this list or calls VDX.
