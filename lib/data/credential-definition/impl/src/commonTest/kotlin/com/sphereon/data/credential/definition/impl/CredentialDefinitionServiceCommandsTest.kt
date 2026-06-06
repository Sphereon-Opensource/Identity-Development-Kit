@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.credential.definition.impl

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.data.credential.definition.ClaimPath
import com.sphereon.data.credential.definition.ClaimValueKind
import com.sphereon.data.credential.definition.CredentialClaim
import com.sphereon.data.credential.definition.CredentialDefinitionLifecycleStatus
import com.sphereon.data.credential.definition.CredentialTypeBindingRef
import com.sphereon.data.credential.definition.command.AddCredentialDefinitionClaimArgs
import com.sphereon.data.credential.definition.command.CreateCredentialDefinitionArgs
import com.sphereon.data.credential.definition.command.DeleteCredentialDefinitionArgs
import com.sphereon.data.credential.definition.command.GetCredentialDefinitionArgs
import com.sphereon.data.credential.definition.command.ListCredentialDefinitionsArgs
import com.sphereon.data.credential.definition.command.RemoveCredentialDefinitionClaimArgs
import com.sphereon.data.credential.definition.command.SetCredentialDefinitionClaimsArgs
import com.sphereon.data.credential.definition.command.SetCredentialDefinitionLifecycleArgs
import com.sphereon.data.credential.definition.command.SnapshotCredentialDefinitionVersionArgs
import com.sphereon.data.credential.definition.command.UpdateCredentialDefinitionArgs
import com.sphereon.data.credential.definition.command.UpdateCredentialDefinitionClaimArgs
import com.sphereon.data.credential.definition.impl.InMemoryCredentialDefinitionStore
import com.sphereon.data.credential.definition.impl.test.createTestSessionExecution
import com.sphereon.data.credential.definition.impl.test.expectErr
import com.sphereon.data.credential.definition.impl.test.expectOk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CredentialDefinitionServiceCommandsTest {
    private class Fixture {
        val execution = createTestSessionExecution()
        val store = InMemoryCredentialDefinitionStore()
        val create = CreateCredentialDefinitionServiceCommandImpl(execution, store)
        val get = GetCredentialDefinitionServiceCommandImpl(execution, store)
        val list = ListCredentialDefinitionsServiceCommandImpl(execution, store)
        val update = UpdateCredentialDefinitionServiceCommandImpl(execution, store)
        val delete = DeleteCredentialDefinitionServiceCommandImpl(execution, store)
        val setClaims = SetCredentialDefinitionClaimsServiceCommandImpl(execution, store)
        val addClaim = AddCredentialDefinitionClaimServiceCommandImpl(execution, store)
        val updateClaim = UpdateCredentialDefinitionClaimServiceCommandImpl(execution, store)
        val removeClaim = RemoveCredentialDefinitionClaimServiceCommandImpl(execution, store)
        val snapshot = SnapshotCredentialDefinitionVersionServiceCommandImpl(execution, store)
        val setLifecycle = SetCredentialDefinitionLifecycleServiceCommandImpl(execution, store)
    }

    private fun fixture() = Fixture()

    private fun claim(
        path: String,
        valueKind: ClaimValueKind = ClaimValueKind.STRING,
        labels: Map<String, String> = mapOf("en" to "Label $path"),
    ) = CredentialClaim(path = ClaimPath(path), valueKind = valueKind, labels = labels)

    private suspend fun Fixture.createDefault() =
        create
            .execute(
                CreateCredentialDefinitionArgs(
                    name = "PID definition",
                    credentialTypeBindingRef = CredentialTypeBindingRef("binding-pid"),
                    description = "the canonical PID definition",
                    claims = listOf(claim("given_name")),
                ),
            ).expectOk()

    @Test
    fun create_get_list_roundTrip() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            assertEquals("PID definition", created.name)
            assertEquals(1L, created.version)
            assertEquals(CredentialDefinitionLifecycleStatus.DRAFT, created.lifecycleStatus)
            assertEquals(f.execution.tenantId, created.tenantId)

            val fetched = f.get.execute(GetCredentialDefinitionArgs(created.id)).expectOk()
            assertEquals(created, fetched)

            val listed = f.list.execute(ListCredentialDefinitionsArgs()).expectOk()
            assertEquals(listOf(created.id), listed.definitions.map { it.id })
        }

    @Test
    fun get_missing_returnsNotFound() =
        runTest {
            val f = fixture()
            val error = f.get.execute(GetCredentialDefinitionArgs(Uuid.random())).expectErr()
            assertEquals(ErrorCategory.NOT_FOUND, error.category)
        }

    @Test
    fun create_blankName_rejected() =
        runTest {
            val f = fixture()
            val error =
                f.create
                    .execute(
                        CreateCredentialDefinitionArgs(name = "  ", credentialTypeBindingRef = CredentialTypeBindingRef("b")),
                    ).expectErr()
            assertEquals(ErrorCategory.VALIDATION, error.category)
        }

    @Test
    fun update_changesNameDescription() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val updated =
                f.update
                    .execute(
                        UpdateCredentialDefinitionArgs(definitionId = created.id, name = "Renamed"),
                    ).expectOk()
            assertEquals("Renamed", updated.name)
            assertEquals("the canonical PID definition", updated.description)
        }

    @Test
    fun delete_removesAndReportsExistence() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            assertTrue(
                f.delete
                    .execute(DeleteCredentialDefinitionArgs(created.id))
                    .expectOk()
                    .deleted
            )
            assertEquals(
                ErrorCategory.NOT_FOUND,
                f.get
                    .execute(GetCredentialDefinitionArgs(created.id))
                    .expectErr()
                    .category,
            )
            assertEquals(
                false,
                f.delete
                    .execute(DeleteCredentialDefinitionArgs(created.id))
                    .expectOk()
                    .deleted
            )
        }

    @Test
    fun setClaims_replacesWholeList() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val replaced =
                f.setClaims
                    .execute(
                        SetCredentialDefinitionClaimsArgs(
                            definitionId = created.id,
                            claims = listOf(claim("family_name"), claim("birth_date", ClaimValueKind.DATE)),
                        ),
                    ).expectOk()
            assertEquals(setOf("family_name", "birth_date"), replaced.claims.map { it.path.value }.toSet())
        }

    @Test
    fun setClaims_duplicatePaths_rejected() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val error =
                f.setClaims
                    .execute(
                        SetCredentialDefinitionClaimsArgs(
                            definitionId = created.id,
                            claims = listOf(claim("given_name"), claim("given_name")),
                        ),
                    ).expectErr()
            assertEquals(ErrorCategory.VALIDATION, error.category)
        }

    @Test
    fun addClaim_appends_andRejectsDuplicate() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val withNew =
                f.addClaim
                    .execute(
                        AddCredentialDefinitionClaimArgs(definitionId = created.id, claim = claim("family_name")),
                    ).expectOk()
            assertEquals(setOf("given_name", "family_name"), withNew.claims.map { it.path.value }.toSet())

            val error =
                f.addClaim
                    .execute(
                        AddCredentialDefinitionClaimArgs(definitionId = created.id, claim = claim("given_name")),
                    ).expectErr()
            assertEquals(ErrorCategory.VALIDATION, error.category)
        }

    @Test
    fun updateClaim_replacesByPath() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val replaced =
                f.updateClaim
                    .execute(
                        UpdateCredentialDefinitionClaimArgs(
                            definitionId = created.id,
                            claimPath = "given_name",
                            claim = claim("given_name", ClaimValueKind.STRING, mapOf("en" to "First name")),
                        ),
                    ).expectOk()
            assertEquals("First name", replaced.claims.single { it.path.value == "given_name" }.labels["en"])
        }

    @Test
    fun updateClaim_pathMismatch_rejected() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val error =
                f.updateClaim
                    .execute(
                        UpdateCredentialDefinitionClaimArgs(
                            definitionId = created.id,
                            claimPath = "given_name",
                            claim = claim("family_name"),
                        ),
                    ).expectErr()
            assertEquals(ErrorCategory.VALIDATION, error.category)
        }

    @Test
    fun updateClaim_missingClaim_returnsNotFound() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val error =
                f.updateClaim
                    .execute(
                        UpdateCredentialDefinitionClaimArgs(
                            definitionId = created.id,
                            claimPath = "missing",
                            claim = claim("missing"),
                        ),
                    ).expectErr()
            assertEquals(ErrorCategory.NOT_FOUND, error.category)
        }

    @Test
    fun removeClaim_removesByPath() =
        runTest {
            val f = fixture()
            val created =
                f.create
                    .execute(
                        CreateCredentialDefinitionArgs(
                            name = "C",
                            credentialTypeBindingRef = CredentialTypeBindingRef("b"),
                            claims = listOf(claim("given_name"), claim("family_name")),
                        ),
                    ).expectOk()
            val removed =
                f.removeClaim
                    .execute(
                        RemoveCredentialDefinitionClaimArgs(definitionId = created.id, claimPath = "given_name"),
                    ).expectOk()
            assertEquals(listOf("family_name"), removed.claims.map { it.path.value })
        }

    @Test
    fun removeClaim_missing_returnsNotFound() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val error =
                f.removeClaim
                    .execute(
                        RemoveCredentialDefinitionClaimArgs(definitionId = created.id, claimPath = "missing"),
                    ).expectErr()
            assertEquals(ErrorCategory.NOT_FOUND, error.category)
        }

    @Test
    fun snapshotVersion_bumpsVersion() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val bumped = f.snapshot.execute(SnapshotCredentialDefinitionVersionArgs(created.id)).expectOk()
            assertEquals(created.version + 1, bumped.version)
        }

    @Test
    fun setLifecycle_togglesDraftPublished() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val published =
                f.setLifecycle
                    .execute(
                        SetCredentialDefinitionLifecycleArgs(created.id, CredentialDefinitionLifecycleStatus.PUBLISHED),
                    ).expectOk()
            assertEquals(CredentialDefinitionLifecycleStatus.PUBLISHED, published.lifecycleStatus)

            val backToDraft =
                f.setLifecycle
                    .execute(
                        SetCredentialDefinitionLifecycleArgs(created.id, CredentialDefinitionLifecycleStatus.DRAFT),
                    ).expectOk()
            assertEquals(CredentialDefinitionLifecycleStatus.DRAFT, backToDraft.lifecycleStatus)
        }

    @Test
    fun setLifecycle_toSameStatus_isNoOp() =
        runTest {
            val f = fixture()
            val created = f.createDefault()
            val unchanged =
                f.setLifecycle
                    .execute(
                        SetCredentialDefinitionLifecycleArgs(created.id, created.lifecycleStatus),
                    ).expectOk()
            assertEquals(created, unchanged)
            assertEquals(created.updatedAt, unchanged.updatedAt)
        }
}
