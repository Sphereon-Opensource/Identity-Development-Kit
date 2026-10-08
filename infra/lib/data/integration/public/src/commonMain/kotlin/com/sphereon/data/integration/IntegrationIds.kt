/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.data.integration

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ResourceDescriptorId(
    val value: String,
) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class FieldDescriptorId(
    val value: String,
) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class TransformationId(
    val value: String,
) {
    override fun toString(): String = value
}
