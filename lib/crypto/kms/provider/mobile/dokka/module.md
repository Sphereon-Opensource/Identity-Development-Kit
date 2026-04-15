# Module lib-crypto-kms-provider-mobile

KMS provider backed by on-device secure enclaves: iOS Secure Enclave and Android Keystore. It lets a wallet or other mobile app generate and use signing keys that never leave the device's hardware-backed key store, which is the baseline expectation for holder-bound credentials and device-bound mdoc presentations.

This module is only meaningful on iOS and Android targets; on other platforms, use the software, AWS, Azure, or REST provider.
