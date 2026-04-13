package com.sphereon.crypto.core

internal object CryptoCoreInfo {
    // Make sure we have something to keep the compiler happy
    const val MOVED =
        "This package has been replaced by com.sphereon.idk:lib-crypto-core-public and -impl. You can use this as a meta dependency on both, although -impl also already exposes -public as api. Use -public for public API and -impl for implementation details"
}
