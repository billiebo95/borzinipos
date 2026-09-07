package com.borzini.pos.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.borzini.pos.ui.pos.PosScreen
import com.borzini.pos.ui.catalog.ProductEditorScreen
import com.borzini.pos.ui.catalog.CategoryManagerScreen
import com.borzini.pos.ui.checkout.CheckoutScreen
import com.borzini.pos.ui.warehouse.WarehouseScreen
import com.borzini.pos.ui.warehouse.InventoryItemEditorScreen
import com.borzini.pos.ui.warehouse.PurchaseEditorScreen
import com.borzini.pos.ui.warehouse.PurchaseListScreen
import com.borzini.pos.ui.warehouse.StockMovementsScreen
import com.borzini.pos.ui.warehouse.InventoryCountScreen
import com.borzini.pos.ui.receipts.ReceiptsScreen
import com.borzini.pos.ui.receipts.ReceiptDetailScreen
import com.borzini.pos.ui.stats.StatsScreen
import com.borzini.pos.ui.settings.SettingsScreen
import com.borzini.pos.ui.settings.ArchiveScreen
import com.borzini.pos.ui.settings.ExpensesScreen
import com.borzini.pos.ui.settings.GoalsScreen
import com.borzini.pos.ui.settings.BackupScreen
import com.borzini.pos.ui.settings.GoogleSyncScreen
import com.borzini.pos.ui.settings.AppearanceScreen
import com.borzini.pos.ui.settings.CoffeeShopSettingsScreen

object Routes {
    const val POS = "pos"
    const val CHECKOUT = "checkout"
    const val PRODUCT_EDITOR = "product_editor?productId={productId}"
    const val CATEGORY_MANAGER = "category_manager"
    const val WAREHOUSE = "warehouse"
    const val INVENTORY_ITEM_EDITOR = "inventory_item_editor?itemId={itemId}"
    const val PURCHASE_LIST = "purchase_list"
    const val PURCHASE_EDITOR = "purchase_editor?purchaseId={purchaseId}"
    const val STOCK_MOVEMENTS = "stock_movements/{itemId}"
    const val INVENTORY_COUNT = "inventory_count/{itemId}"
    const val RECEIPTS = "receipts"
    const val RECEIPT_DETAIL = "receipt_detail/{saleId}"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ARCHIVE = "archive"
    const val EXPENSES = "expenses"
    const val GOALS = "goals"
    const val BACKUP = "backup"
    const val GOOGLE_SYNC = "google_sync"
    const val APPEARANCE = "appearance"
    const val COFFEE_SHOP_SETTINGS = "coffee_shop_settings"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.POS, "Касса", Icons.Filled.PointOfSale),
    BottomTab(Routes.WAREHOUSE, "Склад", Icons.Filled.Inventory2),
    BottomTab(Routes.STATS, "Статистика", Icons.Filled.BarChart),
    BottomTab(Routes.SETTINGS, "Настройки", Icons.Filled.Settings),
)

@Composable
fun BorziniNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.POS,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable(Routes.POS) { PosScreen(navController) }
            composable(Routes.CHECKOUT) { CheckoutScreen(navController) }
            composable(
                Routes.PRODUCT_EDITOR,
                arguments = listOf(navArgument("productId") { type = NavType.StringType; nullable = true }),
            ) { backStack ->
                ProductEditorScreen(navController, backStack.arguments?.getString("productId"))
            }
            composable(Routes.CATEGORY_MANAGER) { CategoryManagerScreen(navController) }

            composable(Routes.WAREHOUSE) { WarehouseScreen(navController) }
            composable(
                Routes.INVENTORY_ITEM_EDITOR,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType; nullable = true }),
            ) { backStack ->
                InventoryItemEditorScreen(navController, backStack.arguments?.getString("itemId"))
            }
            composable(Routes.PURCHASE_LIST) { PurchaseListScreen(navController) }
            composable(
                Routes.PURCHASE_EDITOR,
                arguments = listOf(navArgument("purchaseId") { type = NavType.StringType; nullable = true }),
            ) { backStack ->
                PurchaseEditorScreen(navController, backStack.arguments?.getString("purchaseId"))
            }
            composable(
                Routes.STOCK_MOVEMENTS,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStack ->
                StockMovementsScreen(navController, requireNotNull(backStack.arguments?.getString("itemId")))
            }
            composable(
                Routes.INVENTORY_COUNT,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStack ->
                InventoryCountScreen(navController, requireNotNull(backStack.arguments?.getString("itemId")))
            }

            composable(Routes.RECEIPTS) { ReceiptsScreen(navController) }
            composable(
                Routes.RECEIPT_DETAIL,
                arguments = listOf(navArgument("saleId") { type = NavType.StringType }),
            ) { backStack ->
                ReceiptDetailScreen(navController, requireNotNull(backStack.arguments?.getString("saleId")))
            }

            composable(Routes.STATS) { StatsScreen(navController) }

            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.ARCHIVE) { ArchiveScreen(navController) }
            composable(Routes.EXPENSES) { ExpensesScreen(navController) }
            composable(Routes.GOALS) { GoalsScreen(navController) }
            composable(Routes.BACKUP) { BackupScreen(navController) }
            composable(Routes.GOOGLE_SYNC) { GoogleSyncScreen(navController) }
            composable(Routes.APPEARANCE) { AppearanceScreen(navController) }
            composable(Routes.COFFEE_SHOP_SETTINGS) { CoffeeShopSettingsScreen(navController) }
        }
    }
}
