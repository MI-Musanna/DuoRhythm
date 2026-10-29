package com.duorhythm.app.ui.wallet

object WalletSessionManager {
    @Volatile
    var isWalletUnlockedForSession: Boolean = false

    fun lockWallet() {
        isWalletUnlockedForSession = false
    }

    fun unlockWallet() {
        isWalletUnlockedForSession = true
    }
}
