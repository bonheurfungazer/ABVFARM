package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.FinancialTransaction
import com.example.viewmodels.FarmViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancesScreen(navController: NavController, viewModel: FarmViewModel) {
    val transactions by viewModel.financialTransactions.collectAsState()
    val batches by viewModel.fatteningBatches.collectAsState()
    val rabbits by viewModel.rabbits.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "INCOME", "EXPENSE"
    var selectedCategoryFilter by remember { mutableStateOf("ALL") } // "ALL", "VENTE_CHAIR", "VENTE_REPRODUCTEUR", "ALIMENT", "SANTE", "MATERIEL"

    val totalIncome = remember(transactions) {
        transactions.filter { it.type == "INCOME" }.sumOf { it.amountFcfa }
    }
    val totalExpense = remember(transactions) {
        transactions.filter { it.type == "EXPENSE" }.sumOf { it.amountFcfa }
    }
    val netBalance = totalIncome - totalExpense

    val filteredTransactions = remember(transactions, selectedTypeFilter, selectedCategoryFilter) {
        transactions.filter { tx ->
            val matchType = when (selectedTypeFilter) {
                "INCOME" -> tx.type == "INCOME"
                "EXPENSE" -> tx.type == "EXPENSE"
                else -> true
            }
            val matchCat = when (selectedCategoryFilter) {
                "ALL" -> true
                else -> tx.category == selectedCategoryFilter
            }
            matchType && matchCat
        }
    }

    val currencyFormat = remember {
        NumberFormat.getInstance(Locale.FRENCH).apply {
            maximumFractionDigits = 0
        }
    }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Suivi Financier & Trésorerie", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Comptabilité de l'élevage (FCFA)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nouvelle Opération", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_finance_fab")
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main Financial Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Solde Net de l'Élevage",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${if (netBalance >= 0) "+" else ""}${currencyFormat.format(netBalance)} FCFA",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (netBalance >= 0) Color(0xFF166534) else MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(16.dp))
                                Text("Total Recettes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "+${currencyFormat.format(totalIncome)} FCFA",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Text("Total Dépenses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "-${currencyFormat.format(totalExpense)} FCFA",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            // Type filter chips (Tous / Recettes / Dépenses)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTypeFilter == "ALL",
                    onClick = { selectedTypeFilter = "ALL" },
                    label = { Text("Tous (${transactions.size})") }
                )
                FilterChip(
                    selected = selectedTypeFilter == "INCOME",
                    onClick = { selectedTypeFilter = "INCOME" },
                    label = { Text("🟢 Recettes") }
                )
                FilterChip(
                    selected = selectedTypeFilter == "EXPENSE",
                    onClick = { selectedTypeFilter = "EXPENSE" },
                    label = { Text("🔴 Dépenses") }
                )
            }

            // Categories horizontal scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val categories = listOf(
                    "ALL" to "Toutes catégories",
                    "VENTE_CHAIR" to "🥩 Vente Chair",
                    "VENTE_REPRODUCTEUR" to "🐰 Reproducteurs",
                    "ALIMENT" to "🌾 Alimentation",
                    "SANTE" to "🩺 Santé/Soins",
                    "MATERIEL" to "🛠️ Matériel"
                )
                categories.forEach { (catKey, catLabel) ->
                    AssistChip(
                        onClick = { selectedCategoryFilter = catKey },
                        label = { Text(catLabel, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selectedCategoryFilter == catKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            // Transaction list
            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            "Aucune transaction enregistrée",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Cliquez sur \"Nouvelle Opération\" pour enregistrer vos ventes ou dépenses d'exploitation.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { tx ->
                        val isIncome = tx.type == "INCOME"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (isIncome) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.errorContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isIncome) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (isIncome) Color(0xFF166534) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = when (tx.category) {
                                                    "VENTE_CHAIR" -> "🥩 Vente Chair"
                                                    "VENTE_REPRODUCTEUR" -> "🐰 Reproducteur"
                                                    "ALIMENT" -> "🌾 Alimentation"
                                                    "SANTE" -> "🩺 Santé/Soins"
                                                    "MATERIEL" -> "🛠️ Matériel"
                                                    else -> tx.category
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            dateFormatter.format(Date(tx.date)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tx.description.ifBlank { "Opération sans libellé" },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    if (tx.relatedBatchId != null) {
                                        val linkedBatch = batches.find { it.id == tx.relatedBatchId }
                                        Text(
                                            "Lot associé : ${linkedBatch?.batchCode ?: "#${tx.relatedBatchId}"}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (tx.relatedRabbitId != null) {
                                        val linkedRabbit = rabbits.find { it.id == tx.relatedRabbitId }
                                        Text(
                                            "Lapin associé : ${linkedRabbit?.name ?: "#${tx.relatedRabbitId}"}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${if (isIncome) "+" else "-"}${currencyFormat.format(tx.amountFcfa)} F",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isIncome) Color(0xFF166534) else MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp
                                    )

                                    IconButton(
                                        onClick = { viewModel.deleteFinancialTransaction(tx) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Supprimer",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Add Transaction
    if (showAddDialog) {
        var txType by remember { mutableStateOf("INCOME") } // "INCOME" or "EXPENSE"
        var category by remember { mutableStateOf("VENTE_CHAIR") }
        var amountStr by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var selectedBatchId by remember { mutableStateOf<Long?>(null) }
        var selectedRabbitId by remember { mutableStateOf<Int?>(null) }

        val incomeCategories = listOf(
            "VENTE_CHAIR" to "🥩 Vente de Lapins de Chair",
            "VENTE_REPRODUCTEUR" to "🐰 Vente de Reproducteurs",
            "AUTRE_RECETTE" to "💰 Autre Recette"
        )
        val expenseCategories = listOf(
            "ALIMENT" to "🌾 Achat d'Alimentation / Granulés",
            "SANTE" to "🩺 Soins Vétérinaires / Médicaments",
            "MATERIEL" to "🛠️ Équipement / Matériel d'Élevage",
            "AUTRE_DEPENSE" to "💸 Autre Dépense"
        )

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nouvelle Transaction Financière", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // Type Selector Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                txType = "INCOME"
                                category = "VENTE_CHAIR"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (txType == "INCOME") Color(0xFF166534) else Color.Transparent,
                                contentColor = if (txType == "INCOME") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🟢 Recette (+)", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                txType = "EXPENSE"
                                category = "ALIMENT"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (txType == "EXPENSE") MaterialTheme.colorScheme.error else Color.Transparent,
                                contentColor = if (txType == "EXPENSE") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔴 Dépense (-)", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Category dropdown
                    var expandedCategory by remember { mutableStateOf(false) }
                    val currentCatList = if (txType == "INCOME") incomeCategories else expenseCategories
                    val currentCatLabel = currentCatList.find { it.first == category }?.second ?: category

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = currentCatLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Catégorie") },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().clickable { expandedCategory = true },
                            shape = RoundedCornerShape(12.dp)
                        )
                        DropdownMenu(
                            expanded = expandedCategory,
                            onDismissRequest = { expandedCategory = false }
                        ) {
                            currentCatList.forEach { (catId, catName) ->
                                DropdownMenuItem(
                                    text = { Text(catName) },
                                    onClick = {
                                        category = catId
                                        expandedCategory = false
                                    }
                                )
                            }
                        }
                    }

                    // Amount in FCFA
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Montant (FCFA)") },
                        placeholder = { Text("Ex: 15000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Motif") },
                        placeholder = { Text("Ex: Vente de 5 lapins à l'hôtel...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Optional link to batch
                    if (batches.isNotEmpty()) {
                        var expandedBatch by remember { mutableStateOf(false) }
                        val selectedBatch = batches.find { it.id == selectedBatchId }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedBatch?.let { "Lot : ${it.batchCode}" } ?: "Aucun lot associé",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Associer à un lot d'engraissement (optionnel)") },
                                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth().clickable { expandedBatch = true },
                                shape = RoundedCornerShape(12.dp)
                            )
                            DropdownMenu(
                                expanded = expandedBatch,
                                onDismissRequest = { expandedBatch = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Aucun lot") },
                                    onClick = {
                                        selectedBatchId = null
                                        expandedBatch = false
                                    }
                                )
                                batches.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text("${b.batchCode} (${b.currentCount} têtes)") },
                                        onClick = {
                                            selectedBatchId = b.id
                                            expandedBatch = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            viewModel.addFinancialTransaction(
                                FinancialTransaction(
                                    date = System.currentTimeMillis(),
                                    type = txType,
                                    category = category,
                                    amountFcfa = amount,
                                    description = description.ifBlank {
                                        if (txType == "INCOME") "Recette $category" else "Dépense $category"
                                    },
                                    relatedBatchId = selectedBatchId,
                                    relatedRabbitId = selectedRabbitId
                                )
                            )
                            showAddDialog = false
                        }
                    },
                    enabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0
                ) {
                    Text("Valider", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
