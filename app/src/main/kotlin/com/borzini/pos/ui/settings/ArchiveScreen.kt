package com.borzini.pos.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun ArchiveScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val products by container.catalogRepository.observeAllProducts().collectAsState(initial = emptyList())
    val items by container.inventoryRepository.observeAllItems().collectAsState(initial = emptyList())
    val categories by container.catalogRepository.observeAllCategories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val archivedProducts = products.filter { it.isArchived }
    val archivedItems = items.filter { it.isArchived }
    val archivedCategories = categories.filter { it.isArchived }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Архив") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Text("Товары", modifier = Modifier.padding(16.dp)) }
            items(archivedProducts, key = { it.id }) { product ->
                ListItem(
                    headlineContent = { Text(product.name) },
                    trailingContent = {
                        TextButton(onClick = { scope.launch { container.catalogRepository.setProductArchived(product.id, false, Instant.now().toEpochMilli()) } }) { Text("Восстановить") }
                    },
                )
                HorizontalDivider()
            }
            item { Text("Складские позиции", modifier = Modifier.padding(16.dp)) }
            items(archivedItems, key = { it.id }) { invItem ->
                ListItem(
                    headlineContent = { Text(invItem.name) },
                    trailingContent = {
                        TextButton(onClick = { scope.launch { container.inventoryRepository.setArchived(invItem.id, false, Instant.now().toEpochMilli()) } }) { Text("Восстановить") }
                    },
                )
                HorizontalDivider()
            }
            item { Text("Категории", modifier = Modifier.padding(16.dp)) }
            items(archivedCategories, key = { it.id }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    trailingContent = {
                        TextButton(onClick = { scope.launch { container.catalogRepository.setCategoryArchived(category.id, false, Instant.now().toEpochMilli()) } }) { Text("Восстановить") }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
