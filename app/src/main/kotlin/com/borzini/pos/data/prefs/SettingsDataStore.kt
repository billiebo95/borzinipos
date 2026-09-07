package com.borzini.pos.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "borzini_settings")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    /** Hex ARGB string, e.g. "FF6F4E37" (coffee brown) - the default BORZINI accent. */
    val accentColorHex: String = "FF6F4E37",
    /** 0.85..1.30 multiplier applied on top of Compose's base type scale. */
    val textScale: Float = 1.0f,
    val timezoneId: String = "Europe/Moscow",
    val negativeStockAllowed: Boolean = false,
    val demoModeEnabled: Boolean = false,
    val coffeeShopName: String = "BORZINI",
    val syncOverMeteredAllowed: Boolean = false,
    val autoAcquiringFeeEnabled: Boolean = false,
    /** Percent, e.g. 1.8 for 1.8%, applied to cashless turnover when [autoAcquiringFeeEnabled]. */
    val autoAcquiringFeePercent: Float = 1.8f,
    val googleAccountEmail: String? = null,
    val driveFolderId: String? = null,
    val spreadsheetId: String? = null,
    val lastSyncEpochMillis: Long? = null,
)

/**
 * App-wide settings (not tied to any one sale/purchase/product, so kept out of Room): theming,
 * timezone, stock policy, and the small amount of sync state that is safe to keep outside the
 * durable sync queue (which lives in Room - see SyncQueueDao).
 */
class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val TIMEZONE_ID = stringPreferencesKey("timezone_id")
        val NEGATIVE_STOCK_ALLOWED = booleanPreferencesKey("negative_stock_allowed")
        val DEMO_MODE_ENABLED = booleanPreferencesKey("demo_mode_enabled")
        val COFFEE_SHOP_NAME = stringPreferencesKey("coffee_shop_name")
        val SYNC_OVER_METERED = booleanPreferencesKey("sync_over_metered_allowed")
        val AUTO_FEE_ENABLED = booleanPreferencesKey("auto_acquiring_fee_enabled")
        val AUTO_FEE_PERCENT = floatPreferencesKey("auto_acquiring_fee_percent")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val SPREADSHEET_ID = stringPreferencesKey("spreadsheet_id")
        val LAST_SYNC_EPOCH_MILLIS = longPreferencesKey("last_sync_epoch_millis")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.LIGHT,
            accentColorHex = prefs[Keys.ACCENT_COLOR] ?: "FF6F4E37",
            textScale = prefs[Keys.TEXT_SCALE] ?: 1.0f,
            timezoneId = prefs[Keys.TIMEZONE_ID] ?: "Europe/Moscow",
            negativeStockAllowed = prefs[Keys.NEGATIVE_STOCK_ALLOWED] ?: false,
            demoModeEnabled = prefs[Keys.DEMO_MODE_ENABLED] ?: false,
            coffeeShopName = prefs[Keys.COFFEE_SHOP_NAME] ?: "BORZINI",
            syncOverMeteredAllowed = prefs[Keys.SYNC_OVER_METERED] ?: false,
            autoAcquiringFeeEnabled = prefs[Keys.AUTO_FEE_ENABLED] ?: false,
            autoAcquiringFeePercent = prefs[Keys.AUTO_FEE_PERCENT] ?: 1.8f,
            googleAccountEmail = prefs[Keys.GOOGLE_ACCOUNT_EMAIL],
            driveFolderId = prefs[Keys.DRIVE_FOLDER_ID],
            spreadsheetId = prefs[Keys.SPREADSHEET_ID],
            lastSyncEpochMillis = prefs[Keys.LAST_SYNC_EPOCH_MILLIS],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setAccentColorHex(hex: String) = context.dataStore.edit { it[Keys.ACCENT_COLOR] = hex }
    suspend fun setTextScale(scale: Float) = context.dataStore.edit { it[Keys.TEXT_SCALE] = scale }
    suspend fun setTimezoneId(id: String) = context.dataStore.edit { it[Keys.TIMEZONE_ID] = id }
    suspend fun setNegativeStockAllowed(allowed: Boolean) =
        context.dataStore.edit { it[Keys.NEGATIVE_STOCK_ALLOWED] = allowed }

    suspend fun setDemoModeEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.DEMO_MODE_ENABLED] = enabled }
    suspend fun setCoffeeShopName(name: String) = context.dataStore.edit { it[Keys.COFFEE_SHOP_NAME] = name }
    suspend fun setSyncOverMeteredAllowed(allowed: Boolean) =
        context.dataStore.edit { it[Keys.SYNC_OVER_METERED] = allowed }

    suspend fun setAutoAcquiringFee(enabled: Boolean, percent: Float) = context.dataStore.edit {
        it[Keys.AUTO_FEE_ENABLED] = enabled
        it[Keys.AUTO_FEE_PERCENT] = percent
    }

    suspend fun setGoogleAccountEmail(email: String?) = context.dataStore.edit {
        if (email == null) it.remove(Keys.GOOGLE_ACCOUNT_EMAIL) else it[Keys.GOOGLE_ACCOUNT_EMAIL] = email
    }

    suspend fun setDriveAndSheetIds(driveFolderId: String?, spreadsheetId: String?) = context.dataStore.edit {
        if (driveFolderId == null) it.remove(Keys.DRIVE_FOLDER_ID) else it[Keys.DRIVE_FOLDER_ID] = driveFolderId
        if (spreadsheetId == null) it.remove(Keys.SPREADSHEET_ID) else it[Keys.SPREADSHEET_ID] = spreadsheetId
    }

    suspend fun setLastSyncEpochMillis(millis: Long) = context.dataStore.edit { it[Keys.LAST_SYNC_EPOCH_MILLIS] = millis }
}
