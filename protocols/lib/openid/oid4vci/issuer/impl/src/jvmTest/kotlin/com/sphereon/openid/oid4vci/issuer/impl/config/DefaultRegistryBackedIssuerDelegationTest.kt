package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.core.api.Ok
import com.sphereon.openid.oid4vci.issuer.config.CredentialSigningConfig
import com.sphereon.openid.oid4vci.issuer.config.RegistryBackedIssuerConfiguration
import com.sphereon.statuslist.StatusListBinding
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DefaultRegistryBackedIssuerDelegationTest {
    @Test
    fun defaultSelectionDelegatesIdentityAndMetadataToTheSameRegistry() =
        runBlocking<Unit> {
            val calls = mutableListOf<String>()
            val vcts = listOf("https://issuer.example/type")
            val canonical =
                delegate { name, _ ->
                    calls += name
                    when (name) {
                        "getIssuerIdentifier" -> "https://issuer.example"
                        "listVcts" -> vcts
                        else -> error("Unexpected registry call: $name")
                    }
                }
            val selected = DefaultRegistryBackedOid4vciIssuerConfigProvider(canonical)
            assertEquals("https://issuer.example", selected.issuerIdentifier)
            assertSame(vcts, selected.listVcts())
            assertEquals(listOf("getIssuerIdentifier", "listVcts"), calls)
        }

    @Test
    fun defaultSelectionForwardsLifecycleAndIssuanceSurfacesWithoutFallback() =
        runBlocking<Unit> {
            val calls = mutableListOf<String>()
            val signingConfigs = emptyMap<String, CredentialSigningConfig>()
            val statusBinding = Ok<StatusListBinding?>(null)
            val canonical =
                delegate { name, args ->
                    calls += name
                    when (name) {
                        "prepare", "reloadConfiguration" -> {
                            Unit
                        }

                        "credentialSigningConfigs" -> {
                            signingConfigs
                        }

                        "statusListBindingForIssuance" -> {
                            assertEquals("credential-a", args?.first())
                            statusBinding
                        }

                        else -> {
                            error("Unexpected registry call: $name")
                        }
                    }
                }
            val selected = DefaultRegistryBackedOid4vciIssuerConfigProvider(canonical)
            selected.prepare()
            selected.reloadConfiguration()
            assertSame(signingConfigs, selected.credentialSigningConfigs())
            assertSame(statusBinding, selected.statusListBindingForIssuance("credential-a"))
            assertEquals(listOf("prepare", "reloadConfiguration", "credentialSigningConfigs", "statusListBindingForIssuance"), calls)
        }

    private fun delegate(invoke: (String, Array<out Any?>?) -> Any?): RegistryBackedIssuerConfiguration =
        Proxy.newProxyInstance(
            RegistryBackedIssuerConfiguration::class.java.classLoader,
            arrayOf(RegistryBackedIssuerConfiguration::class.java),
        ) { _, method, args -> invoke(method.name, args) } as RegistryBackedIssuerConfiguration
}
