/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.boundary

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

class WalletSecureComponentBoundaryTest {
    @Test
    fun holderFacingWalletCodeDoesNotImportKms() {
        val root = idkRoot()
        val violations =
            sourceFiles(root)
                .flatMap { path ->
                    Files.readAllLines(path).mapIndexedNotNull { index, line ->
                        val trimmed = line.trim()
                        val isForbiddenImport = trimmed.startsWith("import ") && forbiddenKmsImports.any { trimmed.startsWith(it) }
                        val isForbiddenUsage = trimmed.contains("asKeyManagerServiceGraph")
                        if (isForbiddenImport || isForbiddenUsage) {
                            "${root.relativize(path)}:${index + 1}: $trimmed"
                        } else {
                            null
                        }
                    }
                }

        assertTrue(
            violations.isEmpty(),
            "Holder-facing wallet code must not import KMS APIs. Route wallet -> WSCA -> WSCD -> KMS instead:\n${violations.joinToString("\n")}",
        )
    }

    private fun sourceFiles(root: Path): List<Path> =
        listOf(
            root.resolve("lib/wallet/public/src/commonMain/kotlin"),
            root.resolve("lib/wallet/impl/src/commonMain/kotlin"),
            root.resolve("lib/openid/oid4vci/holder/impl/src/commonMain/kotlin"),
            root.resolve("lib/openid/oid4vp/holder/impl/src/commonMain/kotlin"),
            root.resolve("lib/wallet/wsca/public/src/commonMain/kotlin"),
            root.resolve("lib/wallet/wsca/impl/src/commonMain/kotlin"),
            root.resolve("lib/wallet/unit/public/src/commonMain/kotlin"),
            root.resolve("lib/wallet/provider/public/src/commonMain/kotlin"),
            root.resolve("lib/wallet/provider/local/src/commonMain/kotlin"),
        ).flatMap { kotlinFilesUnder(it) } +
            kotlinFilesUnder(root.resolve("lib/wallet/interaction")).filter {
                normalize(it).contains("/src/commonMain/kotlin/")
            }

    private fun kotlinFilesUnder(base: Path): List<Path> {
        if (!Files.isDirectory(base)) return emptyList()
        val files = mutableListOf<Path>()
        val stream = Files.walk(base)
        try {
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt")) {
                    files.add(path)
                }
            }
        } finally {
            stream.close()
        }
        return files
    }

    private fun idkRoot(): Path {
        val cwd = Paths.get("").toAbsolutePath().normalize()
        val candidates = listOf(cwd, cwd.resolve("vdx/edk/idk"), cwd.resolve("edk/idk"))
        for (candidate in candidates) {
            if (Files.isDirectory(candidate.resolve("lib/wallet")) && Files.isDirectory(candidate.resolve("lib/openid"))) {
                return candidate
            }
        }

        var current: Path? = cwd
        while (current != null) {
            if (Files.isDirectory(current.resolve("lib/wallet")) && Files.isDirectory(current.resolve("lib/openid"))) {
                return current
            }
            current = current.parent
        }
        error("Could not locate IDK root from $cwd")
    }

    private fun normalize(path: Path): String = path.toString().replace('\\', '/')

    private companion object {
        // Mirrors the forbidden patterns in the idk root build.gradle.kts verifyWalletKmsBoundary task:
        // the whole com.sphereon.crypto.core.kms package, the whole com.sphereon.crypto.kms.provider
        // package, plus the asKeyManagerServiceGraph usage check below.
        private val forbiddenKmsImports =
            listOf(
                "import com.sphereon.crypto.core.kms.",
                "import com.sphereon.crypto.kms.provider.",
            )
    }
}
