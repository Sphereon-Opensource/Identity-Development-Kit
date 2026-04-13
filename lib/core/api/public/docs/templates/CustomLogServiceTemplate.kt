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
