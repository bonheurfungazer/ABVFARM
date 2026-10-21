package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.FatteningBatch
import com.example.data.FinancialTransaction
import com.example.data.MatingRecord
import com.example.data.Rabbit
import com.example.viewmodels.FarmViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FatteningBatchesScreen(navController: NavController, viewModel: FarmViewModel) {
    val batches by viewModel.fatteningBatches.collectAsState()
    val matings by viewModel.matingRecords.collectAsState()
    val rabbits by viewModel.rabbits.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Lots Actifs, 1: Historique / Clôturés
    var showAddDialog by remember { mutableStateOf(false) }

    // Dialog state for updating weight
    var batchForWeight by remember { mutableStateOf<FatteningBatch?>(null) }
    var weightInput by remember { mutableStateOf("") }

    // Dialog state for recording mortality
    var batchForMortality by remember { mutableStateOf<FatteningBatch?>(null) }
    var mortalityInput by remember { mutableStateOf("1") }

    // Dialog state for recording sale
    var batchForSale by remember { mutableStateOf<FatteningBatch?>(null) }
    var saleCountInput by remember { mutableStateOf("") }
    var salePriceInput by remember { mutableStateOf("") }
    var saleDescInput by remember { mutableStateOf("") }

    val activeBatches = remember(batches) { batches.filter { it.status == "ACTIVE" } }
    val historyBatches = remember(batches) { batches.filter { it.status != "ACTIVE" } }

    val totalActiveKits = activeBatches.sumOf { it.currentCount }
    val totalInitialKits = activeBatches.sumOf { it.initialCount }
    val totalMortalities = activeBatches.sumOf { it.mortalityCount }
    val avgWeight = if (activeBatches.isNotEmpty()) {
        val weightedSum = activeBatches.sumOf { it.averageWeightGrams * it.currentCount }
        if (totalActiveKits > 0) weightedSum / totalActiveKits else 0
    } else 0

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val currencyFormat = remember { NumberFormat.getInstance(Locale.FRENCH) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bandes d'Engraissement", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "$totalActiveKits lapereaux en croissance (${activeBatches.size} lots)",
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
                text = { Text("Nouveau Lot", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_batch_fab")
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // KPI Summary Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("En Engraissement", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "$totalActiveKits",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text("${activeBatches.size} lots actifs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Poids Moyen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            if (avgWeight > 0) "$avgWeight g" else "--",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            if (avgWeight >= 2000) "Prêt pour vente 🎯" else "En croissance 🌱",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Pertes / Morts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        val mortRate = if (totalInitialKits > 0) (totalMortalities.toDouble() / totalInitialKits) * 100.0 else 0.0
                        Text(
                            "$totalMortalities",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            String.format(Locale.getDefault(), "%,.1f%% perte", mortRate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Tabs: Actifs / Historique
            PrimaryTabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Lots Actifs (${activeBatches.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Clôturés / Vendus (${historyBatches.size})", fontWeight = FontWeight.Bold) }
                )
            }

            val displayedBatches = if (activeTab == 0) activeBatches else historyBatches

            if (displayedBatches.isEmpty()) {
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
                            Icons.Rounded.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            if (activeTab == 0) "Aucune bande d'engraissement active" else "Aucun historique de lot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (activeTab == 0) "Créez un nouveau lot après sevrage pour suivre la croissance et les ventes collectives."
                            else "Les lots vendus ou archivés apparaîtront ici.",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedBatches, key = { it.id }) { batch ->
                        BatchCardItem(
                            batch = batch,
                            dateFormatter = dateFormatter,
                            onUpdateWeight = {
                                batchForWeight = batch
                                weightInput = if (batch.averageWeightGrams > 0) batch.averageWeightGrams.toString() else ""
                            },
                            onRecordMortality = {
                                batchForMortality = batch
                                mortalityInput = "1"
                            },
                            onRecordSale = {
                                batchForSale = batch
                                saleCountInput = batch.currentCount.toString()
                                salePriceInput = ""
                                saleDescInput = "Vente lapins chair - Lot ${batch.batchCode}"
                            },
                            onDelete = {
                                viewModel.deleteFatteningBatch(batch)
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog: Create new batch
    if (showAddDialog) {
        AddFatteningBatchDialog(
            matings = matings,
            rabbits = rabbits,
            onDismiss = { showAddDialog = false },
            onConfirm = { newBatch ->
                viewModel.addFatteningBatch(newBatch)
                showAddDialog = false
            }
        )
    }

    // Dialog: Update weight
    if (batchForWeight != null) {
        val b = batchForWeight!!
        AlertDialog(
            onDismissRequest = { batchForWeight = null },
            title = { Text("Pesée du Lot : ${b.batchCode}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Saisissez le nouveau poids moyen du lot en grammes (g) :", fontSize = 13.sp)
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Poids moyen (grammes)") },
                        placeholder = { Text("Ex: 1850") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val w = weightInput.toIntOrNull() ?: 0
                        viewModel.updateFatteningBatch(b.copy(averageWeightGrams = w))
                        batchForWeight = null
                    },
                    enabled = weightInput.isNotBlank()
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { batchForWeight = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialog: Record mortality
    if (batchForMortality != null) {
        val b = batchForMortality!!
        AlertDialog(
            onDismissRequest = { batchForMortality = null },
            title = { Text("Enregistrer une Perte / Décès", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Lot : ${b.batchCode} (actuellement ${b.currentCount} lapins)", fontSize = 13.sp)
                    OutlinedTextField(
                        value = mortalityInput,
                        onValueChange = { mortalityInput = it },
                        label = { Text("Nombre de lapereaux décédés") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lost = mortalityInput.toIntOrNull() ?: 1
                        val newCurrent = maxOf(0, b.currentCount - lost)
                        val newMort = b.mortalityCount + lost
                        val newStatus = if (newCurrent == 0) "SOLD_OUT" else b.status
                        viewModel.updateFatteningBatch(b.copy(currentCount = newCurrent, mortalityCount = newMort, status = newStatus))
                        batchForMortality = null
                    },
                    enabled = (mortalityInput.toIntOrNull() ?: 0) > 0
                ) {
                    Text("Confirmer")
                }
            },
            dismissButton = {
                TextButton(onClick = { batchForMortality = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialog: Record sale
    if (batchForSale != null) {
        val b = batchForSale!!
        AlertDialog(
            onDismissRequest = { batchForSale = null },
            title = { Text("Vente de Lapins de Chair", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Lot : ${b.batchCode} (${b.currentCount} disponibles)", fontSize = 13.sp)

                    OutlinedTextField(
                        value = saleCountInput,
                        onValueChange = { saleCountInput = it },
                        label = { Text("Nombre de lapins vendus") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = salePriceInput,
                        onValueChange = { salePriceInput = it },
                        label = { Text("Montant total perçu (FCFA)") },
                        placeholder = { Text("Ex: 35000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = saleDescInput,
                        onValueChange = { saleDescInput = it },
                        label = { Text("Description / Client") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = saleCountInput.toIntOrNull() ?: 0
                        val price = salePriceInput.toDoubleOrNull() ?: 0.0
                        if (count > 0 && price >= 0) {
                            val newCurrent = maxOf(0, b.currentCount - count)
                            val newStatus = if (newCurrent == 0) "SOLD_OUT" else b.status
                            viewModel.updateFatteningBatch(b.copy(currentCount = newCurrent, status = newStatus))

                            // Create financial transaction
                            viewModel.addFinancialTransaction(
                                FinancialTransaction(
                                    date = System.currentTimeMillis(),
                                    type = "INCOME",
                                    category = "VENTE_CHAIR",
                                    amountFcfa = price,
                                    description = saleDescInput.ifBlank { "Vente de $count lapins de chair (Lot ${b.batchCode})" },
                                    relatedBatchId = b.id
                                )
                            )
                            batchForSale = null
                        }
                    },
                    enabled = (saleCountInput.toIntOrNull() ?: 0) > 0 && salePriceInput.isNotBlank()
                ) {
                    Text("Enregistrer Vente", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { batchForSale = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun BatchCardItem(
    batch: FatteningBatch,
    dateFormatter: SimpleDateFormat,
    onUpdateWeight: () -> Unit,
    onRecordMortality: () -> Unit,
    onRecordSale: () -> Unit,
    onDelete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val daysSinceWeaning = ((now - batch.weaningDate) / (24 * 3600 * 1000L)).coerceAtLeast(0)
    val daysUntilTarget = ((batch.targetSaleDate - now) / (24 * 3600 * 1000L))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Code & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            batch.batchCode,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Emplacement : ${batch.cageLocation.ifBlank { "Non assigné" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (batch.status) {
                        "ACTIVE" -> Color(0xFFDCFCE7)
                        "SOLD_OUT" -> MaterialTheme.colorScheme.surfaceVariant
                        "QUARANTINE" -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = when (batch.status) {
                            "ACTIVE" -> "🟢 Actif"
                            "SOLD_OUT" -> "🏁 Clôturé"
                            "QUARANTINE" -> "⚠️ Quarantaine"
                            else -> batch.status
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (batch.status) {
                            "ACTIVE" -> Color(0xFF166534)
                            "SOLD_OUT" -> MaterialTheme.colorScheme.onSurfaceVariant
                            "QUARANTINE" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Effectif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${batch.currentCount} / ${batch.initialCount} têtes",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (batch.mortalityCount > 0) {
                        Text(
                            "${batch.mortalityCount} morts",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Column {
                    Text("Poids Moyen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (batch.averageWeightGrams > 0) "${batch.averageWeightGrams} g" else "Non pesé",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Âge: J+$daysSinceWeaning", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column {
                    Text("Vente Prévue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        dateFormatter.format(Date(batch.targetSaleDate)),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (daysUntilTarget > 0) "Dans $daysUntilTarget j" else if (daysUntilTarget == 0L) "Aujourd'hui !" else "Dépassée (${-daysUntilTarget}j)",
                        fontSize = 11.sp,
                        color = if (daysUntilTarget <= 0) MaterialTheme.colorScheme.error else Color(0xFF166534)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            if (batch.status == "ACTIVE") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onUpdateWeight,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Scale, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pesée", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onRecordMortality,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.HeartBroken, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Perte", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onRecordSale,
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Sell, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vendre", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun AddFatteningBatchDialog(
    matings: List<MatingRecord>,
    rabbits: List<Rabbit>,
    onDismiss: () -> Unit,
    onConfirm: (FatteningBatch) -> Unit
) {
    val calendar = Calendar.getInstance()
    var batchCode by remember {
        mutableStateOf("LOT-${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${('A'..'Z').random()}")
    }
    var selectedMatingId by remember { mutableStateOf<Int?>(null) }
    var initialCountStr by remember { mutableStateOf("8") }
    var cageLocation by remember { mutableStateOf("Cage Engraissement 1") }
    var avgWeightStr by remember { mutableStateOf("600") }
    var targetDaysStr by remember { mutableStateOf("45") }

    val birthMatings = remember(matings) { matings.filter { it.status == "Mise bas" } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau Lot d'Engraissement", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = batchCode,
                    onValueChange = { batchCode = it },
                    label = { Text("Code du Lot") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Optional Portée d'origine
                if (birthMatings.isNotEmpty()) {
                    var expandedMating by remember { mutableStateOf(false) }
                    val selectedMating = birthMatings.find { it.id == selectedMatingId }
                    val matingLabel = if (selectedMating != null) {
                        val dam = rabbits.find { it.id == selectedMating.femaleId }?.name ?: "Femelle #${selectedMating.femaleId}"
                        "Portée de $dam"
                    } else "Aucune portée spécifique (manuel)"

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = matingLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Portée d'origine") },
                            trailingIcon = {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth().clickable { expandedMating = true },
                            shape = RoundedCornerShape(12.dp)
                        )
                        DropdownMenu(
                            expanded = expandedMating,
                            onDismissRequest = { expandedMating = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Aucune (création indépendante)") },
                                onClick = {
                                    selectedMatingId = null
                                    expandedMating = false
                                }
                            )
                            birthMatings.forEach { m ->
                                val dam = rabbits.find { it.id == m.femaleId }?.name ?: "Femelle #${m.femaleId}"
                                DropdownMenuItem(
                                    text = { Text("Portée de $dam (#${m.id})") },
                                    onClick = {
                                        selectedMatingId = m.id
                                        expandedMating = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = initialCountStr,
                        onValueChange = { initialCountStr = it },
                        label = { Text("Nombre de lapereaux") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = avgWeightStr,
                        onValueChange = { avgWeightStr = it },
                        label = { Text("Poids moyen (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = cageLocation,
                    onValueChange = { cageLocation = it },
                    label = { Text("Cage / Parc d'engraissement") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = targetDaysStr,
                    onValueChange = { targetDaysStr = it },
                    label = { Text("Durée d'engraissement (jours)") },
                    placeholder = { Text("Ex: 45 jours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = initialCountStr.toIntOrNull() ?: 1
                    val weight = avgWeightStr.toIntOrNull() ?: 0
                    val targetDays = targetDaysStr.toLongOrNull() ?: 45L
                    val now = System.currentTimeMillis()
                    val targetDate = now + (targetDays * 24 * 3600 * 1000L)

                    onConfirm(
                        FatteningBatch(
                            batchCode = batchCode.trim(),
                            matingRecordId = selectedMatingId,
                            weaningDate = now,
                            initialCount = count,
                            currentCount = count,
                            mortalityCount = 0,
                            cageLocation = cageLocation.trim(),
                            averageWeightGrams = weight,
                            targetSaleDate = targetDate,
                            status = "ACTIVE"
                        )
                    )
                },
                enabled = batchCode.isNotBlank() && (initialCountStr.toIntOrNull() ?: 0) > 0
            ) {
                Text("Créer le Lot", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
