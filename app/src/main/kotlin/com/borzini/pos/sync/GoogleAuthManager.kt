package com.borzini.pos.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.borzini.pos.data.prefs.SettingsDataStore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in for BORZINI's own sync, entirely separate from Claude/Google Drive access this
 * chat session may have - the Android app authorises itself with the coffee shop owner's Google
 * account the first time they open Settings -> "Google-аккаунт и синхронизация" and tap sign in.
 *
 * Scope requested is deliberately the minimal one: [DriveScopes.DRIVE_FILE] ("view and manage
 * its own files") covers both Drive (the BORZINI backup folder) and the Sheets it creates inside
 * that folder - Sheets API operations are permitted under drive.file for spreadsheets the app
 * itself created, so no broader `.../auth/spreadsheets` or full-Drive scope is requested.
 */
class GoogleAuthManager(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) {
    private val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun signInIntent(): Intent = signInClient.signInIntent

    fun lastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun hasRequiredScope(account: GoogleSignInAccount): Boolean =
        GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))

    suspend fun handleSignInResult(intent: Intent?): Result<GoogleSignInAccount> = runCatching {
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn
            .getSignedInAccountFromIntent(intent)
            .await()
        settingsDataStore.setGoogleAccountEmail(account.email)
        account
    }

    suspend fun signOut() {
        runCatching { signInClient.signOut().await() }
        settingsDataStore.setGoogleAccountEmail(null)
        settingsDataStore.setDriveAndSheetIds(null, null)
    }

    /** Builds an API credential for the currently signed-in account, or null if nobody is signed in. */
    fun currentCredential(): GoogleAccountCredential? {
        val account = lastSignedInAccount() ?: return null
        val accountName = account.account?.name ?: account.email ?: return null
        return GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .apply { selectedAccount = Account(accountName, "com.google") }
    }
}
