package com.duorhythm.app.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

val LifecycleOwner.lifecycleScope: CoroutineScope
    get() {
        val coroutineJob = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Main.immediate + coroutineJob)
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    coroutineJob.cancel()
                    source.lifecycle.removeObserver(this)
                }
            }
        })
        return scope
    }

suspend fun LifecycleOwner.repeatOnLifecycle(
    state: Lifecycle.State,
    block: suspend CoroutineScope.() -> Unit
) {
    if (lifecycle.currentState == Lifecycle.State.DESTROYED) return
    coroutineScope {
        var launchedJob: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.upTo(state)) {
                if (launchedJob == null || launchedJob?.isCancelled == true) {
                    launchedJob = launch { block() }
                }
            } else if (event == Lifecycle.Event.downFrom(state)) {
                launchedJob?.cancel()
                launchedJob = null
            } else if (event == Lifecycle.Event.ON_DESTROY) {
                launchedJob?.cancel()
                launchedJob = null
            }
        }
        lifecycle.addObserver(observer)
        try {
            if (lifecycle.currentState.isAtLeast(state) && launchedJob == null) {
                launchedJob = launch { block() }
            }
            awaitCancellation()
        } finally {
            lifecycle.removeObserver(observer)
            launchedJob?.cancel()
        }
    }
}
