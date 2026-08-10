/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance

import java.util.Properties

data class OidfSuiteLock(
    val upstreamRepository: String,
    val upstreamCommit: String,
    val patchSet: String,
    val gitImage: String,
    val mavenImage: String,
    val serverRuntimeImage: String,
    val upstreamNginxImage: String,
    val serverImage: String,
    val nginxImage: String,
    val mongoImage: String,
) {
    init {
        require(upstreamCommit.matches(Regex("[0-9a-f]{40}"))) { "OIDF suite commit must be a full Git SHA" }
        require(patchSet.isNotBlank()) { "OIDF suite patch set must be explicit" }
        require(gitImage.hasSha256Digest()) { "Git builder image must be digest-pinned: $gitImage" }
        require(mavenImage.hasSha256Digest()) { "Maven builder image must be digest-pinned: $mavenImage" }
        require(serverRuntimeImage.hasSha256Digest()) { "OIDF server runtime image must be digest-pinned: $serverRuntimeImage" }
        require(upstreamNginxImage.hasSha256Digest()) { "Upstream OIDF nginx image must be digest-pinned: $upstreamNginxImage" }
        require(serverImage.hasImmutableReference()) { "OIDF server image must have an explicit tag or digest: $serverImage" }
        require(nginxImage.hasImmutableReference()) { "OIDF nginx image must have an explicit tag or digest: $nginxImage" }
        require(mongoImage.hasSha256Digest()) { "Mongo image must be digest-pinned: $mongoImage" }
    }

    companion object {
        fun load(): OidfSuiteLock {
            val properties = Properties()
            val stream =
                OidfSuiteLock::class.java.classLoader.getResourceAsStream("suite.lock.properties")
                    ?: error("suite.lock.properties is missing from tests-oidf-conformance-oid4vc resources")
            stream.use(properties::load)
            require(properties.required("schemaVersion") == "1") { "Unsupported OIDF suite lock schema" }
            return OidfSuiteLock(
                upstreamRepository = properties.required("upstreamRepository"),
                upstreamCommit = properties.required("upstreamCommit"),
                patchSet = properties.required("patchSet"),
                gitImage = properties.required("gitImage"),
                mavenImage = properties.required("mavenImage"),
                serverRuntimeImage = properties.required("serverRuntimeImage"),
                upstreamNginxImage = properties.required("upstreamNginxImage"),
                serverImage = properties.required("serverImage"),
                nginxImage = properties.required("nginxImage"),
                mongoImage = properties.required("mongoImage"),
            )
        }
    }
}

private fun Properties.required(name: String): String =
    getProperty(name)?.takeIf(String::isNotBlank) ?: error("Missing '$name' in suite.lock.properties")

private fun String.hasImmutableReference(): Boolean {
    if (contains("@sha256:")) return true
    val lastSlash = lastIndexOf('/')
    val lastColon = lastIndexOf(':')
    return lastColon > lastSlash && substring(lastColon + 1) !in setOf("latest", "master", "main")
}

private fun String.hasSha256Digest(): Boolean = substringAfterLast("@", missingDelimiterValue = "").matches(Regex("sha256:[0-9a-f]{64}"))
