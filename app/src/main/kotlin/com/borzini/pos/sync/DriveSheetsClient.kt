package com.borzini.pos.sync

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File as JavaFile

private const val APP_NAME = "BORZINI"
private const val DRIVE_FOLDER_MIME = "application/vnd.google-apps.folder"

/**
 * Thin, explicit wrapper around the Drive v3 and Sheets v4 Java clients. Every call is a plain
 * blocking HTTP request from the generated client, moved onto Dispatchers.IO - there is no hidden
 * retry/backoff here beyond what SyncEngine itself does around a whole batch.
 */
class DriveSheetsClient(private val credential: GoogleAccountCredential) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    val drive: Drive by lazy {
        Drive.Builder(transport, jsonFactory, credential).setApplicationName(APP_NAME).build()
    }
    val sheets: Sheets by lazy {
        Sheets.Builder(transport, jsonFactory, credential).setApplicationName(APP_NAME).build()
    }

    suspend fun ensureFolder(name: String = APP_NAME): String = withContext(Dispatchers.IO) {
        val query = "mimeType = '$DRIVE_FOLDER_MIME' and name = '$name' and trashed = false and 'root' in parents"
        val existing = drive.files().list().setQ(query).setSpaces("drive").execute()
        existing.files?.firstOrNull()?.id?.let { return@withContext it }

        val folder = DriveFile().setName(name).setMimeType(DRIVE_FOLDER_MIME)
        drive.files().create(folder).setFields("id").execute().id
    }

    suspend fun ensureSpreadsheet(folderId: String, name: String = "$APP_NAME - данные"): String = withContext(Dispatchers.IO) {
        val query = "mimeType = 'application/vnd.google-apps.spreadsheet' and name = '$name' and trashed = false and '$folderId' in parents"
        val existing = drive.files().list().setQ(query).setSpaces("drive").execute()
        existing.files?.firstOrNull()?.id?.let { return@withContext it }

        val spreadsheet = Spreadsheet().setProperties(SpreadsheetProperties().setTitle(name))
        val created = sheets.spreadsheets().create(spreadsheet).execute()
        val spreadsheetId = requireNotNull(created.spreadsheetId)

        // Newly created spreadsheet lives in "My Drive" root - move it into the BORZINI folder.
        val file = drive.files().get(spreadsheetId).setFields("parents").execute()
        val previousParents = file.parents?.joinToString(",") ?: ""
        drive.files().update(spreadsheetId, null)
            .setAddParents(folderId)
            .setRemoveParents(previousParents)
            .setFields("id, parents")
            .execute()

        setUpTabs(spreadsheetId, created)
        spreadsheetId
    }

    private suspend fun setUpTabs(spreadsheetId: String, created: Spreadsheet) = withContext(Dispatchers.IO) {
        val defaultSheetId = created.sheets?.firstOrNull()?.properties?.sheetId ?: 0
        val requests = mutableListOf<Request>()

        // Rename the default first tab to our first schema sheet instead of leaving "Лист1".
        requests += Request().setUpdateSheetProperties(
            UpdateSheetPropertiesRequest()
                .setProperties(SheetProperties().setSheetId(defaultSheetId).setTitle(SheetsSchema.ALL.first().sheetName))
                .setFields("title"),
        )
        SheetsSchema.ALL.drop(1).forEach { def ->
            requests += Request().setAddSheet(AddSheetRequest().setProperties(SheetProperties().setTitle(def.sheetName)))
        }
        sheets.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(requests)).execute()

        SheetsSchema.ALL.forEach { def ->
            sheets.spreadsheets().values()
                .update(
                    spreadsheetId,
                    "'${def.sheetName}'!A1",
                    ValueRange().setValues(listOf(def.headers)),
                )
                .setValueInputOption("RAW")
                .execute()
        }
    }

    /** Finds the row whose column A equals [id] on [sheetName], or null if it is not there yet. */
    private suspend fun findRow(spreadsheetId: String, sheetName: String, id: String): Int? = withContext(Dispatchers.IO) {
        val range = "'$sheetName'!A:A"
        val values = sheets.spreadsheets().values().get(spreadsheetId, range).execute().getValues() ?: return@withContext null
        val index = values.indexOfFirst { it.isNotEmpty() && it[0].toString() == id }
        if (index < 0) null else index + 1 // Sheets rows are 1-indexed.
    }

    /** Updates the existing row for [id], or appends a new one if it has never been synced before. */
    suspend fun upsertRow(spreadsheetId: String, sheetName: String, id: String, rowValues: List<Any?>) =
        withContext(Dispatchers.IO) {
            val existingRow = findRow(spreadsheetId, sheetName, id)
            val body = ValueRange().setValues(listOf(rowValues))
            if (existingRow != null) {
                val lastColumn = columnLetter(rowValues.size)
                sheets.spreadsheets().values()
                    .update(spreadsheetId, "'$sheetName'!A$existingRow:$lastColumn$existingRow", body)
                    .setValueInputOption("RAW")
                    .execute()
            } else {
                sheets.spreadsheets().values()
                    .append(spreadsheetId, "'$sheetName'!A:A", body)
                    .setValueInputOption("RAW")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute()
            }
        }

    /** Blanks out a row's cells (kept in place, not physically deleted, so other cached row numbers stay valid). */
    suspend fun clearRow(spreadsheetId: String, sheetName: String, id: String) = withContext(Dispatchers.IO) {
        val row = findRow(spreadsheetId, sheetName, id) ?: return@withContext
        sheets.spreadsheets().values()
            .clear(spreadsheetId, "'$sheetName'!A$row:Z$row", ClearValuesRequest())
            .execute()
    }

    suspend fun uploadBackupFile(folderId: String, localFile: JavaFile, driveFileName: String) = withContext(Dispatchers.IO) {
        val query = "name = '$driveFileName' and trashed = false and '$folderId' in parents"
        val existing = drive.files().list().setQ(query).setSpaces("drive").execute().files?.firstOrNull()
        val media = FileContent("application/json", localFile)
        if (existing != null) {
            drive.files().update(existing.id, null, media).execute()
        } else {
            val metadata = DriveFile().setName(driveFileName).setParents(listOf(folderId))
            drive.files().create(metadata, media).execute()
        }
    }

    private fun columnLetter(columnCount: Int): String {
        var n = columnCount
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }
}
