/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.core.api.cache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Serializer interface for cache keys and values.
 *
 * Enables type-safe serialization of cache entries for distributed backends
 * that require byte array or string representation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheSerializer", exact = true)
interface CacheSerializer<T : Any> {
    /**
     * Serialize a value to bytes.
     */
    fun serialize(value: T): ByteArray

    /**
     * Deserialize bytes back to a value.
     */
    fun deserialize(bytes: ByteArray): T

    /**
     * Serialize a value to a string key.
     * Used for cache key representation.
     */
    fun serializeToString(value: T): String = serialize(value).decodeToString()

    /**
     * Deserialize a string key back to a value.
     */
    fun deserializeFromString(string: String): T = deserialize(string.encodeToByteArray())
}

/**
 * Standard serializers for common cache key and value types.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheSerializers", exact = true)
object CacheSerializers {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * String serializer - pass through.
     */
    val string: CacheSerializer<String> = object : CacheSerializer<String> {
        override fun serialize(value: String): ByteArray = value.encodeToByteArray()
        override fun deserialize(bytes: ByteArray): String = bytes.decodeToString()
        override fun serializeToString(value: String): String = value
        override fun deserializeFromString(string: String): String = string
    }

    /**
     * ByteArray serializer - pass through.
     */
    val byteArray: CacheSerializer<ByteArray> = object : CacheSerializer<ByteArray> {
        override fun serialize(value: ByteArray): ByteArray = value
        override fun deserialize(bytes: ByteArray): ByteArray = bytes
    }

    /**
     * Int serializer.
     */
    val int: CacheSerializer<Int> = object : CacheSerializer<Int> {
        override fun serialize(value: Int): ByteArray = value.toString().encodeToByteArray()
        override fun deserialize(bytes: ByteArray): Int = bytes.decodeToString().toInt()
        override fun serializeToString(value: Int): String = value.toString()
        override fun deserializeFromString(string: String): Int = string.toInt()
    }

    /**
     * Long serializer.
     */
    val long: CacheSerializer<Long> = object : CacheSerializer<Long> {
        override fun serialize(value: Long): ByteArray = value.toString().encodeToByteArray()
        override fun deserialize(bytes: ByteArray): Long = bytes.decodeToString().toLong()
        override fun serializeToString(value: Long): String = value.toString()
        override fun deserializeFromString(string: String): Long = string.toLong()
    }

    /**
     * Boolean serializer.
     */
    val boolean: CacheSerializer<Boolean> = object : CacheSerializer<Boolean> {
        override fun serialize(value: Boolean): ByteArray = if (value) "1".encodeToByteArray() else "0".encodeToByteArray()
        override fun deserialize(bytes: ByteArray): Boolean = bytes.decodeToString() == "1"
        override fun serializeToString(value: Boolean): String = if (value) "1" else "0"
        override fun deserializeFromString(string: String): Boolean = string == "1"
    }

    /**
     * JSON serializer for arbitrary serializable types.
     */
    inline fun <reified T : Any> json(): CacheSerializer<T> = json(serializer<T>())

    /**
     * JSON serializer with explicit KSerializer.
     */
    fun <T : Any> json(serializer: KSerializer<T>): CacheSerializer<T> = object : CacheSerializer<T> {
        override fun serialize(value: T): ByteArray = json.encodeToString(serializer, value).encodeToByteArray()
        override fun deserialize(bytes: ByteArray): T = json.decodeFromString(serializer, bytes.decodeToString())
        override fun serializeToString(value: T): String = json.encodeToString(serializer, value)
        override fun deserializeFromString(string: String): T = json.decodeFromString(serializer, string)
    }
}
