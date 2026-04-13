package com.sphereon.crypto.core.kms

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("HasKeyStoreService", exact = true)
interface HasKeyStoreService {
    val keyStore: KeyStoreService
}