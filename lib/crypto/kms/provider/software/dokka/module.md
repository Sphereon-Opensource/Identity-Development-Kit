# Module lib-crypto-kms-provider-software

In-process software KMS provider. Keys are generated, stored, and used directly in the application process using the platform-native cryptography engine. It is the default provider for development, unit and integration tests, and deployments where offloading keys to an external KMS is not a requirement.

For production deployments with strict key-custody requirements, prefer one of the AWS, Azure, mobile, or REST providers instead.
