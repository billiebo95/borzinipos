package com.borzini.pos.di

import android.content.Context
import androidx.room.Room
import com.borzini.pos.data.backup.BackupRepository
import com.borzini.pos.data.db.BorziniDatabase
import com.borzini.pos.data.prefs.SettingsDataStore
import com.borzini.pos.data.repository.CatalogRepository
import com.borzini.pos.data.repository.DemoDataSeeder
import com.borzini.pos.data.repository.ExpenseRepository
import com.borzini.pos.data.repository.FirstRunSeeder
import com.borzini.pos.data.repository.GoalRepository
import com.borzini.pos.data.repository.InventoryRepository
import com.borzini.pos.data.repository.PurchaseRepository
import com.borzini.pos.data.repository.ReturnRepository
import com.borzini.pos.data.repository.SaleRepository
import com.borzini.pos.data.repository.StatsRepository
import com.borzini.pos.data.repository.SyncQueueHelper
import com.borzini.pos.sync.GoogleAuthManager
import com.borzini.pos.sync.SyncEngine
import com.borzini.pos.ui.pos.CartHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-written dependency container (no Hilt/Dagger): one instance built in
 * [com.borzini.pos.BorziniApplication.onCreate] and reused everywhere. Kept deliberately simple -
 * every ViewModel factory just reads what it needs off this object.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val database: BorziniDatabase = Room.databaseBuilder(
        appContext,
        BorziniDatabase::class.java,
        BorziniDatabase.DATABASE_NAME,
    ).build()

    val settingsDataStore = SettingsDataStore(appContext)

    private val syncQueueHelper = SyncQueueHelper(database.syncQueueDao())

    val catalogRepository = CatalogRepository(database, syncQueueHelper)
    val inventoryRepository = InventoryRepository(database, syncQueueHelper)
    val purchaseRepository = PurchaseRepository(database, syncQueueHelper)
    val saleRepository = SaleRepository(database, syncQueueHelper)
    val returnRepository = ReturnRepository(database, syncQueueHelper)
    val expenseRepository = ExpenseRepository(database, syncQueueHelper)
    val goalRepository = GoalRepository(database, syncQueueHelper)
    val statsRepository = StatsRepository(database)
    val backupRepository = BackupRepository(database)

    val googleAuthManager = GoogleAuthManager(appContext, settingsDataStore)
    val syncEngine = SyncEngine(appContext, database, settingsDataStore, googleAuthManager)

    val cartHolder = CartHolder()

    val firstRunSeeder = FirstRunSeeder(catalogRepository)
    val demoDataSeeder = DemoDataSeeder(catalogRepository, inventoryRepository)

    /** Fire-and-forget bootstrap scope: only used for the one-time first-run category seed below. */
    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        bootstrapScope.launch { firstRunSeeder.seedDefaultCategoriesIfEmpty() }
    }
}
