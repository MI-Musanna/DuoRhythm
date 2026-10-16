package com.pocketdev.duorhythm.ui.wallet

import java.util.concurrent.CopyOnWriteArrayList

object WalletDataSyncManager {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun register(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregister(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        for (listener in listeners) {
            try {
                listener.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
