package com.sphereon.crypto.kms.rest.api.mapper

import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.kms.rest.api.generated.models.KeyProvider
import com.sphereon.crypto.kms.rest.api.generated.models.KeyProviderType
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeyProvidersResponse

fun KmsProvider.toRest() : KeyProvider {
    return KeyProvider(
        providerId = this.id,
        type = KeyProviderType.valueOf(this.kmsProviderType.uppercase())
    )
}

fun Array<KeyProvider>.toRestResponse() : ListKeyProvidersResponse {
    return ListKeyProvidersResponse(
        providers = this
    )
}


