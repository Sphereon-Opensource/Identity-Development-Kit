/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.blob.impl

import com.sphereon.data.store.blob.DefaultTempUrlPolicy
import com.sphereon.data.store.blob.TempUrlPolicy
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * DI-bound default temp URL policy. Always approves.
 * EDK replaces via @ContributesBinding(replaces = [DefaultTempUrlPolicyImpl::class]).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<TempUrlPolicy>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultTempUrlPolicyImpl", exact = true)
class DefaultTempUrlPolicyImpl : TempUrlPolicy by DefaultTempUrlPolicy()
