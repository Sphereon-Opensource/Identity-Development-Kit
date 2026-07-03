/*
 * Copyright 2026 Sphereon International B.V.
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
 */

package com.sphereon.wallet.credential

fun walletInstanceRootPrefix(): String = "wallet-instances/"

fun walletInstancePath(walletInstanceId: String): String =
    walletInstancePrefix(walletInstanceId) + "/instance"

fun walletStorageProfilePath(walletInstanceId: String): String =
    walletInstancePrefix(walletInstanceId) + "/storage-profile"

fun walletCredentialRecordEnvelopePath(
    walletInstanceId: String,
    credentialRecordId: String,
): String = walletCredentialPrefix(walletInstanceId, credentialRecordId) + "/record"

fun walletCredentialInstanceBodyPath(
    walletInstanceId: String,
    credentialRecordId: String,
    credentialInstanceId: String,
): String =
    walletCredentialPrefix(walletInstanceId, credentialRecordId) +
        "/instances/" +
        walletPathSegment(credentialInstanceId, "credentialInstanceId") +
        "/body"

fun walletCredentialMetadataPath(
    walletInstanceId: String,
    credentialRecordId: String,
): String = walletCredentialPrefix(walletInstanceId, credentialRecordId) + "/metadata"

fun walletCredentialMetadataPrefix(walletInstanceId: String): String =
    walletInstancePrefix(walletInstanceId) + "/credentials/"

fun walletIssuancePrefix(walletInstanceId: String): String =
    walletInstancePrefix(walletInstanceId) + "/issuance/"

fun walletIssuanceSessionPath(
    walletInstanceId: String,
    issuanceSessionId: String,
): String = walletIssuancePrefix(walletInstanceId) + walletPathSegment(issuanceSessionId, "issuanceSessionId")

fun walletDeferredAccessTokenPath(
    walletInstanceId: String,
    issuanceSessionId: String,
): String = walletIssuanceSessionPath(walletInstanceId, issuanceSessionId) + "/access-token"

private fun walletCredentialPrefix(
    walletInstanceId: String,
    credentialRecordId: String,
): String =
    walletCredentialMetadataPrefix(walletInstanceId) + walletPathSegment(credentialRecordId, "credentialRecordId")

private fun walletInstancePrefix(walletInstanceId: String): String =
    walletInstanceRootPrefix() + walletPathSegment(walletInstanceId, "walletInstanceId")

fun walletPathSegment(
    value: String,
    name: String,
): String {
    val segment = value.trim()
    require(segment.isNotBlank()) { "$name must not be blank" }
    require(!segment.contains('\u0000')) { "$name must not contain null bytes" }
    require(!segment.contains('/')) { "$name must not contain '/'" }
    require(!segment.contains('\\')) { "$name must not contain '\\'" }
    require(segment != "." && segment != "..") { "$name must not be a path traversal segment" }
    return segment
}
