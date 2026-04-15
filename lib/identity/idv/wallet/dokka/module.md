# Module lib-idv-wallet

Wallet driver for IDK Identity Verification. It runs an IDV flow by requesting credentials from a holder wallet over OID4VP, taking advantage of the verifier stack already present in IDK. Reach for this when the source of truth for a verification is a credential in a user-controlled wallet, not an external IdP.