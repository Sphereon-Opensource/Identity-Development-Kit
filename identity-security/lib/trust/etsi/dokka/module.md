# Module lib-trust-etsi

ETSI / eIDAS trust-list validator plugged into the trust-core framework. It resolves ETSI Lists of Trusted Lists (LOTLs), walks them into concrete trusted-service certificates, and validates counterparty certificates against the resulting anchor set. Reach for this in EU-regulated deployments where trust must be grounded in the eIDAS LOTL.

This is a network-active module: LOTL fetches hit the published ETSI endpoints, and deployments that lock down outbound network must allow them, with cached anchors bridging transient outages. The LOTL data model itself lives in `lib-trust-etsi-entities-public`.
