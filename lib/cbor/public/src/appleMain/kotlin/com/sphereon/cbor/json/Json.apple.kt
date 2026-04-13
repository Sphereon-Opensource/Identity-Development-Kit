package com.sphereon.cbor.json

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding

@OptIn(ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST")
actual fun <T> toJsonDTO(subject: HasToJsonString): T {
    val jsonString = subject.toJsonString()
    val nsString = NSString.create(string = jsonString)
    val nsData = nsString.dataUsingEncoding(NSUTF8StringEncoding)
        ?: throw IllegalArgumentException("Failed to encode string to NSData")

    return memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val jsonObject = NSJSONSerialization.JSONObjectWithData(
            data = nsData,
            options = 0u,
            error = error.ptr
        )

        error.value?.let { throw Exception("JSON parsing failed: ${it.localizedDescription}") }
        jsonObject as T
    }
}
