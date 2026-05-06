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

package com.sphereon.did.methods.webvh.resolver.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.WebvhDidUrlBuilder
import com.sphereon.did.methods.webvh.command.FetchWebvhLogInput
import com.sphereon.did.methods.webvh.command.FetchWebvhLogOutput
import com.sphereon.did.methods.webvh.command.FetchWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.log.WebvhLogReader
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.model.WebvhWitnessFile
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FetchWebvhLogServiceCommand>())
class FetchWebvhLogServiceCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<FetchWebvhLogInput, FetchWebvhLogOutput, IdkError>(
        commandId = FetchWebvhLogServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FetchWebvhLogInput>(),
        outputTypeToken = typeToken<FetchWebvhLogOutput>(),
    ),
    FetchWebvhLogServiceCommand {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    override val commandId: String get() = FetchWebvhLogServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is FetchWebvhLogInput

    override suspend fun doExecute(
        args: FetchWebvhLogInput,
        applyDuring: (FetchWebvhLogInput) -> FetchWebvhLogInput,
    ): IdkResult<FetchWebvhLogOutput, IdkError> {
        val input = applyDuring(args)
        val logUrl = WebvhDidUrlBuilder.toLogUrl(input.did).getOrElseErr { return Err(it) }

        val text = fetchText(logUrl).getOrElseErr { return Err(it) }
        val all = WebvhLogReader.read(text).getOrElseErr { return Err(it) }
        val filtered = filter(all, input)

        val witnessFile =
            if (input.includeWitnessFile) {
                val witnessUrl = WebvhDidUrlBuilder.toWitnessUrl(input.did).getOrElseErr { return Err(it) }
                fetchOptionalWitnessFile(witnessUrl)
            } else {
                null
            }

        return Ok(FetchWebvhLogOutput(entries = filtered, witnessFile = witnessFile))
    }

    private fun filter(
        entries: List<WebvhLogEntry>,
        input: FetchWebvhLogInput
    ): List<WebvhLogEntry> {
        val versionId = input.versionId
        if (versionId != null) {
            return entries.filter { it.versionId == versionId }
        }
        val versionNumber = input.versionNumber
        if (versionNumber != null) {
            val idx = versionNumber - 1
            return if (idx in entries.indices) {
                listOf(entries[idx])
            } else {
                emptyList()
            }
        }
        val versionTime = input.versionTime
        if (versionTime != null) {
            return entries.filter { it.versionTime <= versionTime }
        }
        return entries
    }

    private suspend fun fetchText(url: String): IdkResult<String, IdkError> {
        val client = httpClientFactory.createClient(HttpClientOptions())
        return try {
            val response: HttpResponse = client.get(url)
            if (!response.status.isSuccess()) {
                return Err(IdkError.NOT_FOUND_ERROR(message = "Failed to fetch $url: HTTP ${response.status.value}"))
            }
            Ok(response.bodyAsText())
        } catch (expected: Exception) {
            Err(IdkError.fromString("HTTP fetch failed for $url: ${expected.message}", code = "HTTP_REQUEST_FAILED", exception = expected))
        } finally {
            client.close()
        }
    }

    private suspend fun fetchOptionalWitnessFile(url: String): WebvhWitnessFile? {
        val client = httpClientFactory.createClient(HttpClientOptions())
        return try {
            val response: HttpResponse = client.get(url)
            if (!response.status.isSuccess()) {
                return null
            }
            val body = response.bodyAsText()
            if (body.isBlank()) {
                return null
            }
            json.decodeFromString(WebvhWitnessFile.serializer(), body)
        } catch (_: Exception) {
            null
        } finally {
            client.close()
        }
    }
}

private inline fun <V, E> IdkResult<V, E>.getOrElseErr(onErr: (E) -> Nothing): V =
    if (isOk) {
        value
    } else {
        onErr(error)
    }
