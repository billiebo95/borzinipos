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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.core.BusinessCalendar
import com.borzini.pos.core.StatsPeriodPreset
import com.borzini.pos.data.backup.BackupPreview
import com.borzini.pos.data.backup.RestoreResult
import com.borzini.pos.data.export.CsvExporter
import com.borzini.pos.data.prefs.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.dp

@Composable
fun BackupScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var pendingRestorePreview by remember { mutableStateOf<BackupPreview?>(null) }

    val backupFileName = remember {
        "borzini_backup_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(java.time.LocalDateTime.now()) + ".json"
    }

    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tempFile = File(context.cacheDir, "borzini_export_temp.json")
            val preview = container.backupRepository.export(tempFile)
            context.contentResolver.openOutputStream(uri)?.use { out -> tempFile.inputStream().use { it.copyTo(out) } }
            statusMessage = "Резервная копия создана: товаров ${preview.counts["Товары"]}, чеков ${preview.counts["Чеки"]}"
        }
    }

    val openRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tempFile = File(context.cacheDir, "borzini_restore_temp.json")
            context.contentResolver.openInputStream(uri)?.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
            val preview = container.backupRepository.readPreview(tempFile)
            if (preview == null) {
                statusMessage = "Не удалось прочитать файл — это не резервная копия BORZINI"
            } else {
                pendingRestoreFile = tempFile
                pendingRestorePreview = preview
            }
        }
    }

    val exportSalesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val zone = ZoneId.of(settings.timezoneId)
            val range = BusinessCalendar.resolve(StatsPeriodPreset.THIS_MONTH, zone, Instant.now())
            val sales = container.saleRepository.observeSalesInRange(range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli(), true).first()
            val tempFile = File(context.cacheDir, "sales_export.csv")
            CsvExporter.exportSales(tempFile, sales, zone)
            context.contentResolver.openOutputStream(uri)?.use { out -> tempFile.inputStream().use { it.copyTo(out) } }
            statusMessage = "Экспортировано чеков: ${sales.size}"
        }
    }

    val exportInventoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val items = container.inventoryRepository.observeAllItems().first()
            val tempFile = File(context.cacheDir, "inventory_export.csv")
            CsvExporter.exportInventory(tempFile, items)
            context.contentResolver.openOutputStream(uri)?.use { out -> tempFile.inputStream().use { it.copyTo(out) } }
            statusMessage = "Экспортировано позиций склада: ${items.size}"
        }
    }

    val exportExpensesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val zone = ZoneId.of(settings.timezoneId)
            val range = BusinessCalendar.resolve(StatsPeriodPreset.THIS_MONTH, zone, Instant.now())
            val expenses = container.expenseRepository.observeInRange(range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli(), true).first()
            val tempFile = File(context.cacheDir, "expenses_export.csv")
            CsvExporter.exportExpenses(tempFile, expenses, zone)
            context.contentResolver.openOutputStream(uri)?.use { out -> tempFile.inputStream().use { it.copyTo(out) } }
            statusMessage = "Экспортировано расходов: ${expenses.size}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Резервное копирование") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Резервная копия включает весь учёт: товары, склад, закупки, чеки, возвраты, расходы и цели.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { createBackupLauncher.launch(backupFileName) }) { Text("Создать резервную копию") }
            Button(onClick = { openRestoreLauncher.launch(arrayOf("application/json")) }) { Text("Восстановить из копии") }

            HorizontalDivider()
            Text("Экспорт в CSV (текущий месяц)", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { exportSalesLauncher.launch("sales.csv") }) { Text("Экспортировать продажи") }
            Button(onClick = { exportInventoryLauncher.launch("inventory.csv") }) { Text("Экспортировать склад") }
            Button(onClick = { exportExpensesLauncher.launch("expenses.csv") }) { Text("Экспортировать расходы") }

            statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    val restoreFile = pendingRestoreFile
    val preview = pendingRestorePreview
    if (restoreFile != null && preview != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreFile = null; pendingRestorePreview = null },
            title = { Text("Восстановить эту копию?") },
            text = {
                Column {
                    Text("Дата копии: ${java.time.Instant.ofEpochMilli(preview.exportedAtEpochMillis)}")
                    preview.counts.forEach { (label, count) -> Text("$label: $count") }
                    Text(
                        "Текущие данные будут заменены. Перед восстановлением будет автоматически создана защитная копия текущих данных.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val safetyFile = File(context.filesDir, "backups/before_restore_${System.currentTimeMillis()}.json")
                        val result = container.backupRepository.restore(restoreFile, safetyFile)
                        statusMessage = when (result) {
                            is RestoreResult.Ok -> "Данные восстановлены. Защитная копия сохранена: ${result.safetyBackupPath}"
                            is RestoreResult.IncompatibleFormat -> "Формат копии не поддерживается (версия ${result.foundVersion})"
                            is RestoreResult.Invalid -> result.message
                        }
                        pendingRestoreFile = null
                        pendingRestorePreview = null
                    }
                }) { Text("Восстановить") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreFile = null; pendingRestorePreview = null }) { Text("Отмена") } },
        )
    }
}
