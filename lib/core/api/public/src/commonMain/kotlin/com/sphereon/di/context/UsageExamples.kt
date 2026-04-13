/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.di.context

import com.sphereon.di.session.SessionContextManager

/**
 * This file contains usage examples showing how to use the enhanced multi-context
 * system to solve common challenges in cloud and mobile app development.
 */

/**
 * Example 1: Background Service Management
 *
 * Problem: Anonymous user scope is used for both background services (like NFC)
 * and non-logged-in state, but they should be separate sessions.
 *
 * Solution: Use dedicated background service contexts and sessions.
 */
class BackgroundServiceExample {

    fun setupBackgroundServices(userContextManager: UserContextManager) {
        // Create a dedicated background service context
        val backgroundContext = userContextManager.getBackgroundService()

        // Create specific sessions for different background services
        val contextInstance = userContextManager.getBackgroundService()
        val sessionManager = contextInstance.sessionContextManager

        // NFC service session
        val nfcSession = sessionManager.createOrGetFromId("nfc-background-session", makeActive = false)

        // Bluetooth service session
        val bluetoothSession = sessionManager.createOrGetFromId("bluetooth-background-session", makeActive = false)

        // Location service session
        val locationSession = sessionManager.createOrGetFromId("location-background-session", makeActive = false)
    }
}

/**
 * Example 2: Cross-Context Operations (NFC Tap Example)
 *
 * Problem: An NFC tap in a background service needs to perform operations
 * in the authenticated user session, but they're in different DI scopes.
 *
 * Solution: Use CrossContextOperations to safely execute operations across boundaries.
 */
class NFCCrossContextExample {

    suspend fun handleNFCTap(
        crossContextOps: CrossContextOperations,
        nfcData: String
    ) {
        // Background NFC service receives tap
        println("NFC tap received in background service: $nfcData")

        // Execute the NFC operation in the best available authenticated context
        val result = crossContextOps.executeInBestAuthenticatedContext(
            operation = { userContext, scope ->
                // This runs in the authenticated user's context
                println("Processing NFC tap for user: ${userContext.principal}")

                // Get user-specific services from this context
                val credentialService = scope.getService<Any>("credentialService") // Replace with actual service type
                val walletService = scope.getService<Any>("walletService") // Replace with actual service type

                // Process the NFC data with user context
                "NFC operation completed for ${userContext.tenant.tenantId}"
            }
        )

        println("NFC operation result: $result")
    }

    suspend fun handleNFCTapWithSpecificUser(
        crossContextOps: CrossContextOperations,
        targetTenantId: String,
        nfcData: String
    ) {
        // Execute in a specific user's context, useful when you know which user should handle it
        val result = crossContextOps.executeInBestAuthenticatedContext(
            operation = { userContext, scope ->
                "NFC processed for specific tenant: ${userContext.tenant.tenantId}"
            },
            preferredTenantId = targetTenantId
        )

        println("Targeted NFC operation result: $result")
    }
}

/**
 * Example 3: Multi-User Context Management
 *
 * Problem: Need to access contexts and sessions by ID, and list available contexts.
 *
 * Solution: Use the enhanced manager methods for context lookup and listing.
 */
class MultiUserContextExample {

    fun manageMultipleUsers(userContextManager: UserContextManager) {
        // List all contexts
        val allContexts = userContextManager.listIds()
        println("All contexts: $allContexts")

        // Get specific context by ID (can be null)
        val userContextInstance = userContextManager.getById("tenant1:user@example.com")
        println("User context: ${userContextInstance?.context}")

        // Switch between contexts
        val switchSuccess = userContextManager.activateById("tenant2:admin@example.com")
        if (switchSuccess) {
            println("Switched to admin context")
        }

        // Create context with specific ID for easy retrieval
        val specialContext = userContextManager.createOrGetWithId(
            contextId = "special-user-context",
            tenantAware = object : TenantAware {
                override val tenant = object : TenantContextData {
                    override val tenantId = "special-tenant"
                }
            },
            principalAware = object : PrincipalAware {
                override val principal = "special@user.com"
            },
            makeActive = false
        )
    }

