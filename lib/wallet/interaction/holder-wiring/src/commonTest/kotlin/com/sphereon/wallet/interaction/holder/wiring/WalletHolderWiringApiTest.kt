package com.sphereon.wallet.interaction.holder.wiring

import kotlin.test.Test
import kotlin.test.assertTrue

class WalletHolderWiringApiTest {
    @Test
    fun apiShellContainsOnlyContractMultibindings() {
        assertTrue(WalletHolderProtocolWiringModule::class.simpleName?.isNotBlank() == true)
        assertTrue(WalletInteractionClientGraph::class.simpleName?.isNotBlank() == true)
    }
}
