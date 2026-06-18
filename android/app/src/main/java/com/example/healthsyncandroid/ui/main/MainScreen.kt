package com.example.healthsyncandroid.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import com.example.healthsyncandroid.utils.NetworkDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.healthsyncandroid.utils.SyncLogManager

@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel()
) {
  val context = LocalContext.current
  val isServerRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
  
  val healthRepository = remember { com.example.healthsyncandroid.data.HealthRepository(context) }
  val isSupported by healthRepository.isSupported.collectAsStateWithLifecycle(initialValue = false)
  var hasPermissions by remember { mutableStateOf(false) }
  var grantedPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
  val coroutineScope = rememberCoroutineScope()

  val logs = remember { androidx.compose.runtime.mutableStateListOf<String>() }
  
  LaunchedEffect(Unit) {
      SyncLogManager.logs.collect { log ->
          logs.add(log)
          if (logs.size > 50) {
              logs.removeAt(0)
          }
      }
  }

  val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()

  val requestPermissions = rememberLauncherForActivityResult(requestPermissionActivityContract) { granted ->
      coroutineScope.launch {
          hasPermissions = healthRepository.checkPermissions()
          grantedPermissions = healthRepository.getGrantedPermissions()
      }
  }

  LaunchedEffect(Unit) {
      hasPermissions = healthRepository.checkPermissions()
      grantedPermissions = healthRepository.getGrantedPermissions()
  }
  
  var ipAddress by remember { mutableStateOf<String?>("Retrieving...") }

  LaunchedEffect(Unit) {
      withContext(Dispatchers.IO) {
          ipAddress = NetworkDiagnostics.getLocalIPv4Address() ?: "No Wi-Fi Connection"
      }
  }

  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
      Text(text = "Health Sync", style = MaterialTheme.typography.headlineMedium)
      Spacer(modifier = Modifier.height(16.dp))
      
      Text(text = "Local IP Address: $ipAddress", style = MaterialTheme.typography.bodyLarge)
      Spacer(modifier = Modifier.height(32.dp))
      
      Text(text = "Server Status", style = MaterialTheme.typography.titleMedium)
      Switch(
          checked = isServerRunning,
          onCheckedChange = { isChecked ->
              viewModel.toggleServer(context, isChecked)
          }
      )
      Text(text = if (isServerRunning) "Running on port 8080" else "Stopped")
      
      Spacer(modifier = Modifier.height(32.dp))
      Text(text = "Health Connect", style = MaterialTheme.typography.titleMedium)
      Text(text = "Supported: ${if (isSupported) "Yes" else "No"}")
      Text(text = "Permissions Granted: ${if (hasPermissions) "Yes" else "No"}")
      
      if (isSupported) {
          Spacer(modifier = Modifier.height(16.dp))
          androidx.compose.material3.Button(onClick = {
              requestPermissions.launch(healthRepository.requiredPermissions)
          }) {
              Text("Request Permissions")
          }
          if (grantedPermissions.isNotEmpty()) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(text = "Granted: ${grantedPermissions.size}/${healthRepository.requiredPermissions.size}", style = MaterialTheme.typography.bodySmall)
          }
      }
      
      Spacer(modifier = Modifier.height(32.dp))
      Text(text = "Sync Logs", style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(8.dp))
      
      LazyColumn(
          modifier = Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
              .padding(8.dp)
      ) {
          items(logs) { log ->
              Text(text = log, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.height(4.dp))
          }
      }
  }
}
