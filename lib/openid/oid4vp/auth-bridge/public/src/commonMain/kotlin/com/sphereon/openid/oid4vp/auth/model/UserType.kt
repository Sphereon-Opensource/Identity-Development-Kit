package com.sphereon.openid.oid4vp.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type of user account.
 */
@Serializable
enum class UserType {
    @SerialName("INTERNAL")
    INTERNAL,

    @SerialName("EXTERNAL")
    EXTERNAL
}
