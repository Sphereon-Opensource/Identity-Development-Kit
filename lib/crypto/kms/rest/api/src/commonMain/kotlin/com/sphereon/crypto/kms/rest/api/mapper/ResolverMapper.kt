package com.sphereon.crypto.kms.rest.api.mapper

import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.kms.rest.api.generated.models.IdentifierMethod
import com.sphereon.crypto.kms.rest.api.generated.models.KeyType
import com.sphereon.crypto.kms.rest.api.generated.models.ListResolversResponse
import com.sphereon.crypto.kms.rest.api.generated.models.Resolver

fun KeyResolverService.toRest(): Resolver {
    return Resolver(
        resolverId = this.getId(),
        supportedIdentifierMethods = this.allSupportedIdentifierMethods().map { it -> IdentifierMethod.valueOf(it.name.uppercase()) }.toTypedArray(),
        supportedKeyTypes = this.allSupportedKeyTypes().map { it -> KeyType.valueOf(it.jose.name.uppercase()) }.toTypedArray()
    )
}

fun Array<Resolver>.toRestResponse() : ListResolversResponse {
    return ListResolversResponse(
        resolvers = this
    )
}
