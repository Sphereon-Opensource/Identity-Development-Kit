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

package com.sphereon.did.methods.webvh.provider.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface WebvhProviderCommandDescriptors {
    @Provides
    @IntoMap
    @StringKey(CreateWebvhDidServiceCommand.COMMAND_ID)
    fun create(impl: CreateWebvhDidServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(UpdateWebvhDidServiceCommand.COMMAND_ID)
    fun update(impl: UpdateWebvhDidServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(DeactivateWebvhDidServiceCommand.COMMAND_ID)
    fun deactivate(impl: DeactivateWebvhDidServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(CreateWitnessProofServiceCommand.COMMAND_ID)
    fun createWitnessProof(impl: CreateWitnessProofServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(UpdateWitnessFileServiceCommand.COMMAND_ID)
    fun updateWitnessFile(impl: UpdateWitnessFileServiceCommandImpl): ServiceCommand<*, *, *> = impl
}
