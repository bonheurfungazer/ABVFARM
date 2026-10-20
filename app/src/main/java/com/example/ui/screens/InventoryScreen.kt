package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.data.DailyFeedLog
import com.example.data.InventoryItem
import com.example.ui.theme.*
import com.example.viewmodels.FarmViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Helper function to robustly calculate kg from any Inventory item
fun computeItemKg(item: InventoryItem): Double {
    val u = item.unit.trim().lowercase()
    val name = item.name.lowercase()
    return when {
        u.contains("g") && !u.contains("kg") -> item.quantity / 1000.0
        u.contains("50") || name.contains("50kg") || name.contains("50 kg") -> item.quantity * 50.0
        u.contains("25") || name.contains("25kg") || name.contains("25 kg") -> item.quantity * 25.0
        u.contains("sac") -> item.quantity * 25.0
        else -> item.quantity
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(navController: NavController, viewModel: FarmViewModel) {
    val items by viewModel.inventoryItems.collectAsState()
    val rabbits by viewModel.rabbits.collectAsState()
    val dailyFeedLogs by viewModel.dailyFeedLogs.collectAsState()
    val matingRecords by viewModel.matingRecords.collectAsState()
    val manualFeedStock by viewModel.manualFeedStock.collectAsState()

    var activeTab by remember { mutableStateOf("Stocks") } // "Stocks" (Finance/Purchases), "Alimentation" (Feed Rationing Tracker)

    // Purchase Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<InventoryItem?>(null) }
    var purchaseName by remember { mutableStateOf("") }
    var purchaseCategory by remember { mutableStateOf("Nourriture") } // "Nourriture", "Médicament", "Matériel", "Autre"
    var purchaseQtyString by remember { mutableStateOf("") }
    var purchaseUnit by remember { mutableStateOf("kg") }
    var purchaseTotalCostString by remember { mutableStateOf("") }
    var purchaseNotes by remember { mutableStateOf("") }
    var purchaseDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showPurchaseDatePicker by remember { mutableStateOf(false) }

    // Daily Feed log inputs
    var dailyFeedInputGrams by remember { mutableStateOf("") }
    var dailyFeedUnit by remember { mutableStateOf("kg") }
    var dailyFeedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDailyFeedDatePicker by remember { mutableStateOf(false) }
    var distributionSuccessMsg by remember { mutableStateOf<String?>(null) }

    // Expense Filters
    var timeFilterMode by remember { mutableStateOf("Mois") } // "Mois", "Semaine", "Jour", "Tous"
    var periodOffset by remember { mutableIntStateOf(0) }
    var selectedCategoryFilter by remember { mutableStateOf("Tous") }
    val categories = listOf("Nourriture", "Médicament", "Matériel", "Autre")
    var showExpenseDatePicker by remember { mutableStateOf(false) }

    // Calculate time bounds for the active expense period
    val (periodStartMillis, periodEndMillis, periodLabel) = remember(timeFilterMode, periodOffset) {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
        }
        when (timeFilterMode) {
            "Jour" -> {
                cal.add(Calendar.DAY_OF_YEAR, periodOffset)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val end = cal.timeInMillis - 1
                val label = when (periodOffset) {
                    0 -> "Aujourd'hui (${SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(start))})"
                    -1 -> "Hier (${SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(start))})"
                    else -> SimpleDateFormat("EEEE dd MMMM yyyy", Locale.getDefault()).format(Date(start))
                }
                Triple(start, end, label)
            }
            "Semaine" -> {
                cal.add(Calendar.WEEK_OF_YEAR, periodOffset)
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 7)
                val end = cal.timeInMillis - 1
                val startStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(start))
                val endStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(end))
                val label = if (periodOffset == 0) "Cette semaine ($startStr - $endStr)" else "Semaine du $startStr au $endStr"
                Triple(start, end, label)
            }
            "Mois" -> {
                cal.add(Calendar.MONTH, periodOffset)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                val end = cal.timeInMillis - 1
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(start)).replaceFirstChar { it.uppercase() }
                val label = if (periodOffset == 0) "Ce mois-ci ($monthName)" else "Mois de $monthName"
                Triple(start, end, label)
            }
            else -> {
                Triple(0L, Long.MAX_VALUE, "Historique complet de l'élevage")
            }
        }
    }

    // Filter items according to time period
    val periodItems = remember(items, periodStartMillis, periodEndMillis, timeFilterMode) {
        if (timeFilterMode == "Tous") {
            items
        } else {
            items.filter { it.purchaseDate in periodStartMillis..periodEndMillis }
        }
    }

    val totalPeriodExpenses = periodItems.sumOf { it.totalPrice }
    val foodExpenses = periodItems.filter { it.category == "Nourriture" }.sumOf { it.totalPrice }
    val healthExpenses = periodItems.filter { it.category == "Médicament" }.sumOf { it.totalPrice }
    val materialExpenses = periodItems.filter { it.category == "Matériel" }.sumOf { it.totalPrice }
    val otherExpenses = periodItems.filter { it.category == "Autre" }.sumOf { it.totalPrice }

    val displayItems = if (selectedCategoryFilter == "Tous") {
        periodItems
    } else {
        periodItems.filter { it.category == selectedCategoryFilter }
    }

    // ---- Calculations for Feed & Rationing ----
    val activeRabbits = rabbits.filter { it.isActive }
    var countMalesAdult = 0
    var countFemalesGestating = 0
    var countFemalesLactating = 0
    var countFemalesNormal = 0
    var countYoung1 = 0 // 1-2 months
    var countYoung2 = 0 // 2-4 months
    var countBabies = 0 // < 30 days

    activeRabbits.forEach { r ->
        val ageDays = (System.currentTimeMillis() - r.dateOfBirth) / (24 * 3600 * 1000L + 1)
        if (ageDays < 30) {
            countBabies++
        } else if (ageDays <= 75) {
            countYoung1++
        } else if (ageDays < 150) {
            countYoung2++
        } else { // Adult / Mature >= 150 days (5 months)
            if (r.sex == "Male") {
                countMalesAdult++
            } else {
                val hasMiseBasRecent = matingRecords.any { m ->
                    m.femaleId == r.id &&
                            m.status == "Mise bas" &&
                            (System.currentTimeMillis() - m.matingDate < 71 * 24 * 3600 * 1000L)
                }
                val hasRecentMating = matingRecords.any { m ->
                    m.femaleId == r.id &&
                            m.expectedBirthDate > System.currentTimeMillis() &&
                            (m.expectedBirthDate - System.currentTimeMillis() < 31L * 24 * 3600 * 1000L)
                }

                if (hasMiseBasRecent) {
                    countFemalesLactating++
                } else if (hasRecentMating) {
                    countFemalesGestating++
                } else {
                    countFemalesNormal++
                }
            }
        }
    }

    // Nutritional rates (grams/day)
    val rateMale = 120.0
    val rateFemale = 120.0
    val rateGestating = 180.0
    val rateLactating = 280.0
    val rateYoung1 = 50.0  // 1-2 months
    val rateYoung2 = 90.0  // 2-4 months

    val predMales = countMalesAdult * rateMale
    val predFemNormal = countFemalesNormal * rateFemale
    val predFemGestating = countFemalesGestating * rateGestating
    val predFemLactating = countFemalesLactating * rateLactating
    val predYoung1 = countYoung1 * rateYoung1
    val predYoung2 = countYoung2 * rateYoung2

    val totalRecommendedGrams = predMales + predFemNormal + predFemGestating + predFemLactating + predYoung1 + predYoung2
    val totalRecommendedKg = totalRecommendedGrams / 1000.0

    // Feed stock calculations
    val nourritureItems = items.filter { it.category == "Nourriture" }
    val totalPurchasedFeedKg = nourritureItems.sumOf { computeItemKg(it) }
    val totalDistributedFeedKg = dailyFeedLogs.sumOf { it.quantityGrams } / 1000.0
    val currentFeedStockKg = if (manualFeedStock >= 0.0) manualFeedStock else maxOf(0.0, totalPurchasedFeedKg - totalDistributedFeedKg)
    val daysRemaining = if (totalRecommendedKg > 0.0) currentFeedStockKg / totalRecommendedKg else Double.POSITIVE_INFINITY

    // Check if feed was distributed today
    val todayCal = Calendar.getInstance()
    val todayFeedLogs = dailyFeedLogs.filter { log ->
        val logCal = Calendar.getInstance().apply { timeInMillis = log.feedDate }
        logCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                logCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    }
    val distributedTodayKg = todayFeedLogs.sumOf { it.quantityGrams } / 1000.0
    val hasFedToday = todayFeedLogs.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Stocks & Finances 💰", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (activeTab == "Stocks") {
                FloatingActionButton(
                    onClick = {
                        itemToEdit = null
                        purchaseName = ""
                        purchaseCategory = "Nourriture"
                        purchaseQtyString = ""
                        purchaseUnit = "kg"
                        purchaseTotalCostString = ""
                        purchaseNotes = ""
                        purchaseDateMillis = System.currentTimeMillis()
                        showAddDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_inventory_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Enregistrer une dépense")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
            ) {
                // Segmented Tab Switcher (Achats vs Alimentation)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Stocks" to "Dépenses & Achats 💰", "Alimentation" to "Déduction & Rations 🌾").forEach { (key, title) ->
                        val isSelected = activeTab == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { activeTab = key }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (activeTab == "Stocks") {
                    // ---- TAB 1: FINANCIAL EXPENSES & INVENTORY (LAZY VERTICAL GRID) ----
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Time Filter Selector (Mois, Semaine, Jour, Tout)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Mode tabs
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        listOf("Mois" to "Mois", "Semaine" to "Semaine", "Jour" to "Jour", "Tous" to "Tout").forEach { (modeKey, modeTitle) ->
                                            val isModeSelected = timeFilterMode == modeKey
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isModeSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable {
                                                        timeFilterMode = modeKey
                                                        periodOffset = 0
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = modeTitle,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isModeSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // Interactive Period Navigator (< Période >)
                                    if (timeFilterMode != "Tous") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            IconButton(
                                                onClick = { periodOffset -= 1 },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                                    contentDescription = "Période précédente",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Text(
                                                text = periodLabel,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                            )

                                            IconButton(
                                                onClick = { periodOffset += 1 },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                                    contentDescription = "Période suivante",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        if (periodOffset != 0) {
                                            FilledTonalButton(
                                                onClick = { periodOffset = 0 },
                                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Rounded.Today, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Revenir à la période actuelle", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Expenses Summary Card (Cadre vert avec auto-ajustement et anti-débordement)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    // Ligne 1: Titre + Badge d'achats
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Icon(
                                                Icons.Rounded.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.95f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Text(
                                                text = "Total Dépenses",
                                                color = Color.White.copy(alpha = 0.95f),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = Color.White.copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "${periodItems.size} achat${if (periodItems.size > 1) "s" else ""}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                                            )
                                        }
                                    }

                                    // Ligne 2: Libellé de la période avec troncature intelligente
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = periodLabel,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Ligne 3: Grand montant avec redimensionnement automatique adaptatif
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val formattedTotal = String.format(Locale.getDefault(), "%,.0f FCFA", totalPeriodExpenses)
                                    val totalFontSize = when {
                                        formattedTotal.length > 18 -> 19.sp
                                        formattedTotal.length > 14 -> 22.sp
                                        formattedTotal.length > 11 -> 25.sp
                                        else -> 28.sp
                                    }
                                    Text(
                                        text = formattedTotal,
                                        fontSize = totalFontSize,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )

                                    // Répartition par catégorie avec disposition résiliente
                                    if (totalPeriodExpenses > 0) {
                                        val activeCategories = listOfNotNull(
                                            if (foodExpenses > 0) Triple("Aliments", foodExpenses, Icons.Rounded.LocalFlorist) else null,
                                            if (healthExpenses > 0) Triple("Santé", healthExpenses, Icons.Rounded.MedicalServices) else null,
                                            if (materialExpenses > 0) Triple("Matériel", materialExpenses, Icons.Rounded.Home) else null,
                                            if (otherExpenses > 0) Triple("Autre", otherExpenses, Icons.Rounded.ShoppingBag) else null
                                        )

                                        if (activeCategories.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.22f))
                                            Spacer(modifier = Modifier.height(10.dp))

                                            if (activeCategories.size <= 2) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    activeCategories.forEach { (catTitle, catAmount, catIcon) ->
                                                        CategoryPill(
                                                            modifier = Modifier.weight(1f),
                                                            title = catTitle,
                                                            amount = catAmount,
                                                            total = totalPeriodExpenses,
                                                            icon = catIcon
                                                        )
                                                    }
                                                }
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    activeCategories.chunked(2).forEach { rowList ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            rowList.forEach { (catTitle, catAmount, catIcon) ->
                                                                CategoryPill(
                                                                    modifier = Modifier.weight(1f),
                                                                    title = catTitle,
                                                                    amount = catAmount,
                                                                    total = totalPeriodExpenses,
                                                                    icon = catIcon
                                                                )
                                                            }
                                                            if (rowList.size == 1) {
                                                                Spacer(modifier = Modifier.weight(1f))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Cartes d'Insights Dynamiques selon la période (Jour / Semaine / Mois / Tous)
                        if (totalPeriodExpenses > 0) {
                            val topCategoryName = listOf(
                                "Aliments" to foodExpenses,
                                "Santé" to healthExpenses,
                                "Matériel" to materialExpenses,
                                "Autre" to otherExpenses
                            ).maxByOrNull { it.second }?.let { if (it.second > 0) it.first else "-" } ?: "-"

                            when (timeFilterMode) {
                                "Jour" -> {
                                    item {
                                        PeriodInsightMetricCard(
                                            title = "Moyenne / Achat",
                                            value = if (periodItems.isNotEmpty()) String.format(Locale.getDefault(), "%,.0f F", totalPeriodExpenses / periodItems.size) else "0 F",
                                            icon = Icons.Rounded.Calculate,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    item {
                                        PeriodInsightMetricCard(
                                            title = "Poste Principal",
                                            value = topCategoryName,
                                            icon = Icons.Rounded.Star,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                "Semaine" -> {
                                    item {
                                        PeriodInsightMetricCard(
                                            title = "Moyenne / Jour (7j)",
                                            value = String.format(Locale.getDefault(), "%,.0f F/j", totalPeriodExpenses / 7.0),
                                            icon = Icons.Rounded.TrendingUp,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    item {
                                        PeriodInsightMetricCard(
                                            title = "Principal Poste",
                                            value = topCategoryName,
                                            icon = Icons.Rounded.PieChart,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                "Mois" -> {
                                    item {
                                        PeriodInsightMetricCard(
                                            title = "Moyenne / Jour (30j)",
                                            value = String.format(Locale.getDefault(), "%,.0f F/j", totalPeriodExpenses / 30.0),
                                            icon = Icons.Rounded.CalendarMonth,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    item {
                                        PeriodInsightMetricCard(
                                            title = "Catégorie Majeure",
                                            value = topCategoryName,
                                            icon = Icons.Rounded.PieChart,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                "Tous" -> {
                                    item {
                                        PeriodInsightMetricCard(
                                            title = "Coût Moyen / Achat",
                                            value = if (periodItems.isNotEmpty()) String.format(Locale.getDefault(), "%,.0f F", totalPeriodExpenses / periodItems.size) else "0 F",
                                            icon = Icons.Rounded.Assessment,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    item {
                                        PeriodInsightMetricCard(
                                            title = "Poste Majeur Global",
                                            value = topCategoryName,
                                            icon = Icons.Rounded.Insights,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Category Filter selector
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val filterCategories = listOf("Tous") + categories
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                filterCategories.forEach { filterCat ->
                                    val isSelected = selectedCategoryFilter == filterCat
                                    val background = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent
                                    val textColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(background)
                                            .clickable { selectedCategoryFilter = filterCat }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (filterCat == "Nourriture") "Alim." else filterCat,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = textColor
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Section Header for Items
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Détail des achats & dépenses",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${displayItems.size} élément(s)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 6. Items in LazyVerticalGrid
                        if (displayItems.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ShoppingBag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            "Aucune dépense enregistrée sur cette période.",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(
                                            onClick = {
                                                itemToEdit = null
                                                purchaseName = ""
                                                purchaseCategory = "Nourriture"
                                                purchaseQtyString = ""
                                                purchaseUnit = "kg"
                                                purchaseTotalCostString = ""
                                                purchaseNotes = ""
                                                purchaseDateMillis = System.currentTimeMillis()
                                                showAddDialog = true
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Enregistrer un achat")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(displayItems, key = { it.id }) { record ->
                                InventoryExpenseGridCard(
                                    item = record,
                                    onEdit = {
                                        itemToEdit = record
                                        purchaseName = record.name
                                        purchaseCategory = record.category
                                        purchaseQtyString = if (record.quantity % 1.0 == 0.0) record.quantity.toInt().toString() else record.quantity.toString()
                                        purchaseUnit = record.unit
                                        purchaseTotalCostString = if (record.totalPrice % 1.0 == 0.0) record.totalPrice.toInt().toString() else record.totalPrice.toString()
                                        purchaseNotes = record.notes
                                        purchaseDateMillis = record.purchaseDate
                                        showAddDialog = true
                                    },
                                    onDelete = { viewModel.deleteInventoryItem(record) }
                                )
                            }
                        }
                    }
                } else {
                    // ---- TAB 2: ALIMENTATION & DÉDUCTION DE STOCK ----
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Success Toast/Banner if triggered
                        if (distributionSuccessMsg != null) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("✅", fontSize = 18.sp)
                                        Text(
                                            distributionSuccessMsg ?: "",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { distributionSuccessMsg = null }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Card 1: Stock Actuel & Autonomie
                        item {
                            val isCritical = currentFeedStockKg <= 0.0
                            val isWarning = !isCritical && currentFeedStockKg < (totalRecommendedKg * 5.0)

                            val containerCardCol = when {
                                isCritical -> MaterialTheme.colorScheme.errorContainer
                                isWarning -> Color(0xFFFFF7D6)
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            }
                            val onContainerCol = when {
                                isCritical -> MaterialTheme.colorScheme.onErrorContainer
                                isWarning -> Color(0xFF6B5100)
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }

                            val progress = if (totalPurchasedFeedKg > 0.0) {
                                (currentFeedStockKg / totalPurchasedFeedKg).toFloat().coerceIn(0f, 1f)
                            } else 0f

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = containerCardCol)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("📦", fontSize = 20.sp)
                                            Text(
                                                "Stock d'Aliments Disponible",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = onContainerCol
                                            )
                                        }

                                        val percent = (progress * 100).toInt()
                                        Text(
                                            if (manualFeedStock >= 0.0) "Saisie Manuelle" else "$percent% restant",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = onContainerCol,
                                            modifier = Modifier
                                                .background(onContainerCol.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Metrics Row (Acheté, Distribué, Restant)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Total Acheté 🛒", fontSize = 9.5.sp, color = onContainerCol.copy(alpha = 0.75f), maxLines = 1)
                                            Text(String.format(Locale.getDefault(), "%.1f kg", totalPurchasedFeedKg), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onContainerCol, maxLines = 1)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Déduit/Distribué 🥣", fontSize = 9.5.sp, color = onContainerCol.copy(alpha = 0.75f), maxLines = 1)
                                            Text(String.format(Locale.getDefault(), "%.1f kg", totalDistributedFeedKg), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onContainerCol, maxLines = 1)
                                        }
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Text("Réserve Actuelle 📦", fontSize = 9.5.sp, color = onContainerCol.copy(alpha = 0.75f), maxLines = 1)
                                            Text(
                                                String.format(Locale.getDefault(), "%.1f kg", currentFeedStockKg),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 14.sp,
                                                color = if (isCritical) MaterialTheme.colorScheme.error else onContainerCol,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (isCritical) MaterialTheme.colorScheme.error else if (isWarning) Color(0xFFC48E00) else MaterialTheme.colorScheme.primary,
                                        trackColor = onContainerCol.copy(alpha = 0.15f)
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (manualFeedStock >= 0.0) {
                                            "ℹ️ Stock défini en saisie manuelle : ${String.format(Locale.getDefault(), "%.1f kg", manualFeedStock)}"
                                        } else if (totalPurchasedFeedKg > 0.0) {
                                            "ℹ️ Pourcentage calculé sur la quantité déclarée (${String.format(Locale.getDefault(), "%.1f kg", currentFeedStockKg)} / ${String.format(Locale.getDefault(), "%.1f kg", totalPurchasedFeedKg)} achetés = ${(progress * 100).toInt()}%)"
                                        } else {
                                            "ℹ️ Déclarez un achat ou un stock pour initialiser le calcul."
                                        },
                                        fontSize = 10.sp,
                                        color = onContainerCol.copy(alpha = 0.75f)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Autonomy forecast text
                                    if (totalRecommendedKg <= 0.0) {
                                        Text(
                                            "Aucun lapin actif dans votre cheptel pour estimer l'autonomie.",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = onContainerCol
                                        )
                                    } else if (isCritical) {
                                        Text(
                                            "⚠️ Stock épuisé ! Vos ${activeRabbits.size} lapins consomment ${String.format(Locale.getDefault(), "%.2f kg", totalRecommendedKg)}/jour.",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        val days = daysRemaining.toInt()
                                        val exhaustMsg = when {
                                            days == 0 -> "⚠️ Épuisement AUJOURD'HUI même !"
                                            days == 1 -> "⚠️ Épuisement prévu DEMAIN !"
                                            else -> "⏳ Autonomie estimée : environ $days jours."
                                        }
                                        Text(
                                            text = exhaustMsg,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = onContainerCol
                                        )
                                        val exhaustMillis = System.currentTimeMillis() + (daysRemaining * 24L * 3600L * 1000L).toLong()
                                        val formattedDate = SimpleDateFormat("EEEE dd MMMM yyyy", Locale.getDefault()).format(Date(exhaustMillis))
                                        Text(
                                            text = "Réapprovisionner avant le $formattedDate",
                                            fontSize = 11.sp,
                                            color = onContainerCol.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        // Card 2: ⚡ Module de Déduction Quotidienne Rapide (1-Click & Pesée)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "⚡ Déduction Journalière",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (hasFedToday) {
                                            Text(
                                                "Ration donnée aujourd'hui (${String.format(Locale.getDefault(), "%.2f kg", distributedTodayKg)}) ✅",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF155724),
                                                modifier = Modifier
                                                    .background(Color(0xFFD4EDDA), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        "Chaque fois que vous nourrissez vos lapins, déduisez la ration du stock pour garder votre inventaire à jour.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // Quick 1-Click deduction button
                                    Button(
                                        onClick = {
                                            if (totalRecommendedGrams > 0.0) {
                                                viewModel.addDailyFeedLog(
                                                    DailyFeedLog(
                                                        quantityGrams = totalRecommendedGrams,
                                                        feedDate = System.currentTimeMillis(),
                                                        notes = "Ration journalière automatique (${activeRabbits.size} lapins)"
                                                    )
                                                )
                                                distributionSuccessMsg = "Ration du jour (${String.format(Locale.getDefault(), "%.2f kg", totalRecommendedKg)}) déduite du stock !"
                                            }
                                        },
                                        enabled = totalRecommendedGrams > 0.0,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Déduire la ration recommandée du jour (${String.format(Locale.getDefault(), "%.2f kg", totalRecommendedKg)})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                                    // Custom Weighing / Backdated feed distribution
                                    Text(
                                        "Ou enregistrer une pesée spécifique :",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = dailyFeedInputGrams,
                                            onValueChange = { dailyFeedInputGrams = it },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            label = { Text("Quantité") },
                                            placeholder = { Text(if (dailyFeedUnit == "kg") "Ex: 2.5" else "Ex: 2500") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.2f),
                                            shape = RoundedCornerShape(12.dp)
                                        )

                                        // Unit Toggle (kg vs g)
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .padding(2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            listOf("kg", "g").forEach { u ->
                                                val isSelected = dailyFeedUnit == u
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                                        .clickable { dailyFeedUnit = u }
                                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = u,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                val parsed = dailyFeedInputGrams.toDoubleOrNull() ?: 0.0
                                                val inGrams = if (dailyFeedUnit == "kg") parsed * 1000.0 else parsed
                                                if (inGrams > 0.0) {
                                                    viewModel.addDailyFeedLog(
                                                        DailyFeedLog(
                                                            quantityGrams = inGrams,
                                                            feedDate = dailyFeedDateMillis,
                                                            notes = "Pesée manuelle"
                                                        )
                                                    )
                                                    distributionSuccessMsg = "Distribution de ${String.format(Locale.getDefault(), "%.2f kg", inGrams / 1000.0)} déduite du stock !"
                                                    dailyFeedInputGrams = ""
                                                    dailyFeedDateMillis = System.currentTimeMillis()
                                                }
                                            },
                                            enabled = (dailyFeedInputGrams.toDoubleOrNull() ?: 0.0) > 0.0,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Déduire", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }

                                    // Date picker shortcut for distribution
                                    val feedDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(dailyFeedDateMillis))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Date de la distribution : $feedDateStr",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(onClick = { showDailyFeedDatePicker = true }) {
                                            Text("Changer la date", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: Mode de Stock & Calibrage Manuel
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "⚙️ Mode de Calcul du Stock",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val isAuto = manualFeedStock < 0.0
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isAuto) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                .clickable { viewModel.setManualFeedStock(-1.0) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Automatique (Achats - Log)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAuto) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (!isAuto) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                .clickable {
                                                    if (manualFeedStock < 0.0) {
                                                        viewModel.setManualFeedStock(maxOf(0.0, totalPurchasedFeedKg - totalDistributedFeedKg))
                                                    }
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Manuel (Ajustement)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (!isAuto) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (manualFeedStock >= 0.0) {
                                        var manualText by remember(manualFeedStock) { mutableStateOf(manualFeedStock.toString()) }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedTextField(
                                                value = manualText,
                                                onValueChange = { newValue ->
                                                    manualText = newValue
                                                    val parsed = newValue.toDoubleOrNull()
                                                    if (parsed != null && parsed >= 0.0) {
                                                        viewModel.setManualFeedStock(parsed)
                                                    }
                                                },
                                                label = { Text("Stock physique disponible (kg)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            Text("kg", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    } else {
                                        Text(
                                            "💡 En mode Automatique, le stock disponible est calculé exactement en soustrayant vos distributions journalières de vos achats de nourriture.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Card 4: Plan Nutritionnel Prévisionnel
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.Scale, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Text(
                                            "Besoins Prévisionnels du Cheptel (${activeRabbits.size} lapins)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MetricBox(
                                            modifier = Modifier.weight(1f),
                                            title = "PAR JOUR",
                                            value = String.format(Locale.getDefault(), "%.2f kg", totalRecommendedKg)
                                        )
                                        MetricBox(
                                            modifier = Modifier.weight(1f),
                                            title = "PAR SEMAINE",
                                            value = String.format(Locale.getDefault(), "%.1f kg", totalRecommendedKg * 7.0)
                                        )
                                        MetricBox(
                                            modifier = Modifier.weight(1f),
                                            title = "PAR MOIS (30J)",
                                            value = String.format(Locale.getDefault(), "%.1f kg", totalRecommendedKg * 30.0)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    val bags25kg = Math.max(1, Math.ceil((totalRecommendedKg * 30.0) / 25.0).toInt())
                                    Text(
                                        "Approvisionnement conseillé : prévoir environ $bags25kg sac(s) de 25 kg de provende par mois.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        // Card 5: Détail Démographique du Cheptel
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "Détail par Catégorie d'Animaux",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val rows = listOf(
                                        Triple("🌾 Mâles Adultes ($rateMale g/j)", countMalesAdult, predMales),
                                        Triple("🌸 Femelles Repos ($rateFemale g/j)", countFemalesNormal, predFemNormal),
                                        Triple("🤰 Femelles Gestantes ($rateGestating g/j)", countFemalesGestating, predFemGestating),
                                        Triple("🍼 Femelles Allaitantes ($rateLactating g/j)", countFemalesLactating, predFemLactating),
                                        Triple("🐣 Lapereaux Sevrés 1-2m ($rateYoung1 g/j)", countYoung1, predYoung1),
                                        Triple("🐰 Croissance 2-4m ($rateYoung2 g/j)", countYoung2, predYoung2),
                                        Triple("👶 Lapillons non-sevrés (<30j, 0g)", countBabies, 0.0)
                                    )

                                    rows.forEach { (label, count, grams) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("x$count", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                if (grams > 0) {
                                                    Text("(${grams.toInt()}g/j)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    }
                                }
                            }
                        }

                        // Section 6: Historique des Déductions / Pesées
                        item {
                            Text(
                                "Historique des Déductions Alimentaires (${dailyFeedLogs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (dailyFeedLogs.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Aucune distribution enregistrée pour le moment. Cliquez sur le bouton 'Déduire la ration' chaque jour pour tenir votre stock à jour.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(dailyFeedLogs, key = { it.id }) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(log.feedDate)),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = if (log.quantityGrams >= 1000.0)
                                                        String.format(Locale.getDefault(), "%.2f kg", log.quantityGrams / 1000.0)
                                                    else "${log.quantityGrams.toInt()} g",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )

                                                val difference = log.quantityGrams - totalRecommendedGrams
                                                val diffAbs = Math.abs(difference)
                                                val (badgeText, badgeColor, textCol) = when {
                                                    diffAbs < 100.0 -> Triple("Équilibré ✅", Color(0xFFD4EDDA), Color(0xFF155724))
                                                    difference > 0 -> {
                                                        val dispDiff = if (diffAbs >= 1000.0) String.format("%.1fkg", diffAbs / 1000) else "${diffAbs.toInt()}g"
                                                        Triple("Excès (+$dispDiff) ⚠️", Color(0xFFFFF3CD), Color(0xFF856404))
                                                    }
                                                    else -> {
                                                        val dispDiff = if (diffAbs >= 1000.0) String.format("%.1fkg", diffAbs / 1000) else "${diffAbs.toInt()}g"
                                                        Triple("Déficit (-$dispDiff) 📉", Color(0xFFCCE5FF), Color(0xFF004085))
                                                    }
                                                }

                                                Text(
                                                    text = badgeText,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textCol,
                                                    modifier = Modifier
                                                        .background(badgeColor, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteDailyFeedLog(log) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Supprimer",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
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
    }

    // Modal Dialog: Add or Edit Inventory Item / Expense
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    if (itemToEdit != null) "Modifier la dépense" else "Enregistrer un achat",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Item Name
                    OutlinedTextField(
                        value = purchaseName,
                        onValueChange = { purchaseName = it },
                        label = { Text("Désignation de l'article") },
                        placeholder = { Text("Ex: Provende croissance, Vitamines, Paille...") },
                        modifier = Modifier.fillMaxWidth().testTag("inv_input_name"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Category selects
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Catégorie d'achat", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = purchaseCategory == cat
                                val background = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(background)
                                        .clickable {
                                            purchaseCategory = cat
                                            if (cat == "Nourriture" && purchaseUnit == "Unité") {
                                                purchaseUnit = "kg"
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cat, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                            }
                        }
                    }

                    // Quantity and Unit Selection
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = purchaseQtyString,
                            onValueChange = { purchaseQtyString = it },
                            label = { Text("Quantité") },
                            placeholder = { Text("Ex: 50") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("inv_input_qty"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = purchaseUnit,
                            onValueChange = { purchaseUnit = it },
                            label = { Text("Unité") },
                            placeholder = { Text("kg, sac, Litre...") },
                            modifier = Modifier.weight(1f).testTag("inv_input_unit"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    // Unit Presets for Food
                    if (purchaseCategory == "Nourriture") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("kg", "Sac (25 kg)", "Sac (50 kg)", "g").forEach { presetUnit ->
                                val isSelected = purchaseUnit == presetUnit
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { purchaseUnit = presetUnit }
                                ) {
                                    Text(
                                        text = presetUnit,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Total expense in FCFA
                    OutlinedTextField(
                        value = purchaseTotalCostString,
                        onValueChange = { purchaseTotalCostString = it },
                        label = { Text("Montant total payé (FCFA)") },
                        placeholder = { Text("Ex: 15000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("inv_input_price"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Purchase Date Picker
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Date de l'achat :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(purchaseDateMillis))
                            Text(
                                formattedDate,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = { showPurchaseDatePicker = true }) {
                                Text("Changer 📅", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Quick date chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val todayMs = System.currentTimeMillis()
                            val yesterdayMs = todayMs - 24 * 3600 * 1000L
                            val threeDaysAgoMs = todayMs - 3 * 24 * 3600 * 1000L

                            listOf(
                                "Aujourd'hui" to todayMs,
                                "Hier" to yesterdayMs,
                                "Il y a 3j" to threeDaysAgoMs
                            ).forEach { (lbl, ms) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { purchaseDateMillis = ms }
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Notes
                    OutlinedTextField(
                        value = purchaseNotes,
                        onValueChange = { purchaseNotes = it },
                        label = { Text("Notes optionnelles (Fournisseur, lieu...)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedQty = purchaseQtyString.toDoubleOrNull() ?: 1.0
                        val parsedTotalCost = purchaseTotalCostString.toDoubleOrNull() ?: 0.0
                        val parsedPricePerUnit = if (parsedQty > 0) parsedTotalCost / parsedQty else parsedTotalCost
                        if (purchaseName.isNotBlank()) {
                            val existing = itemToEdit
                            if (existing != null) {
                                viewModel.updateInventoryItem(
                                    existing.copy(
                                        name = purchaseName.trim(),
                                        category = purchaseCategory,
                                        quantity = parsedQty,
                                        unit = purchaseUnit.trim(),
                                        pricePerUnit = parsedPricePerUnit,
                                        totalPrice = parsedTotalCost,
                                        purchaseDate = purchaseDateMillis,
                                        notes = purchaseNotes.trim()
                                    )
                                )
                            } else {
                                viewModel.addInventoryItem(
                                    InventoryItem(
                                        name = purchaseName.trim(),
                                        category = purchaseCategory,
                                        quantity = parsedQty,
                                        unit = purchaseUnit.trim(),
                                        pricePerUnit = parsedPricePerUnit,
                                        totalPrice = parsedTotalCost,
                                        purchaseDate = purchaseDateMillis,
                                        notes = purchaseNotes.trim()
                                    )
                                )
                            }
                            showAddDialog = false
                        }
                    },
                    enabled = purchaseName.isNotBlank()
                ) {
                    Text(if (itemToEdit != null) "Mettre à jour" else "Enregistrer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Purchase DatePicker modal
    if (showPurchaseDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = purchaseDateMillis)
        DatePickerDialog(
            onDismissRequest = { showPurchaseDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        purchaseDateMillis = dateState.selectedDateMillis ?: System.currentTimeMillis()
                        showPurchaseDatePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurchaseDatePicker = false }) {
                    Text("Annuler")
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    // Daily Feed DatePicker modal
    if (showDailyFeedDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = dailyFeedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDailyFeedDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dailyFeedDateMillis = dateState.selectedDateMillis ?: System.currentTimeMillis()
                        showDailyFeedDatePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDailyFeedDatePicker = false }) {
                    Text("Annuler")
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
fun CategoryPill(
    modifier: Modifier = Modifier,
    title: String,
    amount: Double,
    total: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val percent = if (total > 0) ((amount / total) * 100).toInt() else 0
    val amountFormatted = String.format(Locale.getDefault(), "%,.0f F", amount)
    val amountFontSize = when {
        amountFormatted.length > 11 -> 9.5.sp
        amountFormatted.length > 8 -> 10.5.sp
        else -> 11.5.sp
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 9.5.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "$percent%",
                        fontSize = 8.5.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = amountFormatted,
                    fontSize = amountFontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PeriodInsightMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun InventoryExpenseGridCard(
    item: InventoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val itemIcon = when (item.category) {
        "Nourriture" -> Icons.Rounded.LocalFlorist
        "Médicament" -> Icons.Rounded.MedicalServices
        "Matériel" -> Icons.Rounded.Home
        else -> Icons.Rounded.ShoppingBag
    }

    val iconColor = when (item.category) {
        "Nourriture" -> MaterialTheme.colorScheme.primary
        "Médicament" -> Terracotta
        "Matériel" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Category Badge + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = itemIcon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = item.category,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formatter.format(Date(item.purchaseDate)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Item Name & Quantity
            Column {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                val unitPriceStr = if (item.pricePerUnit > 0) " • ${String.format(Locale.getDefault(), "%,.0f F/%s", item.pricePerUnit, item.unit)}" else ""
                Text(
                    text = "$qtyStr ${item.unit}$unitPriceStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Footer: Price & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%,.0f FCFA", item.totalPrice),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Modifier",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
fun InventoryItemRow(
    item: InventoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val itemIcon = when (item.category) {
        "Nourriture" -> Icons.Rounded.LocalFlorist
        "Médicament" -> Icons.Rounded.MedicalServices
        "Matériel" -> Icons.Rounded.Home
        else -> Icons.Rounded.ShoppingBag
    }

    val iconColor = when (item.category) {
        "Nourriture" -> MaterialTheme.colorScheme.primary
        "Médicament" -> Terracotta
        "Matériel" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clickable { onEdit() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category circle icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = itemIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Desc Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${if (item.quantity % 1.0 == 0.0) item.quantity.toInt() else item.quantity} ${item.unit} • ${formatter.format(Date(item.purchaseDate))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.notes.isNotEmpty()) {
                    Text(
                        item.notes,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Total Cost display & actions
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.getDefault(), "%,.0f FCFA", item.totalPrice),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Modifier",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
