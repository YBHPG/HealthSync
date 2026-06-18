package com.example.healthsyncandroid.ui.main

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.example.healthsyncandroid.service.LocalServerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainScreenViewModel : ViewModel() {
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    fun toggleServer(context: Context, isChecked: Boolean) {
        val intent = Intent(context, LocalServerService::class.java)
        if (isChecked) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
        _isServerRunning.value = isChecked
    }
}
