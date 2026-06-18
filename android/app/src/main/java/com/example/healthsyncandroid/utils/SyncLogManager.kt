package com.example.healthsyncandroid.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object SyncLogManager {
    private val _logs = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 50)
    val logs: SharedFlow<String> = _logs

    fun log(message: String) {
        _logs.tryEmit(message)
    }
}
