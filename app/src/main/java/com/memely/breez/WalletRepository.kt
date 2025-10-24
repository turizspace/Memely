package com.memely.breez

import kotlinx.coroutines.flow.StateFlow

class WalletRepository {
    fun balanceFlow(): StateFlow<Long> = BreezManager.balance
}
