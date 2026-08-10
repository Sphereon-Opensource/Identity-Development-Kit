/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.conf

/**
 * Config key identifying the application (platform) tenant.
 *
 * Every subsystem that has to decide whether the current session belongs to the
 * application tenant reads this one key: the platform-admin authority path for
 * secret management, license resolution and enforcement, identifier protection,
 * and the application bootstrap ceremony. They must all agree, otherwise a
 * deployment that leaves the key unset ends up with one subsystem believing the
 * platform tenant is called X while another believes it is called Y, and the
 * platform-admin path can never match.
 */
const val KEY_APPLICATION_TENANT_ID: String = "application.tenant.id"

/**
 * The single fallback value for [KEY_APPLICATION_TENANT_ID].
 *
 * This is the ONLY place this default may be declared. Read it from here rather
 * than repeating the literal, so the resolved application tenant id can never
 * differ between two call sites in the same process.
 */
const val DEFAULT_APPLICATION_TENANT_ID: String = "platform"

/**
 * Every value a deployment may currently have live as its application tenant id
 * without having configured [KEY_APPLICATION_TENANT_ID] explicitly.
 *
 * [DEFAULT_APPLICATION_TENANT_ID] is what an unset key resolves to today.
 * `application` is what it resolved to in deployments provisioned before the
 * fallback was unified, and those installations still carry a tenant under that
 * id, so both names identify a real application tenant somewhere in the field.
 *
 * These are identities of the deployment itself, never of a customer. Anything
 * that hands out tenant identities to customers (slug reservation being the
 * first) must treat this whole set as unavailable, otherwise a customer can
 * claim the name the deployment answers to. A deployment that configures
 * [KEY_APPLICATION_TENANT_ID] to some other value adds that value on top of
 * this floor; this set is the part that holds with no configuration at all.
 */
val RESERVED_APPLICATION_TENANT_IDS: Set<String> = setOf(
    DEFAULT_APPLICATION_TENANT_ID,
    "application",
)
