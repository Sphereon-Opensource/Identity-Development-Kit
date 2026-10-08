package com.sphereon.statuslist.impl.codec

private const val COSE_SIGN1_TAG = 0xd2
private const val COSE_SIGN1_ARRAY = 0x84

internal fun ByteArray.tagStatusListCoseSign1(): ByteArray {
    require(isNotEmpty() && (this[0].toInt() and 0xff) == COSE_SIGN1_ARRAY) {
        "status-list COSE_Sign1 must be a four-item CBOR array"
    }
    return byteArrayOf(COSE_SIGN1_TAG.toByte()) + this
}

internal fun ByteArray.untagStatusListCoseSign1(): ByteArray {
    require(size > 1 && (this[0].toInt() and 0xff) == COSE_SIGN1_TAG && (this[1].toInt() and 0xff) == COSE_SIGN1_ARRAY) {
        "status-list CWT must have COSE_Sign1 tag 18 and a four-item array"
    }
    return copyOfRange(1, size)
}
