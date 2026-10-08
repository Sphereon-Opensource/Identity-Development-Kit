# Module lib-crypto-key-persistence-api

Public contract for a tenant-scoped store of key references. The store persists pointers to keys held by a KMS, not the key material itself: IDK treats private keys as always living inside a KMS, and this layer records which tenant owns which KMS-held key for which purpose.

Depend on this module from any code that needs to look up or register key references. Backing implementations live in `lib-crypto-key-persistence-impl` (orchestration) and `lib-crypto-key-persistence-sqlite` (durable storage); a no-op is available for setups that do not need persistence.
