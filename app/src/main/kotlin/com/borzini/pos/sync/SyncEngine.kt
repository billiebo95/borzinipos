package com.borzini.pos.sync

import android.content.Context
import com.borzini.pos.data.backup.BackupRepository
import com.borzini.pos.data.db.BorziniDatabase
import com.borzini.pos.data.db.entities.SyncEntityType
import com.borzini.pos.data.db.entities.SyncOperation
import com.borzini.pos.data.prefs.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.time.Instant

sealed class SyncStatus {
    data object AllSynced : SyncStatus()
    data class PendingChanges(val count: Int) : SyncStatus()
    data object Syncing : SyncStatus()
    data object NoInternet : SyncStatus()
    data object NeedsSignIn : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class SyncRunResult(val pushed: Int, val failed: Int, val status: SyncStatus)

/**
 * Pushes the durable local outbox (SyncQueueDao) out to the BORZINI Drive folder/Sheets, one
 * batch at a time. One-directional (local -> Sheets) by design for this first version - see spec
 * section 9: Sheets is a synced VIEW for people to read and report from, direct edits there are
 * never silently pulled back in and overwriting local sales/stock history.
 */
class SyncEngine(
    private val context: Context,
    private val db: BorziniDatabase,
    private val settingsDataStore: SettingsDataStore,
    private val authManager: GoogleAuthManager,
) {
    private val networkMonitor = NetworkMonitor(context)
    private val syncQueueDao = db.syncQueueDao()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.AllSynced)
    val status: StateFlow<SyncStatus> = _status

    suspend fun refreshStatus() {
        val pending = syncQueueDao.observePendingCount().first()
        _status.value = when {
            !networkMonitor.isOnline() -> SyncStatus.NoInternet
            authManager.lastSignedInAccount() == null -> if (pending > 0) SyncStatus.PendingChanges(pending) else SyncStatus.AllSynced
            pending > 0 -> SyncStatus.PendingChanges(pending)
            else -> SyncStatus.AllSynced
        }
    }

    /** Manual "Синхронизировать сейчас" and the periodic WorkManager job both call this. */
    suspend fun syncNow(batchSize: Int = 25): SyncRunResult {
        if (!networkMonitor.isOnline()) {
            _status.value = SyncStatus.NoInternet
            return SyncRunResult(0, 0, SyncStatus.NoInternet)
        }
        val settings = settingsDataStore.settingsFlow.first()
        if (!settings.syncOverMeteredAllowed && networkMonitor.isMetered()) {
            // Respect the user's "only sync on Wi-Fi" choice - not an error, just not now.
            val pending = syncQueueDao.observePendingCount().first()
            val status = if (pending > 0) SyncStatus.PendingChanges(pending) else SyncStatus.AllSynced
            _status.value = status
            return SyncRunResult(0, 0, status)
        }
        val account = authManager.lastSignedInAccount()
        if (account == null) {
            _status.value = SyncStatus.NeedsSignIn
            return SyncRunResult(0, 0, SyncStatus.NeedsSignIn)
        }
        val accessToken = authManager.getAccessToken()
        if (accessToken == null) {
            _status.value = SyncStatus.NeedsSignIn
            return SyncRunResult(0, 0, SyncStatus.NeedsSignIn)
        }

        _status.value = SyncStatus.Syncing
        return try {
            val client = DriveSheetsClient(accessToken)
            var folderId = settings.driveFolderId
            var spreadsheetId = settings.spreadsheetId
            if (folderId == null) {
                folderId = client.ensureFolder()
            }
            if (spreadsheetId == null) {
                spreadsheetId = client.ensureSpreadsheet(folderId)
            }
            settingsDataStore.setDriveAndSheetIds(folderId, spreadsheetId)

            var pushed = 0
            var failed = 0
            val batch = syncQueueDao.nextBatch(batchSize)
            for (entry in batch) {
                syncQueueDao.setStatus(entry.id, "IN_PROGRESS")
                try {
                    val type = SyncEntityType.valueOf(entry.entityType)
                    val definition = SheetsSchema.forType(type)
                    val payload = JSONObject(entry.payloadJson)
                    if (entry.operation == SyncOperation.DELETE.name) {
                        client.clearRow(spreadsheetId, definition.sheetName, entry.entityId)
                    } else {
                        val row = RowBuilders.build(type, payload)
                        client.upsertRow(spreadsheetId, definition.sheetName, entry.entityId, row)
                    }
                    syncQueueDao.markDone(entry.id)
                    pushed++
                } catch (e: Exception) {
                    syncQueueDao.markFailed(entry.id, e.message ?: e.toString())
                    failed++
                }
            }

            // Best-effort: also refresh the Drive backup file so a restore point always exists
            // even if nobody opened Settings -> "Резервное копирование" recently.
            runCatching {
                val backupFile = File(context.cacheDir, "borzini_auto_backup.json")
                BackupRepository(db).export(backupFile)
                client.uploadBackupFile(folderId, backupFile, "borzini_backup_latest.json")
            }

            settingsDataStore.setLastSyncEpochMillis(Instant.now().toEpochMilli())
            val remaining = syncQueueDao.observePendingCount().first()
            val finalStatus = if (remaining > 0) SyncStatus.PendingChanges(remaining) else SyncStatus.AllSynced
            _status.value = finalStatus
            SyncRunResult(pushed, failed, finalStatus)
        } catch (e: Exception) {
            val errorStatus = SyncStatus.Error(e.message ?: "Неизвестная ошибка синхронизации")
            _status.value = errorStatus
            SyncRunResult(0, 0, errorStatus)
        }
    }
}
