# Module lib-data-store-credential-design-public

Public contract for credential-design storage: render metadata, localisation data, and branding that drive how an issued credential is presented to a holder or verifier. Depend on this module from issuer-side code that needs to look up or write design data without binding to a particular storage backend. Protocol-aware runtime implementations live in protocols pack module `lib-data-store-credential-design-impl`.
