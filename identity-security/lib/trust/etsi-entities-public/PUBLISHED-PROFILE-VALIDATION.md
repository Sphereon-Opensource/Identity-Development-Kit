# Published LoTE profile validation

`LoTEProfileValidation` checks the six published profiles in ETSI TS 119 602
V1.1.1 Annexes D–I. This does not establish that a publisher is an authorized
European Commission operator, grant provider admission, or validate a signature.
The provisional mDL profile and TS 119 612 are separate.

The checks include explicit scheme metadata, the fixed EU territory and profile
URIs, a maximum of six calendar months, membership/history rules and Pub-EAA
historical SKI identities. The registration-certificate status URI retains the
`WRPRCrovidersList` spelling printed in both C.2.2 and G.1. The PID rules URI uses
`PIDProviders`, as printed in D.1, rather than `PIDProvidersList`.

Self-pointer qualifiers are optional. Supplied qualifiers must match the scheme;
the signature consumer must bind a candidate self-pointer identity to the actual
authenticated signing certificate. This does not dereference the pointer or
prove that its remote location serves the same artifact. A mirror's retrieval
URL need not equal the canonical location. Scheme-information URI roles also
require publisher/operator verification beyond structural URI presence.

Wallet services support exactly one extension object of this form:

```json
{"ServiceUniqueIdentifier":"urn:example:wallet-solution:1"}
```

This is a narrow supported interpretation of the named URI definition in the
pinned `1960201_json_schema_sie.json` at commit
`e84f427f0cde99513b574ef4b5a155ac4a38eab6`. That companion schema does not define
the enclosing array-item encoding or a criticality envelope. This is not a
claim that all published EU lists use this representation. Extra keys, duplicate
identifiers and unknown extensions are rejected, including unknown objects
claiming `critical: false`.

Pub-EAA transition validation requires the corresponding previously authenticated
service. An unchanged status may predate a later list reissuance; a new status
must not be backdated before the publication introducing it. Runtime consumers
must separately reject material whose status is not yet effective. Historical
inspection is not a runtime authorization decision.

Sources: [ETSI TS 119 602 V1.1.1](https://www.etsi.org/deliver/etsi_TS/119600_119699/119602/01.01.01_60/ts_119602v010101p.pdf),
[pinned extension definition](https://forge.etsi.org/rep/esi/x19_60201_lists_of_trusted_entities/-/blob/e84f427f0cde99513b574ef4b5a155ac4a38eab6/1960201_json_schema/1960201_json_schema_sie.json).


Pub-EAA TETradeName must contain the H.2 `OJ:` legal-basis URI with a two-character territory and a nonempty law identifier. The check validates syntax and presence only; it does not establish notification, legal authority, or registration.

Status-transition checks need authenticated corresponding prior service state. An initial observation cannot establish when a status was introduced. Consumers must not infer a transition by joining unrelated provider names; changes to source-scoped service identity may prevent comparison and remain an explicit local-history limitation.
