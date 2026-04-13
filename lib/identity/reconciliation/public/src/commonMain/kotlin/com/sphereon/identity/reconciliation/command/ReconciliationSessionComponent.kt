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
 */

package com.sphereon.identity.reconciliation.command

import com.sphereon.di.session.SessionComponent
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.reconciliation.store.ReconciliationProviderStore
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore
import dev.zacsweers.metro.ContributesTo

/**
 * Component interface for accessing reconciliation services from the DI graph.
 *
 * Use [SessionInstance.asReconciliationComponent] or [SessionComponent.asReconciliationComponent]
 * to access these services from a session context.
 */
@ContributesTo(SessionScope::class)
interface ReconciliationSessionComponent {
    val createReconciliationSessionCommand: CreateReconciliationSessionCommand
    val completeReconciliationCommand: CompleteReconciliationCommand
    val getReconciliationSessionCommand: GetReconciliationSessionCommand
    val cancelReconciliationSessionCommand: CancelReconciliationSessionCommand

    val reconciliationSessionStore: ReconciliationSessionStore
    val reconciliationProviderStore: ReconciliationProviderStore
}

fun SessionInstance.asReconciliationComponent(): ReconciliationSessionComponent =
    this.component as ReconciliationSessionComponent

fun SessionComponent.asReconciliationComponent(): ReconciliationSessionComponent =
    this as ReconciliationSessionComponent
