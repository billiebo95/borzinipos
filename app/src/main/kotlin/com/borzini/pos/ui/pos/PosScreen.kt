package com.borzini.pos.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.ui.Routes
import com.borzini.pos.ui.common.SimpleViewModelFactory
import androidx.compose.runtime.collectAsState

private const val TABLET_MIN_WIDTH_DP = 600

@Composable
fun PosScreen(navController: NavController) {
    val appContainer = LocalAppContainer.current
    val viewModel: PosViewModel = viewModel(factory = SimpleViewModelFactory { PosViewModel(appContainer) })
    val state by viewModel.uiState.collectAsState()

    var pickerFor by remember { mutableStateOf<ProductCardUi?>(null) }

    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_MIN_WIDTH_DP

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("BORZINI", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Поиск по названию") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                item {
                    FilterChip(
                        selected = state.selectedCategoryId == POPULAR_CATEGORY_ID,
                        onClick = { viewModel.selectCategory(POPULAR_CATEGORY_ID) },
                        label = { Text("Популярное") },
                    )
                }
                items(state.categories) { category ->
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }

            if (state.products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет товаров в этой категории", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(state.products, key = { it.product.id }) { card ->
                        val qtyInCart = state.cart.filter { it.productId == card.product.id }.sumOf { it.quantity }
                        ProductCard(
                            card = card,
                            quantityInCart = qtyInCart,
                            onTap = {
                                if (card.hasSingleVariant) viewModel.addSimpleTap(card) else pickerFor = card
                            },
                        )
                    }
                }
            }
        }

        if (isTablet) {
            CartPanel(
                modifier = Modifier.width(340.dp).fillMaxHeight(),
                cart = state.cart,
                cartTotal = state.cartTotal,
                onIncrement = { line -> viewModel.setLineQuantity(line.lineId, line.quantity + 1) },
                onDecrement = { line -> viewModel.setLineQuantity(line.lineId, line.quantity - 1) },
                onRemove = { line -> viewModel.removeLine(line.lineId) },
                onClear = viewModel::clearCart,
                onCheckout = { navController.navigate(Routes.CHECKOUT) },
            )
        }
    }

    if (!isTablet && state.cart.isNotEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("${state.cartCount} поз.", style = MaterialTheme.typography.bodyMedium)
                        Text(state.cartTotal.format(), style = MaterialTheme.typography.titleLarge)
                    }
                    Button(onClick = { navController.navigate(Routes.CHECKOUT) }) {
                        Text("К оплате")
                    }
                }
            }
        }
    }

    pickerFor?.let { card ->
        VariantPickerDialog(
            card = card,
            onDismiss = { pickerFor = null },
            onPick = { variant ->
                viewModel.addVariant(card.product, variant)
                pickerFor = null
            },
        )
    }
}

@Composable
private fun ProductCard(card: ProductCardUi, quantityInCart: Int, onTap: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        onClick = onTap,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                card.product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(card.displayPrice.format(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (quantityInCart > 0) {
                    Box(
                        Modifier
                            .padding(4.dp)
                            .height(28.dp)
                            .width(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(shape = RoundedCornerShape(50)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("$quantityInCart", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
