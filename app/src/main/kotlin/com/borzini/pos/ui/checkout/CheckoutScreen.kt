package com.borzini.pos.ui.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.core.Money
import com.borzini.pos.core.PaymentMethod
import com.borzini.pos.ui.common.SimpleViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val viewModel: CheckoutViewModel = viewModel(factory = SimpleViewModelFactory { CheckoutViewModel(container) })
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScopeCompat()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CheckoutEvent.Completed -> {
                    navController.popBackStack()
                    navController.navigate("receipt_detail/${event.saleId}")
                }
                is CheckoutEvent.Failed -> scope.launch { snackbarHostState.showSnackbar(event.message) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Оплата") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Сумма заказа", style = MaterialTheme.typography.bodyLarge)
            Text(state.total.format(), style = MaterialTheme.typography.displaySmall)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.paymentMethod == PaymentMethod.CASH,
                    onClick = { viewModel.setPaymentMethod(PaymentMethod.CASH) },
                    label = { Text("Наличные") },
                )
                FilterChip(
                    selected = state.paymentMethod == PaymentMethod.CASHLESS,
                    onClick = { viewModel.setPaymentMethod(PaymentMethod.CASHLESS) },
                    label = { Text("Безналичные") },
                )
            }

            if (state.paymentMethod == PaymentMethod.CASH) {
                OutlinedTextField(
                    value = state.cashReceivedText,
                    onValueChange = viewModel::setCashReceivedText,
                    label = { Text("Получено от клиента, ₽") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500, 1000, 2000, 5000).forEach { amount ->
                        Button(onClick = { viewModel.addQuickAmount(Money.fromRubles(java.math.BigDecimal(amount))) }) {
                            Text("$amount ₽")
                        }
                    }
                }
                Button(onClick = viewModel::setExactAmount, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Без сдачи")
                }
                state.change?.let { change ->
                    Text(
                        "Сдача: ${change.format()}",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            } else {
                Text(
                    "Подтвердите, что оплата получена (через терминал или другим способом).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

            Button(
                onClick = viewModel::submit,
                enabled = state.canComplete && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Завершить оплату")
                }
            }
        }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
