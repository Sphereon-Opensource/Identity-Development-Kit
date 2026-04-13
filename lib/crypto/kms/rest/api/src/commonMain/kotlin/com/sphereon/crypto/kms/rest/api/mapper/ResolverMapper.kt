/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms.rest.api.mapper

import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.kms.rest.api.generated.models.IdentifierMethod
import com.sphereon.crypto.kms.rest.api.generated.models.KeyType
import com.sphereon.crypto.kms.rest.api.generated.models.ListResolversResponse
import com.sphereon.crypto.kms.rest.api.generated.models.Resolver

fun KeyResolverService.toRest(): Resolver =
    Resolver(
        resolverId = this.getId(),
        supportedIdentifierMethods = this.allSupportedIdentifierMethods().map { it -> IdentifierMethod.valueOf(it.name.uppercase()) }.toTypedArray(),
        supportedKeyTypes = this.allSupportedKeyTypes().map { it -> KeyType.valueOf(it.jose.name.uppercase()) }.toTypedArray(),
    )

fun Array<Resolver>.toRestResponse(): ListResolversResponse =
    ListResolversResponse(
        resolvers = this,
    )
