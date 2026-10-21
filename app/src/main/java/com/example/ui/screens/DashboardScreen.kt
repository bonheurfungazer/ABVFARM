package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.Reminder
import com.example.ui.navigation.Screen
import com.example.viewmodels.FarmViewModel
import com.example.utils.FarmNotificationManager
import com.example.ui.components.DataBackupBottomSheet
import com.example.R
import androidx.compose.ui.res.painterResource
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: FarmViewModel) {
    val rabbits by viewModel.rabbits.collectAsState()
    val matingRecords by viewModel.matingRecords.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val treatmentRecords by viewModel.treatmentRecords.collectAsState()
    val dailyFeedLogs by viewModel.dailyFeedLogs.collectAsState()
    val fatteningBatches by viewModel.fatteningBatches.collectAsState()
    val financialTransactions by viewModel.financialTransactions.collectAsState()
    val manualFeedStock by viewModel.manualFeedStock.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemDark
    }

    val isCloudConnected by viewModel.isCloudConnected.collectAsState()
    val currentUserEmail by viewModel.currentUserEmail.collectAsState()
    var showDataBackupSheet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    // Dynamic metrics calculations
    val totalRabbits = rabbits.filter { it.isActive }.size

    val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
    val births7d = rabbits.filter { it.isActive && it.dateOfBirth >= sevenDaysAgo && (System.currentTimeMillis() - it.dateOfBirth < 28L * 24 * 3600 * 1000L) }.size

    // Matures (≥ 5 mois / 150 jours) - Reproducteurs prêts
    val matureRabbits = rabbits.filter {
        it.isActive && (System.currentTimeMillis() - it.dateOfBirth) >= 150L * 24 * 3600 * 1000L
    }
    val matureCount = matureRabbits.size
    val matureMales = matureRabbits.count { it.sex == "Male" }
    val matureFemales = matureRabbits.count { it.sex == "Female" }

    // Archives / Sortis de la ferme (Vendu, Mort, Externe, etc.)
    val archiveRabbitsCount = rabbits.count { !it.isActive || it.status != "Actif" }

    // Calculate real food stock
    val totalPurchasedFeedKg = inventoryItems.filter { it.category == "Nourriture" }.sumOf { item ->
        val u = item.unit.trim().lowercase()
        val name = item.name.lowercase()
        when {
            u.contains("g") && !u.contains("kg") -> item.quantity / 1000.0
            u.contains("50") || name.contains("50kg") || name.contains("50 kg") -> item.quantity * 50.0
            u.contains("25") || name.contains("25kg") || name.contains("25 kg") -> item.quantity * 25.0
            u.contains("sac") -> item.quantity * 25.0
            else -> item.quantity
        }
    }
    val totalDistributedFeedKg = dailyFeedLogs.sumOf { it.quantityGrams } / 1000.0
    val realFoodStockKg = if (manualFeedStock >= 0.0) manualFeedStock else maxOf(0.0, totalPurchasedFeedKg - totalDistributedFeedKg)

    val foodStatus = when {
        realFoodStockKg <= 0.0 -> "Vide ⚠️"
        realFoodStockKg < 10.0 -> String.format(Locale.getDefault(), "Faible (%.1f kg)", realFoodStockKg)
        else -> String.format(Locale.getDefault(), "%.1f kg", realFoodStockKg)
    }
    val isFoodWarning = realFoodStockKg < 10.0

    // Feed computation for 1-click action
    val now = System.currentTimeMillis()
    var countMalesAdult = 0
    var countFemalesNormal = 0
    var countFemalesGestating = 0
    var countFemalesLactating = 0
    var countYoung1 = 0
    var countYoung2 = 0

    rabbits.filter { it.isActive }.forEach { r ->
        val ageDays = ((now - r.dateOfBirth) / (24 * 3600 * 1000L)).toInt()
        if (ageDays < 30) {
            // under 1 month
        } else if (ageDays < 60) {
            countYoung1++
        } else if (ageDays < 120) {
            countYoung2++
        } else {
            if (r.sex == "Male") {
                countMalesAdult++
            } else {
                val hasMiseBasRecent = matingRecords.any { m ->
                    m.femaleId == r.id && m.status == "Mise bas" && (now - m.matingDate < 71L * 24 * 3600 * 1000L)
                }
                val hasRecentMating = matingRecords.any { m ->
                    m.femaleId == r.id && m.expectedBirthDate > now && (m.expectedBirthDate - now < 31L * 24 * 3600 * 1000L)
                }
                if (hasMiseBasRecent) countFemalesLactating++
                else if (hasRecentMating) countFemalesGestating++
                else countFemalesNormal++
            }
        }
    }
    val recGramsToday = countMalesAdult * 120.0 + countFemalesNormal * 120.0 + countFemalesGestating * 180.0 + countFemalesLactating * 280.0 + countYoung1 * 50.0 + countYoung2 * 90.0
    val recKgToday = recGramsToday / 1000.0

    val todayFeedLogs = dailyFeedLogs.filter { log ->
        val logCal = java.util.Calendar.getInstance().apply { timeInMillis = log.feedDate }
        val tCal = java.util.Calendar.getInstance()
        logCal.get(java.util.Calendar.YEAR) == tCal.get(java.util.Calendar.YEAR) &&
        logCal.get(java.util.Calendar.DAY_OF_YEAR) == tCal.get(java.util.Calendar.DAY_OF_YEAR)
    }
    val hasFedToday = todayFeedLogs.isNotEmpty()
    val fedTodayKg = todayFeedLogs.sumOf { it.quantityGrams } / 1000.0

    // Urgent Breeding alerts
    val palpationsDue = matingRecords.filter { m ->
        val daysSinceMating = ((now - m.matingDate) / (24 * 3600 * 1000L)).toInt()
        daysSinceMating in 10..14 && m.status == "Accouplé"
    }

    val nestBoxesDue = matingRecords.filter { m ->
        val daysUntilBirth = ((m.expectedBirthDate - now) / (24 * 3600 * 1000L)).toInt()
        daysUntilBirth in 1..3 && m.status in listOf("Accouplé", "Gestation confirmée")
    }

    val birthsImminent = matingRecords.filter { m ->
        val daysUntilBirth = ((m.expectedBirthDate - now) / (24 * 3600 * 1000L)).toInt()
        daysUntilBirth in 0..1 && m.status != "Mise bas" && m.status != "Échoué"
    }

    // Health / Vaccination reminders due for specific rabbits
    val treatmentsDue = treatmentRecords.filter { t ->
        t.nextDueDate != null && (t.nextDueDate - now <= 2L * 24 * 3600 * 1000L) && (now - t.nextDueDate < 14L * 24 * 3600 * 1000L)
    }

    // Monthly expenses
    val nowCal = java.util.Calendar.getInstance()
    val thisMonthExpenses = inventoryItems.filter { item ->
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = item.purchaseDate }
        cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
                cal.get(java.util.Calendar.MONTH) == nowCal.get(java.util.Calendar.MONTH)
    }.sumOf { it.totalPrice }

    val activeBatches = remember(fatteningBatches) { fatteningBatches.filter { it.status == "ACTIVE" } }
    val totalFatteningKits = remember(activeBatches) { activeBatches.sumOf { it.currentCount } }

    val totalIncome = remember(financialTransactions) { financialTransactions.filter { it.type == "INCOME" }.sumOf { it.amountFcfa } }
    val totalExpense = remember(financialTransactions) { financialTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amountFcfa } }
    val netFinancialBalance = totalIncome - totalExpense

    val rabbitsUnderWithdrawal = remember(treatmentRecords, rabbits) {
        val nowMs = System.currentTimeMillis()
        rabbits.filter { r -> treatmentRecords.any { it.rabbitId == r.id && it.isUnderWithdrawal(nowMs) } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "ABVFarm",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.5).sp),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Mon élevage de lapin serein",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Quick Night/Day theme toggle for early morning rounds
                            IconButton(
                                onClick = { viewModel.toggleDarkMode(isDark) },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = if (isDark) "Activer le mode clair" else "Activer le mode sombre (Basse luminosité)",
                                    tint = if (isDark) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RabbitLogo()
                            
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Menu option",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (themeMode) {
                                                    1 -> "Mode Clair ☀️ (Changer)"
                                                    2 -> "Mode Sombre 🌙 (Changer)"
                                                    else -> "Mode Système 📱 (Changer)"
                                                }
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            val next = when (themeMode) {
                                                0 -> 2 // Go to dark
                                                2 -> 1 // Go to light
                                                1 -> 0 // Go to system
                                                else -> 0
                                            }
                                            viewModel.setThemeMode(next)
                                            val msg = when (next) {
                                                1 -> "Mode Clair activé ☀️"
                                                2 -> "Mode Sombre activé 🌙 (Spécial basse luminosité)"
                                                else -> "Thème du système 📱"
                                            }
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                                contentDescription = null,
                                                tint = if (isDark) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("🔔 Tester Bip & Vibration") },
                                        onClick = {
                                            menuExpanded = false
                                            FarmNotificationManager.triggerTestAlert(context)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.NotificationsActive,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("📅 Calendrier & Tâches") },
                                        onClick = {
                                            menuExpanded = false
                                            navController.navigate(Screen.Reminders.route)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.CalendarToday,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sauvegarde & Export") },
                                        onClick = {
                                            menuExpanded = false
                                            showDataBackupSheet = true
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Storage,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            // Elegant Welcome Header Card with a pleasant background gradient
            item {
                WelcomeCard()
            }

            // Cloud Sync Status Banner
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCloudConnected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isCloudConnected) Color(0xFF81C784) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clickable { showDataBackupSheet = true }
                        .testTag("cloud_sync_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isCloudConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCloudConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isCloudConnected) "Cloud Google Synchronisé" else "Cloud Google Non Connecté",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCloudConnected) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                                )
                                if (isCloudConnected) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFC8E6C9)
                                    ) {
                                        Text(
                                            text = "TEMPS RÉEL",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1B5E20),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isCloudConnected) {
                                    "Compte : ${currentUserEmail ?: "Actif"} • 2 personnes peuvent collaborer à distance"
                                } else {
                                    "Touchez pour connecter Google et synchroniser à plusieurs en temps réel"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCloudConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = if (isCloudConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // High action beautiful horizontal buttons
            item {
                SectionHeader(title = "Navigation de l'Élevage")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    item {
                        ActionTile(
                            title = "Mes Lapins",
                            subtitle = "$totalRabbits enregistrés",
                            icon = Icons.Rounded.Face,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = { navController.navigate(Screen.Rabbits.route) },
                            tag = "nav_rabbits"
                        )
                    }
                    item {
                        ActionTile(
                            title = "Accouplements",
                            subtitle = "${matingRecords.filter { it.status != "Mise bas" && it.status != "Échoué" }.size} actifs",
                            icon = Icons.Rounded.DateRange,
                            tint = MaterialTheme.colorScheme.secondary,
                            onClick = { navController.navigate(Screen.Mating.route) },
                            tag = "nav_mating"
                        )
                    }
                    item {
                        ActionTile(
                            title = "Inventaire",
                            subtitle = "${inventoryItems.size} produits",
                            icon = Icons.Rounded.ShoppingCart,
                            tint = MaterialTheme.colorScheme.tertiary,
                            onClick = { navController.navigate(Screen.Inventory.route) },
                            tag = "nav_inventory"
                        )
                    }
                    item {
                        ActionTile(
                            title = "Rappels",
                            subtitle = "${reminders.filter { !it.isCompleted }.size} tâches",
                            icon = Icons.Rounded.Notifications,
                            tint = Color(0xFFC2410C),
                            onClick = { navController.navigate(Screen.Reminders.route) },
                            tag = "nav_reminders"
                        )
                    }
                }
            }

            // Quick Actions & Today's Focus Card
            item {
                SectionHeader(title = "🎯 Focus & Actions Rapides")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1-Click Feed deduction action
                        if (recKgToday > 0.0) {
                            if (!hasFedToday) {
                                Button(
                                    onClick = {
                                        viewModel.addDailyFeedLog(
                                            com.example.data.DailyFeedLog(
                                                feedDate = System.currentTimeMillis(),
                                                quantityGrams = recGramsToday,
                                                notes = "Distribution auto - Dashboard"
                                            )
                                        )
                                        android.widget.Toast.makeText(
                                            context,
                                            "🥣 Ration du jour (${String.format(Locale.getDefault(), "%.2f kg", recKgToday)}) déduite du stock !",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Déduire la ration du jour (${String.format(Locale.getDefault(), "%.2f kg", recKgToday)})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFE8F5E9),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                        Text(
                                            "Ration du jour distribuée (${String.format(Locale.getDefault(), "%.2f kg", fedTodayKg)}) ✅",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF1B5E20)
                                        )
                                    }
                                }
                            }
                        }

                        // Breeding & Health alerts if any
                        if (rabbitsUnderWithdrawal.isNotEmpty() || palpationsDue.isNotEmpty() || nestBoxesDue.isNotEmpty() || birthsImminent.isNotEmpty() || treatmentsDue.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (rabbitsUnderWithdrawal.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navController.navigate(Screen.Rabbits.route) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onError,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "⛔ Délai Sanitaire en cours (${rabbitsUnderWithdrawal.size} lapins)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    "Sous traitement antibiotique / antiparasitaire. Vente et abattage bloqués.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                palpationsDue.forEach { mating ->
                                    val female = rabbits.find { it.id == mating.femaleId }
                                    val male = rabbits.find { it.id == mating.maleId }
                                    val name = female?.name ?: "Femelle #${mating.femaleId}"
                                    val daysSinceMating = ((now - mating.matingDate) / (24 * 3600 * 1000L)).toInt()
                                    val cardBg = if (isDark) Color(0xFF2E1C0F) else Color(0xFFFFF3E0)
                                    val cardBorder = if (isDark) Color(0xFF7A4215) else Color(0xFFFFB74D).copy(alpha = 0.6f)
                                    val titleColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                                    val descColor = if (isDark) Color(0xFFFFCC80) else Color(0xFFBF360C)
                                    val tagBg = if (isDark) Color(0xFF4A250B) else Color(0xFFFFCC80)
                                    val tagText = if (isDark) Color(0xFFFFE0B2) else Color(0xFFBF360C)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navController.navigate(Screen.RabbitDetail.createRoute(mating.femaleId)) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF9800)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("🩺", fontSize = 18.sp)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        "Palpation (J+$daysSinceMating) : $name",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = titleColor
                                                    )
                                                    if (female?.cageNumber?.isNotBlank() == true) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = tagBg
                                                        ) {
                                                            Text(
                                                                "Cage ${female.cageNumber}",
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = tagText,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    "Saillie le ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(mating.matingDate))}${if (male != null) " avec ${male.name}" else ""} • Touchez pour ouvrir la fiche",
                                                    fontSize = 11.sp,
                                                    color = descColor
                                                )
                                            }
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = titleColor, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                nestBoxesDue.forEach { mating ->
                                    val female = rabbits.find { it.id == mating.femaleId }
                                    val male = rabbits.find { it.id == mating.maleId }
                                    val name = female?.name ?: "Femelle #${mating.femaleId}"
                                    val daysUntilBirth = ((mating.expectedBirthDate - now) / (24 * 3600 * 1000L)).toInt()
                                    val cardBg = if (isDark) Color(0xFF0D2535) else Color(0xFFE1F5FE)
                                    val cardBorder = if (isDark) Color(0xFF14567A) else Color(0xFF81D4FA).copy(alpha = 0.6f)
                                    val titleColor = if (isDark) Color(0xFF81D4FA) else Color(0xFF0277BD)
                                    val descColor = if (isDark) Color(0xFFB3E5FC) else Color(0xFF01579B)
                                    val tagBg = if (isDark) Color(0xFF123E59) else Color(0xFFB3E5FC)
                                    val tagText = if (isDark) Color(0xFFE1F5FE) else Color(0xFF01579B)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navController.navigate(Screen.RabbitDetail.createRoute(mating.femaleId)) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF03A9F4)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("📦", fontSize = 18.sp)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        "Boîte à nid : $name",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = titleColor
                                                    )
                                                    if (female?.cageNumber?.isNotBlank() == true) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = tagBg
                                                        ) {
                                                            Text(
                                                                "Cage ${female.cageNumber}",
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = tagText,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    "Mise bas prévue dans $daysUntilBirth j (${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(mating.expectedBirthDate))}) • Touchez pour ouvrir la fiche",
                                                    fontSize = 11.sp,
                                                    color = descColor
                                                )
                                            }
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = titleColor, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                birthsImminent.forEach { mating ->
                                    val female = rabbits.find { it.id == mating.femaleId }
                                    val name = female?.name ?: "Femelle #${mating.femaleId}"
                                    val cardBg = if (isDark) Color(0xFF331323) else Color(0xFFFCE4EC)
                                    val cardBorder = if (isDark) Color(0xFF7A2451) else Color(0xFFF48FB1).copy(alpha = 0.6f)
                                    val titleColor = if (isDark) Color(0xFFF48FB1) else Color(0xFFC2185B)
                                    val descColor = if (isDark) Color(0xFFF8BBD0) else Color(0xFF880E4F)
                                    val tagBg = if (isDark) Color(0xFF521836) else Color(0xFFF8BBD0)
                                    val tagText = if (isDark) Color(0xFFFCE4EC) else Color(0xFF880E4F)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navController.navigate(Screen.RabbitDetail.createRoute(mating.femaleId)) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFE91E63)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("🐣", fontSize = 18.sp)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        "Mise bas imminente : $name",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = titleColor
                                                    )
                                                    if (female?.cageNumber?.isNotBlank() == true) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = tagBg
                                                        ) {
                                                            Text(
                                                                "Cage ${female.cageNumber}",
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = tagText,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    "Surveiller le nid aujourd'hui • Touchez pour ouvrir la fiche",
                                                    fontSize = 11.sp,
                                                    color = descColor
                                                )
                                            }
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = titleColor, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                treatmentsDue.forEach { treatment ->
                                    val rabbit = rabbits.find { it.id == treatment.rabbitId }
                                    val name = rabbit?.name ?: "Lapin #${treatment.rabbitId}"
                                    val cardBg = if (isDark) Color(0xFF271330) else Color(0xFFF3E5F5)
                                    val cardBorder = if (isDark) Color(0xFF6B2782) else Color(0xFFCE93D8).copy(alpha = 0.6f)
                                    val titleColor = if (isDark) Color(0xFFCE93D8) else Color(0xFF7B1FA2)
                                    val descColor = if (isDark) Color(0xFFE1BEE7) else Color(0xFF4A148C)
                                    val tagBg = if (isDark) Color(0xFF431B54) else Color(0xFFE1BEE7)
                                    val tagText = if (isDark) Color(0xFFF3E5F5) else Color(0xFF4A148C)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navController.navigate(Screen.RabbitDetail.createRoute(treatment.rabbitId)) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF9C27B0)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("💉", fontSize = 18.sp)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        "Soin / Vaccin : $name",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = titleColor
                                                    )
                                                    if (rabbit?.cageNumber?.isNotBlank() == true) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = tagBg
                                                        ) {
                                                            Text(
                                                                "Cage ${rabbit.cageNumber}",
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = tagText,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    "${treatment.type} : ${treatment.designation}${if (treatment.dosage.isNotBlank()) " (${treatment.dosage})" else ""} • Touchez pour ouvrir la fiche",
                                                    fontSize = 11.sp,
                                                    color = descColor
                                                )
                                            }
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = titleColor, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Direct quick buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.AddRabbit.route) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Lapin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.Mating.route) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Saillie", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.Inventory.route) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Stat Cards Layout - Aesthetic, modern grid
            item {
                SectionHeader(title = "Indicateurs Clés")
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Lapins Présents",
                            value = "$totalRabbits",
                            subtitle = "${rabbits.count { it.isActive && it.sex == "Male" }} ♂ • ${rabbits.count { it.isActive && it.sex == "Female" }} ♀",
                            icon = Icons.Rounded.Group,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { navController.navigate(Screen.Rabbits.route) }
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Matures (≥5m)",
                            value = "$matureCount",
                            subtitle = "${matureMales} ♂ • ${matureFemales} ♀",
                            icon = Icons.Rounded.EmojiEvents,
                            color = Color(0xFFD97706),
                            onClick = { navController.navigate(Screen.Rabbits.route) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Naissances (7 j)",
                            value = births7d.toString(),
                            subtitle = "Lapereaux au nid",
                            icon = Icons.Rounded.Cake,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Alimentation",
                            value = foodStatus,
                            subtitle = if (isFoodWarning) "Stock bas !" else "Stock provende",
                            icon = Icons.Rounded.LocalFlorist,
                            color = if (isFoodWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            onClick = { navController.navigate(Screen.Inventory.route) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Dépenses du Mois",
                            value = String.format(Locale.getDefault(), "%,.0f F", thisMonthExpenses),
                            subtitle = "Inventaire & Soins",
                            icon = Icons.Rounded.ShoppingCart,
                            color = MaterialTheme.colorScheme.secondary,
                            onClick = { navController.navigate(Screen.Inventory.route) }
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Sortis / Archives",
                            value = "$archiveRabbitsCount",
                            subtitle = "Vendus, Morts, Ext.",
                            icon = Icons.Rounded.FolderShared,
                            color = Color(0xFF6B7280),
                            onClick = { navController.navigate(Screen.Rabbits.route) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Engraissement",
                            value = "$totalFatteningKits",
                            subtitle = "${activeBatches.size} lots actifs",
                            icon = Icons.Rounded.Groups,
                            color = Color(0xFF0D9488),
                            onClick = { navController.navigate(Screen.FatteningBatches.route) }
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Trésorerie",
                            value = "${if (netFinancialBalance >= 0) "+" else ""}${String.format(Locale.getDefault(), "%,.0f F", netFinancialBalance)}",
                            subtitle = "Recettes & Dépenses",
                            icon = Icons.Rounded.AccountBalanceWallet,
                            color = if (netFinancialBalance >= 0) Color(0xFF166534) else MaterialTheme.colorScheme.error,
                            onClick = { navController.navigate(Screen.Finances.route) }
                        )
                    }
                }
            }

            // Food Warning alert if applicable
            if (isFoodWarning) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        AlertCard(message = "Le stock de provende/nourriture est faible ou vide. Pensez à racheter de la nourriture pour garantir la croissance de vos lapereaux.")
                    }
                }
            }

            // Pending Tasks and Reminders today
            item {
                SectionHeader(
                    title = "Tâches du jour",
                    actionText = "Tout voir",
                    onActionClick = { navController.navigate(Screen.Reminders.route) }
                )
            }

            val pendingReminders = reminders.filter { reminder ->
                if (reminder.isCompleted) return@filter false
                val calItem = java.util.Calendar.getInstance().apply { timeInMillis = reminder.dueDate }
                val calToday = java.util.Calendar.getInstance()
                calItem.get(java.util.Calendar.YEAR) == calToday.get(java.util.Calendar.YEAR) &&
                        calItem.get(java.util.Calendar.DAY_OF_YEAR) == calToday.get(java.util.Calendar.DAY_OF_YEAR)
            }
            if (pendingReminders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                "Toutes les tâches sont accomplies !",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Prenez soin de vos lapins bien installés.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            } else {
                items(pendingReminders) { reminder ->
                    ReminderListItem(
                        reminder = reminder,
                        onCompleteChange = { checked ->
                            viewModel.updateReminder(reminder.copy(isCompleted = checked))
                        }
                    )
                }
            }

        }
    }

    if (showDataBackupSheet) {
        DataBackupBottomSheet(
            viewModel = viewModel,
            onDismissRequest = { showDataBackupSheet = false }
        )
    }
}
}

