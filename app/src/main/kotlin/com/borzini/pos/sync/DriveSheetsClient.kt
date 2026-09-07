package com.borzini.pos.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val DRIVE_FOLDER_MIME = "application/vnd.google-apps.folder"
private const val SPREADSHEET_MIME = "application/vnd.google-apps.spreadsheet"

/**
 * Plain HTTPS REST calls to the Drive v3 and Sheets v4 APIs, authorised with the OAuth access
 * token from [GoogleAuthManager.getAccessToken]. Deliberately not built on Google's generated
 * Java client libraries (com.google.api-client / com.google.apis:google-api-services-*) - those
 * are published only to Google's own Maven repository under revision-dated version strings this
 * environment could not verify (see README.md). The REST surface used here is small and stable:
 * files.list/create/update/get, spreadsheets.create/batchUpdate, and values.get/update/append/clear.
 */
class DriveSheetsClient(private val accessToken: String) {

    private fun request(urlString: String, method: String, jsonBody: JSONObject? = null): JSONObject {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 20_000
            readTimeout = 30_000
        }
        if (jsonBody != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(jsonBody.toString().toByteArray(StandardCharsets.UTF_8)) }
        }
        return readJsonResponse(connection)
    }

    private fun readJsonResponse(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
        if (code !in 200..299) {
            throw IOException("Google API $code for ${connection.url}: $body")
        }
        if (body.isBlank()) return JSONObject()
        return JSONObject(body)
    }

    private fun uploadMedia(urlString: String, method: String, contentType: String, bytes: ByteArray): JSONObject {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", contentType)
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        connection.outputStream.use { it.write(bytes) }
        return readJsonResponse(connection)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    suspend fun ensureFolder(name: String = "BORZINI"): String = withContext(Dispatchers.IO) {
        val query = "mimeType = '$DRIVE_FOLDER_MIME' and name = '$name' and trashed = false and 'root' in parents"
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=${enc(query)}&fields=${enc("files(id,name)")}"
        val existing = request(listUrl, "GET")
        existing.optJSONArray("files")?.let { files ->
            if (files.length() > 0) return@withContext files.getJSONObject(0).getString("id")
        }
        val body = JSONObject().apply { put("name", name); put("mimeType", DRIVE_FOLDER_MIME) }
        val created = request("https://www.googleapis.com/drive/v3/files?fields=id", "POST", body)
        created.getString("id")
    }

    suspend fun ensureSpreadsheet(folderId: String, name: String = "BORZINI - данные"): String = withContext(Dispatchers.IO) {
        val query = "mimeType = '$SPREADSHEET_MIME' and name = '$name' and trashed = false and '$folderId' in parents"
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=${enc(query)}&fields=${enc("files(id,name)")}"
        val existing = request(listUrl, "GET")
        existing.optJSONArray("files")?.let { files ->
            if (files.length() > 0) return@withContext files.getJSONObject(0).getString("id")
        }

        val createBody = JSONObject().apply {
            put("properties", JSONObject().apply { put("title", name) })
        }
        val created = request("https://sheets.googleapis.com/v4/spreadsheets", "POST", createBody)
        val spreadsheetId = created.getString("spreadsheetId")

        // Move the newly created spreadsheet (created in "My Drive" root) into the BORZINI folder.
        val fileInfo = request("https://www.googleapis.com/drive/v3/files/$spreadsheetId?fields=parents", "GET")
        val previousParents = fileInfo.optJSONArray("parents")?.let { arr ->
            (0 until arr.length()).joinToString(",") { arr.getString(it) }
        } ?: ""
        val moveUrl = "https://www.googleapis.com/drive/v3/files/$spreadsheetId" +
            "?addParents=${enc(folderId)}&removeParents=${enc(previousParents)}&fields=id"
        request(moveUrl, "PATCH", JSONObject())

        setUpTabs(spreadsheetId, created)
        spreadsheetId
    }

    private fun setUpTabs(spreadsheetId: String, created: JSONObject) {
        val defaultSheetId = created.optJSONArray("sheets")
            ?.optJSONObject(0)?.optJSONObject("properties")?.optInt("sheetId") ?: 0

        val requests = JSONArray()
        requests.put(
            JSONObject().apply {
                put(
                    "updateSheetProperties",
                    JSONObject().apply {
                        put("properties", JSONObject().apply { put("sheetId", defaultSheetId); put("title", SheetsSchema.ALL.first().sheetName) })
                        put("fields", "title")
                    },
                )
            },
        )
        SheetsSchema.ALL.drop(1).forEach { def ->
            requests.put(
                JSONObject().apply {
                    put("addSheet", JSONObject().apply { put("properties", JSONObject().apply { put("title", def.sheetName) }) })
                },
            )
        }
        request(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate",
            "POST",
            JSONObject().apply { put("requests", requests) },
        )

        SheetsSchema.ALL.forEach { def ->
            val range = "'${def.sheetName}'!A1"
            val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${enc(range)}?valueInputOption=RAW"
            val values = JSONArray().apply { put(JSONArray(def.headers)) }
            request(url, "PUT", JSONObject().apply { put("values", values) })
        }
    }

    /** Finds the 1-indexed row whose column A equals [id] on [sheetName], or null if not found yet. */
    private fun findRow(spreadsheetId: String, sheetName: String, id: String): Int? {
        val range = "'$sheetName'!A:A"
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${enc(range)}"
        val result = request(url, "GET")
        val values = result.optJSONArray("values") ?: return null
        for (i in 0 until values.length()) {
            val row = values.optJSONArray(i)
            if (row != null && row.length() > 0 && row.getString(0) == id) return i + 1
        }
        return null
    }

    suspend fun upsertRow(spreadsheetId: String, sheetName: String, id: String, rowValues: List<Any?>) = withContext(Dispatchers.IO) {
        val existingRow = findRow(spreadsheetId, sheetName, id)
        val body = JSONObject().apply { put("values", JSONArray().apply { put(JSONArray(rowValues)) }) }
        if (existingRow != null) {
            val range = "'$sheetName'!A$existingRow:${columnLetter(rowValues.size)}$existingRow"
            val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${enc(range)}?valueInputOption=RAW"
            request(url, "PUT", body)
        } else {
            val range = "'$sheetName'!A:A"
            val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${enc(range)}:append" +
                "?valueInputOption=RAW&insertDataOption=INSERT_ROWS"
            request(url, "POST", body)
        }
        Unit
    }

    suspend fun clearRow(spreadsheetId: String, sheetName: String, id: String) = withContext(Dispatchers.IO) {
        val row = findRow(spreadsheetId, sheetName, id) ?: return@withContext
        val range = "'$sheetName'!A$row:Z$row"
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${enc(range)}:clear"
        request(url, "POST", JSONObject())
        Unit
    }

    suspend fun uploadBackupFile(folderId: String, localFile: File, driveFileName: String) = withContext(Dispatchers.IO) {
        val query = "name = '$driveFileName' and trashed = false and '$folderId' in parents"
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=${enc(query)}&fields=${enc("files(id)")}"
        val existing = request(listUrl, "GET").optJSONArray("files")

        val bytes = localFile.readBytes()
        if (existing != null && existing.length() > 0) {
            val id = existing.getJSONObject(0).getString("id")
            uploadMedia("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media", "PATCH", "application/json", bytes)
        } else {
            val metadata = JSONObject().apply { put("name", driveFileName); put("parents", JSONArray().apply { put(folderId) }) }
            val created = request("https://www.googleapis.com/drive/v3/files?fields=id", "POST", metadata)
            val id = created.getString("id")
            uploadMedia("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media", "PATCH", "application/json", bytes)
        }
        Unit
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
