# Module lib-crypto-kms-provider-rest

Remote KMS provider that delegates every crypto operation to a REST KMS server, typically an instance of `services-kms-rest`. Reach for this in deployments that want the actual key material centralised in a single KMS service, while other IDK components (issuer, verifier, wallet backend) stay stateless with respect to keys.