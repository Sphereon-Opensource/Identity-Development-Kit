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

package com.sphereon.did.methods.webvh.companion

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.did.methods.webvh.WebvhDidUrlBuilder
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference

/**
 * Builds the optional `did:web` companion DID document for a `did:webvh` DID,
 * per spec "Publishing a Parallel did:web DID". The companion is hosted as
 * `did.json` next to `did.jsonl` so resolvers that only understand `did:web`
 * can still see the latest state without parsing the verifiable log.
 *
 * Pure data transformation: no DI, no IO, no signing — the companion document
 * is just a re-keyed copy of the webvh state document with:
 * - `id` rewritten from `did:webvh:<scid>:<host>[:<path>]` to
 *   `did:web:<host>[:<path>]`,
 * - all verification method `id`s and `controller`s rewritten to point at the
 *   new `did:web` identifier,
 * - all verification-relationship references (authentication, assertionMethod,
 *   etc.) rewritten to the new identifier,
 * - `alsoKnownAs` set to `[<webvh DID>]` so resolvers / wallets that follow
 *   alsoKnownAs links know the two identifiers map to the same controller.
 *   (Spec doesn't mandate this binding for companions but it's standard
 *   practice and useful for cross-resolution.)
 */
object WebvhDidWebCompanion {
    /**
     * Build the companion `did:web` DID document. Returns `Err` if [webvhDid]
     * is not parseable as a `did:webvh` DID; otherwise [Ok] with the rewritten
     * document.
     */
    fun build(
        webvhDid: String,
        webvhDocument: DidDocument,
    ): IdkResult<DidDocument, IdkError> {
        val didWebDid =
            WebvhDidUrlBuilder.toDidWebDid(webvhDid).let {
                if (it.isErr) {
                    return Err(it.error)
                }
                it.value
            }
        val rewrittenVerificationMethods = webvhDocument.verificationMethod?.map { vm -> rewriteVerificationMethod(vm, webvhDid, didWebDid) }
        val priorAlsoKnownAs = webvhDocument.alsoKnownAs ?: emptyList()
        val mergedAlsoKnownAs =
            (priorAlsoKnownAs + webvhDid)
                .distinct()
                .filter { it != didWebDid }
        return Ok(
            DidDocument(
                context = webvhDocument.context,
                id = didWebDid,
                controller = webvhDocument.controller.map { rewriteIdentifier(it, webvhDid, didWebDid) },
                alsoKnownAs = mergedAlsoKnownAs.takeIf { it.isNotEmpty() },
                verificationMethod = rewrittenVerificationMethods,
                authentication = webvhDocument.authentication?.map { ref -> rewriteVerificationRef(ref, webvhDid, didWebDid) },
                assertionMethod = webvhDocument.assertionMethod?.map { ref -> rewriteVerificationRef(ref, webvhDid, didWebDid) },
                keyAgreement = webvhDocument.keyAgreement?.map { ref -> rewriteVerificationRef(ref, webvhDid, didWebDid) },
                capabilityInvocation = webvhDocument.capabilityInvocation?.map { ref -> rewriteVerificationRef(ref, webvhDid, didWebDid) },
                capabilityDelegation = webvhDocument.capabilityDelegation?.map { ref -> rewriteVerificationRef(ref, webvhDid, didWebDid) },
                service = webvhDocument.service?.map { svc -> svc.copy(id = rewriteIdentifier(svc.id, webvhDid, didWebDid)) },
            ),
        )
    }

    private fun rewriteVerificationMethod(
        vm: VerificationMethod,
        webvhDid: String,
        didWebDid: String,
    ): VerificationMethod =
        vm.copy(
            id = rewriteIdentifier(vm.id, webvhDid, didWebDid),
            controller = rewriteIdentifier(vm.controller, webvhDid, didWebDid),
        )

    private fun rewriteVerificationRef(
        ref: VerificationMethodOrReference,
        webvhDid: String,
        didWebDid: String,
    ): VerificationMethodOrReference {
        val refId = ref.reference
        if (refId != null) {
            return VerificationMethodOrReference.fromReference(rewriteIdentifier(refId, webvhDid, didWebDid))
        }
        val embeddedVm = ref.embedded
        if (embeddedVm != null) {
            return VerificationMethodOrReference.fromEmbedded(rewriteVerificationMethod(embeddedVm, webvhDid, didWebDid))
        }
        return ref
    }

    private fun rewriteIdentifier(
        id: String,
        webvhDid: String,
        didWebDid: String,
    ): String =
        if (id.startsWith(webvhDid)) {
            didWebDid + id.substring(webvhDid.length)
        } else {
            id
        }
}
