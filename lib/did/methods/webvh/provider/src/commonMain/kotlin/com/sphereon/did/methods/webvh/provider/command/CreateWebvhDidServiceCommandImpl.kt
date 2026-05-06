/*
 * © 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.did.methods.webvh.provider.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.WebvhDidCapabilities
import com.sphereon.did.methods.webvh.WebvhDidUrlBuilder
import com.sphereon.did.methods.webvh.command.CreateWebvhDidInput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidOutput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.log.WebvhLogWriter
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.model.WebvhParameters
import com.sphereon.did.methods.webvh.scid.WebvhPreRotationHasher
import com.sphereon.did.methods.webvh.scid.WebvhScidComputer
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.models.VerificationMethodType
import com.sphereon.did.models.VerificationPurpose
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Mints a new `did:webvh` DID per spec §3 — generates the SCID, builds the
 * genesis DID document and log entry, signs it with eddsa-jcs-2022, and
 * returns the final DID + log JSONL ready to publish.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateWebvhDidServiceCommand>())
class CreateWebvhDidServiceCommandImpl(
    execution: SessionExecution,
    private val signer: WebvhEntrySigner,
    private val didWebCompanionService: com.sphereon.did.methods.webvh.provider.companion.WebvhDidWebCompanionService,
) : TypedServiceCommandAdapter<CreateWebvhDidInput, CreateWebvhDidOutput, IdkError>(
        commandId = CreateWebvhDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateWebvhDidInput>(),
        outputTypeToken = typeToken<CreateWebvhDidOutput>(),
    ),
    CreateWebvhDidServiceCommand {
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        }

    override val commandId: String get() = CreateWebvhDidServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateWebvhDidInput

    override suspend fun doExecute(
        args: CreateWebvhDidInput,
        applyDuring: (CreateWebvhDidInput) -> CreateWebvhDidInput,
    ): IdkResult<CreateWebvhDidOutput, IdkError> {
        val input = applyDuring(args)
        validate(input).getOrElseErr { return Err(it) }

        val placeholderEntry = buildPlaceholderEntry(input)
        val placeholderJson = json.encodeToJsonElement(WebvhLogEntry.serializer(), placeholderEntry).jsonObject
        val placeholderCleaned = JsonObject(placeholderJson - PROOF_FIELD)
        val scid = WebvhScidComputer.computeScid(placeholderCleaned)
        val substitutedEntry = substituteScid(placeholderCleaned, scid)

        val finalDid = WebvhDidUrlBuilder.toDid(scid, input.domain, input.port, input.path)
        val verificationMethodId = "$finalDid#key-1"
        val entryWithSCIDPredecessor = substitutedEntry.copy(versionId = scid, proof = emptyList())
        val signedEntry =
            signer
                .signEntry(
                    entryWithoutProof = entryWithSCIDPredecessor,
                    versionNumber = 1,
                    signingKeyRef = input.updateKeyRefs.first(),
                    verificationMethod = verificationMethodId,
                    createdAt = placeholderEntry.versionTime,
                ).getOrElseErr { return Err(it) }

        val logJsonl = WebvhLogWriter.write(listOf(signedEntry))
        val companion = didWebCompanionService.buildIfEnabled(finalDid, signedEntry.state)
        return Ok(
            CreateWebvhDidOutput(
                did = finalDid,
                didDocument = signedEntry.state,
                logEntry = signedEntry,
                logJsonl = logJsonl,
                didWebDocument = companion?.didWebDocument,
                didWebJson = companion?.didWebJson,
            ),
        )
    }

    /**
     * Build the genesis entry skeleton with `{SCID}` placeholders everywhere
     * the SCID will appear after substitution.
     */
    private fun buildPlaceholderEntry(input: CreateWebvhDidInput): WebvhLogEntry {
        val placeholderDid =
            WebvhDidUrlBuilder.toDid(
                scid = WebvhScidComputer.PLACEHOLDER,
                host = input.domain,
                port = input.port,
                pathSegments = input.path,
            )
        val placeholderDoc =
            buildDidDocument(
                did = placeholderDid,
                updateMultikeys = input.updateMultikeys,
                purposes = input.verificationPurposes,
                services = input.services,
            )
        val nextKeyHashes =
            input.nextKeyMultikeys
                .takeIf { it.isNotEmpty() }
                ?.map { WebvhPreRotationHasher.hashMultikey(it) }
        val placeholderParams =
            WebvhParameters(
                method = WebvhDidCapabilities.SPEC_VERSION,
                scid = WebvhScidComputer.PLACEHOLDER,
                updateKeys = input.updateMultikeys,
                nextKeyHashes = nextKeyHashes,
                witness = input.witness,
                watchers = input.watchers.takeIf { it.isNotEmpty() },
                portable =
                    if (input.portable) {
                        true
                    } else {
                        null
                    },
                ttl = input.ttlSeconds,
            )
        return WebvhLogEntry(
            versionId = WebvhScidComputer.PLACEHOLDER,
            versionTime = Clock.System.now().toString(),
            parameters = placeholderParams,
            state = placeholderDoc,
            proof = emptyList(),
        )
    }

    /** Replace `{SCID}` with [scid] throughout the placeholder JSON. */
    private fun substituteScid(
        placeholderCleaned: JsonObject,
        scid: String
    ): WebvhLogEntry {
        val substitutedText = placeholderCleaned.toString().replace(WebvhScidComputer.PLACEHOLDER, scid)
        val substitutedJson = json.parseToJsonElement(substitutedText).jsonObject
        return json.decodeFromJsonElement(WebvhLogEntry.serializer(), substitutedJson)
    }

    private fun validate(input: CreateWebvhDidInput): IdkResult<Unit, IdkError> {
        if (input.domain.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "domain is required"))
        }
        if (input.updateKeyRefs.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "updateKeyRefs is required"))
        }
        if (input.updateMultikeys.size != input.updateKeyRefs.size) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "updateMultikeys MUST have the same size as updateKeyRefs"))
        }
        return Ok(Unit)
    }

    private fun buildDidDocument(
        did: String,
        updateMultikeys: List<String>,
        purposes: List<VerificationPurpose>,
        services: List<com.sphereon.did.models.DidService>,
    ): DidDocument {
        val verificationMethods = mutableListOf<VerificationMethod>()
        val authRefs = mutableListOf<VerificationMethodOrReference>()
        val assertRefs = mutableListOf<VerificationMethodOrReference>()
        val keyAgrRefs = mutableListOf<VerificationMethodOrReference>()
        val capInvRefs = mutableListOf<VerificationMethodOrReference>()
        val capDelRefs = mutableListOf<VerificationMethodOrReference>()

        for ((index, multikey) in updateMultikeys.withIndex()) {
            val vmId = "$did#key-${index + 1}"
            verificationMethods.add(
                VerificationMethod(
                    id = vmId,
                    type = VerificationMethodType.MULTIKEY.value,
                    controller = did,
                    publicKeyMultibase = multikey,
                ),
            )
            val ref = VerificationMethodOrReference.fromReference(vmId)
            for (purpose in purposes) {
                when (purpose) {
                    VerificationPurpose.AUTHENTICATION -> authRefs.add(ref)
                    VerificationPurpose.ASSERTION_METHOD -> assertRefs.add(ref)
                    VerificationPurpose.KEY_AGREEMENT -> keyAgrRefs.add(ref)
                    VerificationPurpose.CAPABILITY_INVOCATION -> capInvRefs.add(ref)
                    VerificationPurpose.CAPABILITY_DELEGATION -> capDelRefs.add(ref)
                }
            }
        }

        return DidDocument(
            id = did,
            verificationMethod = verificationMethods.takeIf { it.isNotEmpty() },
            authentication = authRefs.takeIf { it.isNotEmpty() },
            assertionMethod = assertRefs.takeIf { it.isNotEmpty() },
            keyAgreement = keyAgrRefs.takeIf { it.isNotEmpty() },
            capabilityInvocation = capInvRefs.takeIf { it.isNotEmpty() },
            capabilityDelegation = capDelRefs.takeIf { it.isNotEmpty() },
            service = services.takeIf { it.isNotEmpty() },
        )
    }

    companion object {
        private const val PROOF_FIELD = "proof"
    }
}

private inline fun <V, E> IdkResult<V, E>.getOrElseErr(onErr: (E) -> Nothing): V =
    if (isOk) {
        value
    } else {
        onErr(error)
    }
