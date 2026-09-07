package com.borzini.pos.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.data.db.entities.CategoryEntity
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class CategoryManagerViewModel(private val container: AppContainer) : ViewModel() {
    val categories = container.catalogRepository.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(id: String?, name: String) = viewModelScope.launch {
        container.catalogRepository.saveCategory(id, name, Instant.now().toEpochMilli())
    }

    fun setArchived(id: String, archived: Boolean) = viewModelScope.launch {
        container.catalogRepository.setCategoryArchived(id, archived, Instant.now().toEpochMilli())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val viewModel: CategoryManagerViewModel = viewModel(factory = com.borzini.pos.ui.common.SimpleViewModelFactory { CategoryManagerViewModel(container) })
    val categories by viewModel.categories.collectAsState()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Категории") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "Добавить категорию") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(categories, key = { it.id }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = { if (category.isArchived) Text("В архиве") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { editing = category }) { Text("Переименовать") }
                            Switch(
                                checked = !category.isArchived,
                                onCheckedChange = { viewModel.setArchived(category.id, !it) },
                            )
                        }
                    },
                )
            }
        }
    }

    if (showNewDialog) {
        CategoryEditDialog(initialName = "", onDismiss = { showNewDialog = false }) { name ->
            viewModel.save(null, name)
            showNewDialog = false
        }
    }
    editing?.let { category ->
        CategoryEditDialog(initialName = category.name, onDismiss = { editing = null }) { name ->
            viewModel.save(category.id, name)
            editing = null
        }
    }
}

@Composable
private fun CategoryEditDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Категория") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
