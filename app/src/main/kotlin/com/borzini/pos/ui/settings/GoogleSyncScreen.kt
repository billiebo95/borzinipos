@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.borzini.pos.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.data.prefs.AppSettings
import com.borzini.pos.sync.SyncStatus
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.dp

private fun statusText(status: SyncStatus): String = when (status) {
    is SyncStatus.AllSynced -> "Всё синхронизировано"
    is SyncStatus.PendingChanges -> "Есть несинхронизированные изменения: ${status.count}"
    is SyncStatus.Syncing -> "Синхронизация…"
    is SyncStatus.NoInternet -> "Нет интернета"
    is SyncStatus.NeedsSignIn -> "Требуется вход в Google-аккаунт"
    is SyncStatus.Error -> "Ошибка синхронизации: ${status.message}"
}

@Composable
fun GoogleSyncScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val syncStatus by container.syncEngine.status.collectAsState()
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.syncEngine.refreshStatus() }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            container.googleAuthManager.handleSignInResult(result.data)
            container.syncEngine.refreshStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google-аккаунт и синхронизация") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Локальная база — основа работы кассы. Google Sheets — синхронизируемое представление данных для просмотра и отчётов, Google Drive хранит резервные копии.",
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider()

            val account = container.googleAuthManager.lastSignedInAccount()
            if (account == null) {
                Text("Аккаунт не подключён", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { signInLauncher.launch(container.googleAuthManager.signInIntent()) }) { Text("Войти в Google-аккаунт") }
            } else {
                Text("Аккаунт: ${account.email}", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    scope.launch { container.googleAuthManager.signOut(); container.syncEngine.refreshStatus() }
                }) { Text("Выйти") }
            }

            HorizontalDivider()
            Text("Статус: ${statusText(syncStatus)}", style = MaterialTheme.typography.bodyLarge)
            settings.lastSyncEpochMillis?.let {
                val formatted = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of(settings.timezoneId)).format(Instant.ofEpochMilli(it))
                Text("Последняя успешная синхронизация: $formatted")
            }

            Button(
                onClick = {
                    isBusy = true
                    scope.launch {
                        container.syncEngine.syncNow()
                        isBusy = false
                    }
                },
                enabled = !isBusy && account != null,
            ) {
                if (isBusy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp)) else Text("Синхронизировать сейчас")
            }
        }
    }
}
