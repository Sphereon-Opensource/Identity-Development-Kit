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

/*
 * Minimal starter template for a custom log service.
 *
 * Copy into your module and replace package/type names.
 */

package com.example.idk.extensions

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AbstractLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService

class CustomLogService(
    private val sink: suspend (LogMessage) -> Unit,
) : AbstractLogService(
    id = SERVICE_ID,
), LogService {

    override val scope: IdkScope = IdkScope.APP

    override suspend fun doExecute(
        args: LogMessage,
        applyDuring: (LogMessage) -> LogMessage
    ): IdkResult<Unit, IdkErrorType> {
        val processed = applyDuring(args)
        sink(processed)
        return Unit.asOkResult()
    }

    companion object {
        const val SERVICE_ID = "CustomLogService"
    }
}
