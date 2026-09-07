package com.borzini.pos.sync

import android.content.Context
import android.content.Intent
import com.borzini.pos.data.prefs.SettingsDataStore
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Drive scope, requested literally rather than via a generated Drive client constant - see app/build.gradle.kts. */
private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/**
 * Google sign-in for BORZINI's own sync, entirely separate from any Google Drive access this
 * Claude session may have - the Android app authorises itself with the coffee shop owner's Google
 * account the first time they open Settings -> "Google-аккаунт и синхронизация" and tap sign in.
 *
 * Scope requested is deliberately the minimal one: "drive.file" ("view and manage its own
 * files") covers both Drive (the BORZINI backup folder) and the Sheets it creates inside that
 * folder - Sheets API operations are permitted under drive.file for spreadsheets the app itself
 * created, so no broader `.../auth/spreadsheets` or full-Drive scope is requested.
 */
class GoogleAuthManager(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) {
    private val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DRIVE_FILE_SCOPE))
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun signInIntent(): Intent = signInClient.signInIntent

    fun lastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun hasRequiredScope(account: GoogleSignInAccount): Boolean = GoogleSignIn.hasPermissions(account, Scope(DRIVE_FILE_SCOPE))

    suspend fun handleSignInResult(intent: Intent?): Result<GoogleSignInAccount> = runCatching {
        val account = GoogleSignIn.getSignedInAccountFromIntent(intent).await()
        settingsDataStore.setGoogleAccountEmail(account.email)
        account
    }

    suspend fun signOut() {
        runCatching { signInClient.signOut().await() }
        settingsDataStore.setGoogleAccountEmail(null)
        settingsDataStore.setDriveAndSheetIds(null, null)
    }

    /**
     * A short-lived OAuth access token for the signed-in account, or null if nobody is signed in.
     * [GoogleAuthUtil.getToken] talks to Google Play Services' account manager, which caches and
     * silently refreshes tokens itself - there is no manual refresh-token handling to get wrong.
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = lastSignedInAccount() ?: return@withContext null
        val androidAccount = account.account ?: return@withContext null
        runCatching {
            GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_FILE_SCOPE")
        }.getOrNull()
    }
}
