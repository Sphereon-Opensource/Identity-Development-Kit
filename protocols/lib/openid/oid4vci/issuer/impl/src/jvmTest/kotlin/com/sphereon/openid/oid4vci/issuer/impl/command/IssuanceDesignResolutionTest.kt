@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.credential.design.CredentialDesignService
import com.sphereon.data.store.credential.design.IssuanceDesignVersion
import com.sphereon.data.store.credential.design.IssuanceDesignVersionSelector
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.DesignHostingMode
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class IssuanceDesignResolutionTest {
    private val tenant = "tenant-a"
    private val config = "Pid"
    private val designId = Uuid.random()
    private val record = CredentialDesignRecord(
        id = designId,
        tenantId = tenant,
        hostingMode = DesignHostingMode.LOCAL,
        bindings = listOf(DesignBinding(credentialConfigurationId = config)),
        displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "PID")),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    /** A design store that records how it was asked to resolve; every other operation is unused here. */
    private class RecordingDesigns(private val visible: List<CredentialDesignRecord>) {
        val resolved = mutableListOf<ResolveCredentialDesignInput>()
        var found = 0

        val service: CredentialDesignService =
            Proxy.newProxyInstance(CredentialDesignService::class.java.classLoader, arrayOf(CredentialDesignService::class.java)) { _, method, args ->
                when (method.name) {
                    "findCredentialDesignByBindingKey" -> {
                        found++
                        assertEquals(DesignBindingKey.CREDENTIAL_CONFIGURATION_ID, args[1])
                        Ok(visible)
                    }
                    "resolveCredentialDesign" -> {
                        val input = args[1] as ResolveCredentialDesignInput
                        resolved += input
                        Ok(ResolvedCredentialDesign(design = visible.firstOrNull() ?: error("nothing to resolve"), renderVariants = emptyList(), appliedLayers = emptyList(), lockedFields = emptyMap(), resolvedAt = Clock.System.now()))
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as CredentialDesignService
    }

    private fun selector(result: IdkResult<IssuanceDesignVersion?, IdkError>) = IssuanceDesignVersionSelector { _, _ -> result }

    @Test
    fun theSelectedPublishedVersionIsResolvedWhoeverCalls() = runTest {
        val designs = RecordingDesigns(listOf(record))

        val resolved = resolveIssuanceDesign(designs.service, selector(Ok(IssuanceDesignVersion(designId, 3))), tenant, config)

        assertTrue(resolved.isOk)
        assertEquals(listOf(ResolveCredentialDesignInput(designId = designId, designVersion = 3)), designs.resolved)
        // The selector, not the caller's view of the store, decides which design and version is used.
        assertEquals(0, designs.found)
    }

    @Test
    fun aConfigurationWhoseDesignHasNoPublishedVersionIsRefused() = runTest {
        val designs = RecordingDesigns(listOf(record))

        val resolved = resolveIssuanceDesign(designs.service, selector(Err(IdkError.INVALID_STATE(message = "no published version"))), tenant, config)

        assertTrue(resolved.isErr)
        assertEquals(INVALID_CREDENTIAL_CONFIGURATION, resolved.error.code)
        assertTrue(resolved.error.message.defaultMessage.contains("'$config'"))
        assertTrue(designs.resolved.isEmpty())
    }

    @Test
    fun aConfigurationWithoutADesignIssuesWithoutOne() = runTest {
        val designs = RecordingDesigns(emptyList())

        assertNull(resolveIssuanceDesign(designs.service, selector(Ok(null)), tenant, config).value)
        assertNull(resolveIssuanceDesign(designs.service, null, tenant, config).value)
    }

    @Test
    fun anUnversionedStoreResolvesTheBoundDesignAsStored() = runTest {
        val designs = RecordingDesigns(listOf(record))

        val resolved = resolveIssuanceDesign(designs.service, null, tenant, config)

        assertEquals(designId, resolved.value?.design?.id)
        assertEquals(listOf(ResolveCredentialDesignInput(designId = designId)), designs.resolved)
    }
}
