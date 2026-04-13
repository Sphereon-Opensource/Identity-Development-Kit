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
 */

package com.sphereon.identity.matching.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.identity.matching.model.CreateIdentityMatchArgs
import com.sphereon.identity.matching.model.DeleteIdentityMatchArgs
import com.sphereon.identity.matching.model.IdentityMatch
import com.sphereon.identity.matching.model.ListIdentityMatchesArgs
import com.sphereon.identity.matching.model.LookupIdentityMatchArgs
import com.sphereon.identity.matching.model.MatchResult

interface LookupIdentityMatchCommand : ServiceCommand<LookupIdentityMatchArgs, MatchResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "identity.matching.lookup"
    }
}

interface CreateIdentityMatchCommand : ServiceCommand<CreateIdentityMatchArgs, IdentityMatch> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "identity.matching.create"
    }
}

interface DeleteIdentityMatchCommand : ServiceCommand<DeleteIdentityMatchArgs, Boolean> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "identity.matching.delete"
    }
}

interface ListIdentityMatchesCommand : ServiceCommand<ListIdentityMatchesArgs, List<IdentityMatch>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "identity.matching.list"
    }
}
