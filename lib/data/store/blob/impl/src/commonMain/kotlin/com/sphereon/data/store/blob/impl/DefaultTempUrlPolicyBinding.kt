package com.sphereon.data.store.blob.impl

import com.sphereon.data.store.blob.DefaultTempUrlPolicy
import com.sphereon.data.store.blob.TempUrlPolicy
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
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
