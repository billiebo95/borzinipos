@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.borzini.pos.ui.warehouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.core.Money
import com.borzini.pos.data.prefs.AppSettings
import com.borzini.pos.ui.Routes
import com.borzini.pos.ui.common.formatDate
import java.time.ZoneId
import androidx.compose.ui.unit.dp

@Composable
fun PurchaseListScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val purchases by container.purchaseRepository.observeAll().collectAsState(initial = emptyList())
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val zone = ZoneId.of(settings.timezoneId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Закупки") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("purchase_editor") }) { Icon(Icons.Filled.Add, contentDescription = "Новая закупка") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(purchases, key = { it.id }) { purchase ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { navController.navigate("purchase_editor?purchaseId=${purchase.id}") },
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(purchase.supplier, style = MaterialTheme.typography.titleMedium)
                            Text(formatDate(purchase.date, zone), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(Money.ofKopecks(purchase.totalKopecks).format(), style = MaterialTheme.typography.titleMedium)
                            Text(if (purchase.isPosted) "Проведена" else "Черновик", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
