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

package com.sphereon.did.manager.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.AddKeyOptions
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidDeactivateOptions
import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.DidKeyMapping
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.manager.DidRole
import com.sphereon.did.manager.DidUpdateOptions
import com.sphereon.did.manager.ManagedDid
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethodConfig
import com.sphereon.did.persistence.DidKeyMappingRecord
import com.sphereon.did.persistence.DidRecord
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.toDidKeyMappingRecord
import com.sphereon.did.persistence.toDidRecord
import com.sphereon.did.persistence.toDidRecordFilter
import com.sphereon.did.persistence.toManagedDid
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.utils.ParsedDid
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Implementation of the DID manager service.
 *
 * Uses the injected [DidRepository] for persistence. The actual storage
 * backend (memory, SQLite, PostgreSQL, etc.) is determined by the
 * repository implementation provided via dependency injection.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DidManager>())
class DidManagerServiceImpl(
    private val providerRegistry: DidProviderRegistry,
    private val resolverRegistry: DidResolverRegistry,
    private val repository: DidRepository,
) : DidManager {
    private var nextId = 1
    private val json = Json { encodeDefaults = false }

    override fun getCapabilities(method: String): DidMethodCapabilities? = providerRegistry.getCapabilities(method)

    override fun getSupportedMethods(): List<String> = providerRegistry.getSupportedMethods()

    override suspend fun create(options: DidCreateOptions): IdkResult<ManagedDid, IdkError> {
        val method = options.method
        val provider =
            providerRegistry.getProvider(method)
                ?: return Err(
                    IdkError.fromString(
                        message = "No provider registered for DID method: $method",
                        code = "UNSUPPORTED_OPERATION",
                    ),
                )

        // Create the DID using the provider
        val createResult =
            provider.create(options).getOrElse {
                return Err(it)
            }

        val now = currentTimestamp()
        val recordId = generateId("did")

        // Create key mappings from the configuration
        val keyMappings =
            options.verificationMethods.map { config ->
                DidKeyMapping(
                    id = generateId("km"),
                    verificationMethodId = config.verificationMethodId,
                    kmsKeyAlias = config.kmsKeyAlias,
                    kmsProviderId = config.kmsProviderId,
                    purposesJson = json.encodeToString(config.purposes.map { it.name }),
                )
            }

        // Create the managed DID record
        val managedDid =
            ManagedDid(
                id = recordId,
                did = createResult.did,
                method = method,
                alias = options.alias,
                document = createResult.didDocument,
                role = DidRole.MANAGED,
                deactivated = false,
                keys = keyMappings,
                createdAt = now,
                updatedAt = now,
            )

        // Persist the record
        repository.save(managedDid.toDidRecord()).getOrElse {
            return Err(it)
        }

        // Persist key mappings
        for (mapping in keyMappings) {
            repository.saveKeyMapping(recordId, mapping.toDidKeyMappingRecord(recordId)).getOrElse {
                // Best effort - record was saved
            }
        }

        return Ok(managedDid)
    }

    override suspend fun get(did: String): IdkResult<ManagedDid, IdkError> {
        val record =
            repository.findByDid(did).getOrElse {
                return Err(it)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))

        val keyMappings =
            repository.getKeyMappings(record.id).getOrElse {
                return Err(it)
            }

        return Ok(record.toManagedDid(keyMappings))
    }

    override suspend fun getByAlias(alias: String): IdkResult<ManagedDid, IdkError> {
        val record =
            repository.findByAlias(alias).getOrElse {
                return Err(it)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID with alias '$alias' not found"))

        val keyMappings =
            repository.getKeyMappings(record.id).getOrElse {
                return Err(it)
            }

        return Ok(record.toManagedDid(keyMappings))
    }

    override suspend fun list(filter: DidFilter?): IdkResult<List<ManagedDid>, IdkError> {
        val persistenceFilter = filter?.toDidRecordFilter()
        val records =
            repository.findAll(persistenceFilter).getOrElse {
                return Err(it)
            }

        val result =
            records.map { record ->
                val keyMappings =
                    repository.getKeyMappings(record.id).getOrElse {
                        emptyList()
                    }
                record.toManagedDid(keyMappings)
            }

        return Ok(result)
    }

    override suspend fun update(
        did: String,
        options: DidUpdateOptions,
    ): IdkResult<ManagedDid, IdkError> {
        val record =
            repository.findByDid(did).getOrElse {
                return Err(it)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))

        val keyMappings =
            repository.getKeyMappings(record.id).getOrElse {
                return Err(it)
            }
        val managedDid = record.toManagedDid(keyMappings)

        val parsed =
            ParsedDid.parse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        val provider =
            providerRegistry.getProvider(parsed.method)
                ?: return Err(
                    IdkError.fromString(
                        message = "No provider registered for DID method: ${parsed.method}",
                        code = "UNSUPPORTED_OPERATION",
                    ),
                )

        // Check if update is supported
        if (!provider.capabilities.lifecycle.update) {
            return Err(
                IdkError.fromString(
                    message = "DID method ${parsed.method} does not support updates",
                    code = "UNSUPPORTED_OPERATION",
                ),
            )
        }

        // Update using the provider
        val updateResult =
            provider.update(did, options).getOrElse {
                return Err(it)
            }

        // Update the managed DID record
        val updated =
            managedDid.copy(
                document = updateResult.didDocument,
                updatedAt = currentTimestamp(),
            )

        repository.update(updated.toDidRecord()).getOrElse {
            return Err(it)
        }

        return Ok(updated)
    }

    override suspend fun deactivate(
        did: String,
        options: DidDeactivateOptions,
    ): IdkResult<Unit, IdkError> {
        val record =
            repository.findByDid(did).getOrElse {
                return Err(it)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))

        val keyMappings =
            repository.getKeyMappings(record.id).getOrElse {
                return Err(it)
            }
        val managedDid = record.toManagedDid(keyMappings)

        val parsed =
            ParsedDid.parse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        val provider = providerRegistry.getProvider(parsed.method)

        // If provider exists and supports deactivation, try to deactivate
        if (provider != null && provider.capabilities.lifecycle.deactivate) {
            provider.deactivate(did, options).getOrElse {
                return Err(it)
            }
        }

        // Mark as deactivated locally
        val updated =
            managedDid.copy(
                deactivated = true,
                updatedAt = currentTimestamp(),
            )

        repository.update(updated.toDidRecord()).getOrElse {
            return Err(it)
        }

        return Ok(Unit)
    }

    override suspend fun delete(did: String): IdkResult<Unit, IdkError> {
        val record =
            repository.findByDid(did).getOrElse {
                return Err(it)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))

        // Delete key mappings first
        repository.deleteKeyMappingsForDid(record.id).getOrElse {
            // Best effort
        }

        // Delete the record
        repository.delete(did).getOrElse {
            return Err(it)
        }

        return Ok(Unit)
    }

    override suspend fun addVerificationMethod(
        did: String,
        config: VerificationMethodConfig,
    ): IdkResult<ManagedDid, IdkError> {
        val record =
            repository.findByDid(did).getOrElse {
                return Err(it)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))

        val keyMappings =
            repository.getKeyMappings(record.id).getOrElse {
                return Err(it)
            }
        val managedDid = record.toManagedDid(keyMappings)

        val parsed =
            ParsedDid.parse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        val provider =
            providerRegistry.getProvider(parsed.method)
                ?: return Err(
                    IdkError.fromString(
                        message = "No provider registered for DID method: ${parsed.method}",
                        code = "UNSUPPORTED_OPERATION",
                    ),
                )

        // Check if key addition is supported
        if (!provider.capabilities.keyManagement.addition) {
            return Err(
                IdkError.fromString(
                    message = "DID method ${parsed.method} does not support adding keys",
                    code = "UNSUPPORTED_OPERATION",
                ),
            )
        }

        // Add the key using the provider
        val addKeyOptions =
            AddKeyOptions(
                currentDocument = managedDid.document,
                config = config,
            )

        val updateResult =
            provider.addKey(did, addKeyOptions).getOrElse {
                return Err(it)
            }

        // Add the key mapping
        val newMapping =
            DidKeyMapping(
                id = generateId("km"),
                verificationMethodId = config.verificationMethodId,
                kmsKeyAlias = config.kmsKeyAlias,
                kmsProviderId = config.kmsProviderId,
                purposesJson = json.encodeToString(config.purposes.map { it.name }),
            )

        val updated =
            managedDid.copy(
                document = updateResult.didDocument,
                keys = managedDid.keys + newMapping,
                updatedAt = currentTimestamp(),
            )

        repository.update(updated.toDidRecord()).getOrElse {
            return Err(it)
        }

        repository.saveKeyMapping(record.id, newMapping.toDidKeyMappingRecord(record.id)).getOrElse {
            // Best effort
        }

        return Ok(updated)
    }

    override suspend fun removeVerificationMethod(
        did: String,
        verificationMethodId: String,
    ): IdkResult<ManagedDid, IdkError> {
        val record =
            repository.findByDid(did).getOrElse {
                return Err(it)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))

        val keyMappings =
            repository.getKeyMappings(record.id).getOrElse {
                return Err(it)
            }
        val managedDid = record.toManagedDid(keyMappings)

        val parsed =
            ParsedDid.parse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        val provider =
            providerRegistry.getProvider(parsed.method)
                ?: return Err(
                    IdkError.fromString(
                        message = "No provider registered for DID method: ${parsed.method}",
                        code = "UNSUPPORTED_OPERATION",
                    ),
                )

        // Check if key removal is supported
        if (!provider.capabilities.keyManagement.removal) {
            return Err(
                IdkError.fromString(
                    message = "DID method ${parsed.method} does not support removing keys",
                    code = "UNSUPPORTED_OPERATION",
                ),
            )
        }

        // Remove the key using the provider
        val updateResult =
            provider.removeKey(did, verificationMethodId).getOrElse {
                return Err(it)
            }

        // Find and remove the key mapping
        val mappingToRemove = keyMappings.find { it.verificationMethodId == verificationMethodId }
        mappingToRemove?.let {
            repository.deleteKeyMapping(it.id).getOrElse {
                // Best effort
            }
        }

        // Remove the key mapping from domain model
        val updated =
            managedDid.copy(
                document = updateResult.didDocument,
                keys = managedDid.keys.filter { it.verificationMethodId != verificationMethodId },
                updatedAt = currentTimestamp(),
            )

        repository.update(updated.toDidRecord()).getOrElse {
            return Err(it)
        }

        return Ok(updated)
    }

    override suspend fun addService(
        did: String,
        service: DidService,
    ): IdkResult<ManagedDid, IdkError> {
        val record =
            repository.findByDid(did).getOrElse {
                return Err(it)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))

        val keyMappings =
            repository.getKeyMappings(record.id).getOrElse {
                return Err(it)
            }
        val managedDid = record.toManagedDid(keyMappings)

        val parsed =
            ParsedDid.parse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        val provider =
            providerRegistry.getProvider(parsed.method)
                ?: return Err(
                    IdkError.fromString(
                        message = "No provider registered for DID method: ${parsed.method}",
                        code = "UNSUPPORTED_OPERATION",
                    ),
                )

        // Check if service addition is supported
        if (!provider.capabilities.serviceManagement.addition) {
            return Err(
                IdkError.fromString(
                    message = "DID method ${parsed.method} does not support adding services",
                    code = "UNSUPPORTED_OPERATION",
                ),
            )
        }

        // Add the service using the provider
        val updateResult =
            provider.addService(did, service).getOrElse {
                return Err(it)
            }

        val updated =
            managedDid.copy(
                document = updateResult.didDocument,
                updatedAt = currentTimestamp(),
            )

        repository.update(updated.toDidRecord()).getOrElse {
            return Err(it)
        }

        return Ok(updated)
    }

    override suspend fun removeService(
        did: String,
        serviceId: String,
    ): IdkResult<ManagedDid, IdkError> {
        val record =
            repository.findByDid(did).getOrElse {
                return Err(it)
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))

        val keyMappings =
            repository.getKeyMappings(record.id).getOrElse {
                return Err(it)
            }
        val managedDid = record.toManagedDid(keyMappings)

        val parsed =
            ParsedDid.parse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        val provider =
            providerRegistry.getProvider(parsed.method)
                ?: return Err(
                    IdkError.fromString(
                        message = "No provider registered for DID method: ${parsed.method}",
                        code = "UNSUPPORTED_OPERATION",
                    ),
                )

        // Check if service removal is supported
        if (!provider.capabilities.serviceManagement.removal) {
            return Err(
                IdkError.fromString(
                    message = "DID method ${parsed.method} does not support removing services",
                    code = "UNSUPPORTED_OPERATION",
                ),
            )
        }

        // Remove the service using the provider
        val updateResult =
            provider.removeService(did, serviceId).getOrElse {
                return Err(it)
            }

        val updated =
            managedDid.copy(
                document = updateResult.didDocument,
                updatedAt = currentTimestamp(),
            )

        repository.update(updated.toDidRecord()).getOrElse {
            return Err(it)
        }

        return Ok(updated)
    }

    override suspend fun import(
        did: String,
        alias: String?,
    ): IdkResult<ManagedDid, IdkError> {
        // Check if already managed
        val existing =
            repository.findByDid(did).getOrElse {
                return Err(it)
            }
        if (existing != null) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "DID is already managed: $did",
                ),
            )
        }

        // Parse to get method
        val parsed =
            ParsedDid.parse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        // Try to resolve the DID to verify it exists
        val resolutionResult =
            resolverRegistry.resolve(did, DidResolutionOptions()).getOrElse {
                return Err(it)
            }

        val now = currentTimestamp()
        val recordId = generateId("did")

        // Create the managed DID record as EXTERNAL
        val managedDid =
            ManagedDid(
                id = recordId,
                did = did,
                method = parsed.method,
                alias = alias,
                document = resolutionResult.didDocument,
                role = DidRole.EXTERNAL,
                deactivated = false,
                keys = emptyList(), // No key mappings for external DIDs
                createdAt = now,
                updatedAt = now,
            )

        // Persist
        repository.save(managedDid.toDidRecord()).getOrElse {
            return Err(it)
        }

        return Ok(managedDid)
    }

    private fun currentTimestamp(): String =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.UTC)
            .toString()

    private fun generateId(prefix: String): String = "$prefix-${nextId++}"

    @ContributesTo(SessionScope::class)
    interface Graph {
        val didManager: DidManager
    }
}
