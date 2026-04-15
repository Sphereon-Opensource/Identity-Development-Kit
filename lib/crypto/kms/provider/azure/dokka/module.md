# Module lib-crypto-kms-provider-azure

KMS provider that delegates signing and key operations to Azure Key Vault. Private key material stays inside Azure-managed HSMs; IDK only holds references and issues operations over the Azure SDK.

Reach for this module when a deployment requires key material to remain in Azure; for local development the software provider is usually a better fit.
