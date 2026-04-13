/*
 * Copyright (c) 2026 Sphereon B.V.
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
package com.sphereon.sdjwt.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.sdjwt.IssueSdJwtArgs
import com.sphereon.sdjwt.IssueSdJwtResult

/**
 * Command interface for issuing SD-JWTs.
 */
interface IssueSdJwtCommand : ServiceCommand<IssueSdJwtArgs, IssueSdJwtResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "sdjwt.jwt.issue"
    }
}
