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

import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * The UserScope is the second scope in a hierarchy. It is created early on and binds Tenant and Principal information.
 * In a multitenant environment it is likely there are multiple instances of objects for different tenants and principals, however a single principal typically only has one object.
 *
 * The UserScope is the second and scope created, directly below the SureAppScope in any application using the Sphereon libraries.
 * Normally you would instantiate the scope using the ContextManager, as early as possible. In a REST API for instance with a request interceptor/middleware before passing on the
 * request to your session layer. In for instance a mobile or web application you would tie it to the authentication process
 *
 * When using @SingleIn(UserScope::class) you are creating an object or component that effectively will be a singleton for a single tenant and principal.
 * A principal is an abstraction for an account, which can either be a user, or a system/machine account. A principal is always associated with a tenant.
 * To not make to many contexts, we decided to make this a combined scope for 2 entities in the system (principal and tenant).
 * If you are creating a session or method that is not interested in either the principal or the tenant you can ignore the information about the other entity
 * in the context that will be injected.
 * However typically you will want to check the type of principal even if you are not interested. For instance a background session typically is not running as a regular user.*
 *
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserScope", exact = true)
public abstract class UserScope private constructor()
