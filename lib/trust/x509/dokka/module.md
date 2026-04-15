# Module lib-trust-x509

X.509 PKI trust validator plugged into the trust-core framework. It validates counterparty certificates against configured trust stores (PKIX path building, revocation where supplied) and extracts entity info from the certificate subject and extensions.

Reach for this module whenever trust is anchored in a traditional CA hierarchy, for example a private PKI, a government issuer root, or a mutually-trusted operator root. Anchor material is loaded from local files: make sure deployment pipelines ship the trust store alongside the binary.
