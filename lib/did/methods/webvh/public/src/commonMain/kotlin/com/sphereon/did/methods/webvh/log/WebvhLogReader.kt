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

package com.sphereon.did.methods.webvh.log

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import kotlinx.serialization.json.Json

/**
 * Parses a `did:webvh` v1.0 `did.jsonl` file: one minified JSON entry per
 * line, terminator `\n`. Empty lines are skipped (lenient).
 */
object WebvhLogReader {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = false
        }

    fun read(jsonl: String): IdkResult<List<WebvhLogEntry>, IdkError> {
        val entries = mutableListOf<WebvhLogEntry>()
        for ((index, rawLine) in jsonl.lineSequence().withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                continue
            }
            val entry =
                try {
                    json.decodeFromString(WebvhLogEntry.serializer(), line)
                } catch (expected: Exception) {
                    return Err(
                        IdkError.fromString(
                            message = "WebvhLogReader: failed to parse log entry on line ${index + 1}: ${expected.message}",
                            code = "PARSING_ERROR",
                            exception = expected,
                        ),
                    )
                }
            entries.add(entry)
        }
        return Ok(entries)
    }
}
