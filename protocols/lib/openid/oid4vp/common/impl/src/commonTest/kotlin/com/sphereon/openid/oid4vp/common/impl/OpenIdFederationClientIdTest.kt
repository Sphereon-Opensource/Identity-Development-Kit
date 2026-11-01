package com.sphereon.openid.oid4vp.common.impl

import com.sphereon.openid.oid4vp.common.ClientIdValidationErrorType
import com.sphereon.openid.oid4vp.common.JarSignerMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenIdFederationClientIdTest {
    private val entity = "https://verifier.example.com/oid4vp/tenant"

    @Test
    fun federationSignedRequestWithHttpsEntityIsValid() {
        assertTrue(openIdFederationClientIdErrors(entity, jarUsed = true, JarSignerMethod.FEDERATION).isEmpty())
    }

    @Test
    fun unsignedOrHeaderKeyedRequestIsRejected() {
        assertEquals(
            listOf(ClientIdValidationErrorType.JAR_REQUIRED),
            openIdFederationClientIdErrors(entity, jarUsed = false, null).map { it.type },
        )
        assertEquals(
            listOf(ClientIdValidationErrorType.JAR_REQUIRED),
            openIdFederationClientIdErrors(entity, jarUsed = true, JarSignerMethod.X5C).map { it.type },
        )
    }

    @Test
    fun entityIdentifierMustBeHttpsWithoutQueryOrFragment() {
        for (bad in listOf("http://verifier.example.com", "https://", "https://verifier.example.com?x=1", "https://verifier.example.com#f")) {
            assertEquals(
                listOf(ClientIdValidationErrorType.VALIDATION_ERROR),
                openIdFederationClientIdErrors(bad, jarUsed = true, JarSignerMethod.FEDERATION).map { it.type },
                bad,
            )
        }
    }
}
