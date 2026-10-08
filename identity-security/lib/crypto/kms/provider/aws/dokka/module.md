# Module lib-crypto-kms-provider-aws

KMS provider that delegates signing and key operations to AWS KMS. Private key material never leaves AWS-managed HSMs; IDK only holds references and issues operations over the AWS SDK.

Reach for this module when a deployment must keep key material inside AWS; for local development the software provider is usually a better fit.