@Composable
fun RabbitLogo() {
    androidx.compose.foundation.Image(
        painter = painterResource(id = R.drawable.abvnewlogo),
        contentDescription = "ABVFarm Logo",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .size(54.dp)
            .shadow(3.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
fun WelcomeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .shadow(4.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    )
                )
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Statut: Optimal 🌿",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Bonjour Éleveur ! 👋",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    "Suivez les gestations en cours, restez alerté sur le stock d'aliments et gérez la généalogie de vos animaux avec précision.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onActionClick() }
            )
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        modifier = Modifier
            .width(155.dp)
            .height(130.dp)
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
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
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = color.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AlertCard(message: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun ReminderListItem(
    reminder: Reminder,
    onCompleteChange: (Boolean) -> Unit
) {
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val dateText = formatter.format(Date(reminder.dueDate))

    var checked by remember { mutableStateOf(reminder.isCompleted) }

    val priorityColor = when (reminder.priority) {
        "Haute" -> MaterialTheme.colorScheme.error
        "Moyenne" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    onCompleteChange(it)
                },
                modifier = Modifier.testTag("checkbox_${reminder.id}")
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (checked) TextDecoration.LineThrough else null
                    ),
                    color = if (checked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                
                if (reminder.description.isNotEmpty()) {
                    Text(
                        text = reminder.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(priorityColor, CircleShape)
                    )
                    Text(
                        text = "Échéance: $dateText",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}


