package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.JarSignerMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestObjectSignerMethodTest {
    private val chain = listOf("MIIB")

    @Test
    fun federationRequestObjectIsFederationSignedEvenWithAnX5cHeader() {
        assertEquals(JarSignerMethod.FEDERATION, requestObjectSignerMethod(ClientIdScheme.OPENID_FEDERATION, true, chain, "kid-1"))
        assertEquals(JarSignerMethod.FEDERATION, requestObjectSignerMethod(ClientIdScheme.OPENID_FEDERATION, true, null, null))
    }

    @Test
    fun otherPrefixesKeepHeaderDetection() {
        assertEquals(JarSignerMethod.X5C, requestObjectSignerMethod(ClientIdScheme.X509_SAN_DNS, true, chain, null))
        assertEquals(JarSignerMethod.DID, requestObjectSignerMethod(ClientIdScheme.DECENTRALIZED_IDENTIFIER, true, null, "did:web:example.com#key-1"))
    }

    @Test
    fun unsignedRequestHasNoSignerMethod() {
        assertNull(requestObjectSignerMethod(ClientIdScheme.OPENID_FEDERATION, false, chain, null))
    }
}