    fun manageMultipleSessions(sessionContextManager: SessionContextManager) {
        // List all sessions for current user context
        val allSessions = sessionContextManager.listIds()
        println("All sessions: $allSessions")

        // Create multiple sessions for different purposes
        sessionContextManager.createOrGetFromId("web-session", makeActive = false)
        sessionContextManager.createOrGetFromId("mobile-session", makeActive = false)
        val apiSession = sessionContextManager.createOrGetFromId("api-session", makeActive = true)

        // Switch between sessions
        val switchSuccess = sessionContextManager.activateById("api-session")
        if (switchSuccess) {
            println("Switched to API session")
        }

        // Add service to specific session using instance
//        apiSession.addService("webAuthService", object {}) // Replace with actual service
    }
}

/**
 * Example 4: Service Management Across Contexts
 *
 * Problem: Need to access services from different contexts/sessions safely.
 *
 * Solution: Use service management methods and cross-context operations.
 */
class ServiceManagementExample {

    fun accessServicesAcrossContexts(crossContextOps: CrossContextOperations) {
        // Get available authenticated contexts
        val authContexts = crossContextOps.getAvailableAuthenticatedContexts()
        println("Available authenticated contexts: ${authContexts.keys}")

        // Get sessions for a specific context
        val contextId = authContexts.keys.firstOrNull()
        if (contextId != null) {
            val sessions = crossContextOps.getAvailableSessionsForContext(contextId)
            println("Sessions for $contextId: ${sessions.keys}")

            // Access a service from a specific context/session using reified extension
            val service: String? = crossContextOps.getServiceFromContext(
                targetContextId = contextId,
                targetSessionId = sessions.keys.firstOrNull()
            )

            println("Retrieved service: $service")
        }
    }
}

/**
 * Example 6: Always Available Current Instance
 *
 * Benefit: getCurrentContextInstance() and getCurrentSessionInstance() never return null.
 * If no specific context/session is active, you get the anonymous instance.
 * This eliminates null checks and makes the API more predictable.
 */
class AlwaysAvailableInstanceExample {

    fun demonstrateAlwaysAvailable(userContextManager: UserContextManager) {
        // These methods NEVER return null - you always get an instance!
        val activeContext = userContextManager.getActive()  // No null check needed!
        val sessionManager = activeContext.sessionContextManager
        val activeSession = sessionManager.getActive()      // No null check needed!

        // You can always safely call methods without null checks
        println("Active context: ${activeContext.contextId}")
        println("Active session: ${activeSession.sessionId}")

        // If no specific context is active, you get anonymous
        println("Is anonymous: ${activeContext.context.principal == null}")

        // Service access is always available
        try {
            val service = activeContext.getService<Any>("someService")
            println("Service found: $service")
        } catch (e: Exception) {
            println("Service not found, but no null pointer!")
        }
    }

    fun beforeAndAfterComparison(userContextManager: UserContextManager) {
        // OLD WAY (with nulls):
        // val context = userContextManager.getActiveContext()
        // if (context != null) {
        //     val sessionManager = context.getSessionManager()
        //     val session = sessionManager.getActiveSession()
        //     if (session != null) {
        //         // Finally do work...
        //     }
        // }

        // NEW WAY (always available):
        val context = userContextManager.getActive()
        val sessionManager = context.sessionContextManager
        val session = sessionManager.getActive()

        // Always safe to use - no null checks needed!
        println("Working with context ${context.contextId} and session ${session.sessionId}")
    }

    fun demonstrateMakeActiveCapability(userContextManager: UserContextManager) {
        // Get context by ID and optionally make it active
        val existingContext = userContextManager.getById("user-123", makeActive = true)

        // Get context by domain object and optionally make it active
        val tenantAware = object : TenantAware {
            override val tenant = object : TenantContextData {
                override val tenantId = "tenant-456"
            }
        }
        val principalAware = object : PrincipalAware {
            override val principal = "user@example.com"
        }

        val contextByDomain = userContextManager.get(tenantAware, principalAware, makeActive = true)

        // Now this context is active
        val activeContext = userContextManager.getActive()
        println("Active context after get: ${activeContext.contextId}")
    }
}

/**
 * Example 5: Cleanup and Resource Management
 *
 * Solution: Properly clean up contexts and sessions when no longer needed.
 */
class ResourceManagementExample {

    fun cleanupExample(userContextManager: UserContextManager) {
        // Clear specific context
        userContextManager.destroyById("old-user-context")

        // Clear all contexts (careful!)
        // userContextManager.destroyAllContexts()

        // Get session manager from active instance (always available)
        val activeInstance = userContextManager.getActive()
        val sessionManager = activeInstance.sessionContextManager

        // Clear specific session
        sessionManager.destroyById("old-session")

        // Clear all sessions for active context
        // sessionManager.destroyAllSessions()
    }
}