package com.sphereon.statuslist.hosting.rest

import kotlin.test.Test
import kotlin.test.assertTrue

class StatusListAdminCommandIdsTest {
    @Test
    fun adminCommandIdsUseTheCanonicalThreeSegmentFormat() {
        val commandIds =
            listOf(
                StatusListHostingApiConstants.AdminCommandIds.ENTRY_GET,
                StatusListHostingApiConstants.AdminCommandIds.ENTRY_REVOKE,
                StatusListHostingApiConstants.AdminCommandIds.CLEAR,
            )

        commandIds.forEach { commandId ->
            assertTrue(
                commandId.matches(Regex("^[a-z0-9]+(?:-[a-z0-9]+)*\\.[a-z0-9]+(?:-[a-z0-9]+)*\\.[a-z0-9]+(?:-[a-z0-9]+)*$")),
                "Invalid command ID: $commandId",
            )
        }
    }
}
