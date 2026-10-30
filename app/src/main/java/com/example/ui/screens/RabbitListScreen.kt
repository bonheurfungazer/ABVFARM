package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.data.GenealogyHelper
import com.example.data.Rabbit
import com.example.ui.navigation.Screen
import com.example.ui.theme.getRabbitGenderColors
import com.example.viewmodels.FarmViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

enum class RabbitSortOption(val label: String) {
    NAME_ASC("Nom (A-Z)"),
    AGE_DESC("Plus âgé en premier"),
    AGE_ASC("Plus jeune en premier"),
    WEIGHT_DESC("Poids le plus lourd"),
    WEIGHT_ASC("Poids le plus léger"),
    CAGE("Numéro de cage")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RabbitListScreen(navController: NavController, viewModel: FarmViewModel) {
    val rabbits by viewModel.rabbits.collectAsState()
    val matingRecords by viewModel.matingRecords.collectAsState()
    val treatmentRecords by viewModel.treatmentRecords.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Tous") } // "Tous", "Adultes", "Gestation", "Allaitante", "Withdrawal"
    var selectedAgeFilter by remember { mutableStateOf("Tous") } // "Tous", "1m", "2m", "3m", "4m", "5m", "6m_plus"
    var selectedWeightFilter by remember { mutableStateOf("Tous") } // "Tous", "under1", "1to2", "around2", "2to3", "3to4", "over4"
    var selectedSexFilter by remember { mutableStateOf("Tous") } // "Tous", "Male", "Female"
    var isArchiveMode by remember { mutableStateOf(false) } // false = Actifs, true = Sortis/Archives
    var selectedSortOption by remember { mutableStateOf(RabbitSortOption.NAME_ASC) }
    var isFilterSheetOpen by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val gestatingFemaleIds = remember(matingRecords, now) {
        matingRecords.filter { it.status in listOf("Accouplé", "Gestation confirmée") && it.expectedBirthDate > now }
            .map { it.femaleId }.toSet()
    }
    val lactatingFemaleIds = remember(matingRecords, now) {
        matingRecords.filter { it.status == "Mise bas" && (now - it.matingDate < 71L * 24 * 3600 * 1000L) }
            .map { it.femaleId }.toSet()
    }
    val withdrawalRabbitIds = remember(treatmentRecords, now) {
        treatmentRecords.filter { it.isUnderWithdrawal(now) }.map { it.rabbitId }.toSet()
    }
    val activeMatingByFemaleId = remember(matingRecords, now) {
        matingRecords.filter { it.status in listOf("Accouplé", "Gestation confirmée") && it.expectedBirthDate > now }
            .associateBy { it.femaleId }
    }
    val activeWithdrawalEndMap = remember(treatmentRecords, now) {
        treatmentRecords.filter { it.isUnderWithdrawal(now) }
            .groupBy { it.rabbitId }
            .mapValues { (_, records) -> records.mapNotNull { it.effectiveWithdrawalEndTime() }.maxOrNull() }
    }
    val sharedKinshipEngine = remember(rabbits) {
        GenealogyHelper.KinshipEngine(rabbits)
    }

    // Filter & sort
    val filteredRabbits = remember(
        rabbits,
        searchQuery,
        selectedCategoryFilter,
        selectedAgeFilter,
        selectedWeightFilter,
        selectedSexFilter,
        isArchiveMode,
        selectedSortOption,
        gestatingFemaleIds,
        lactatingFemaleIds,
        withdrawalRabbitIds,
        now
    ) {
        val rawQuery = searchQuery.trim().lowercase(Locale.getDefault())

        // Smart Query Parser:
        // 1. Detect if query specifies an age in months (e.g. "2 mois", "2mois", "2m", "3 mois")
        val ageQueryMatch = Regex("""(?:^|\s)(\d+)\s*(?:mois|m)(?:\s|$)""").find(rawQuery)
        val queryTargetMonth = ageQueryMatch?.groupValues?.get(1)?.toIntOrNull()

        // 2. Detect if query specifies a weight in kg (e.g. "2kg", "2 kg", "2.5kg", "2,5 kg", "2kilos")
        val weightQueryMatch = Regex("""(?:^|\s)(\d+(?:[.,]\d+)?)\s*(?:kg|kilos?)(?:\s|$)""").find(rawQuery)
        val queryTargetWeight = weightQueryMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

        rabbits.filter { rabbit ->
            val matchesArchive = if (isArchiveMode) (!rabbit.isActive || rabbit.status != "Actif") else (rabbit.isActive && rabbit.status == "Actif")

            val ageMs = now - rabbit.dateOfBirth
            val ageDays = (ageMs / (24 * 60 * 60 * 1000L)).toInt()
            val ageMonths = ageDays / 30.4

            // Category filter
            val matchesCategory = when (selectedCategoryFilter) {
                "Adultes" -> ageMonths >= 5.0
                "Gestation" -> rabbit.sex == "Female" && gestatingFemaleIds.contains(rabbit.id)
                "Allaitante" -> rabbit.sex == "Female" && lactatingFemaleIds.contains(rabbit.id)
                "Withdrawal" -> withdrawalRabbitIds.contains(rabbit.id)
                else -> true
            }

            // Age filter (Exact month target or range)
            val matchesAge = when (selectedAgeFilter) {
                "1m" -> ageDays in 20..45 // ~ 1 mois
                "2m" -> ageDays in 46..75 // ~ 2 mois
                "3m" -> ageDays in 76..105 // ~ 3 mois
                "4m" -> ageDays in 106..135 // ~ 4 mois
                "5m" -> ageDays in 136..165 // ~ 5 mois
                "6m_plus" -> ageDays >= 166 // ≥ 6 mois
                else -> true
            }

            // Weight filter (Exact weight bracket or ~2kg)
            val matchesWeight = when (selectedWeightFilter) {
                "under1" -> rabbit.weight in 0.05..0.99
                "1to2" -> rabbit.weight in 1.0..1.99
                "around2" -> rabbit.weight in 1.70..2.30 // ~ 2.0 kg
                "2to3" -> rabbit.weight in 2.0..2.99
                "3to4" -> rabbit.weight in 3.0..3.99
                "over4" -> rabbit.weight >= 4.0
                else -> true
            }

            // Sex filter
            val matchesSex = when (selectedSexFilter) {
                "Male" -> rabbit.sex == "Male"
                "Female" -> rabbit.sex == "Female"
                else -> true
            }

            // Smart Search Check
            val matchesSearch = if (rawQuery.isEmpty()) {
                true
            } else if (queryTargetMonth != null) {
                // If user typed "2 mois" or "2m", match rabbits around that month
                val monthFloor = ageMonths.toInt()
                monthFloor == queryTargetMonth || abs(ageMonths - queryTargetMonth) <= 0.4
            } else if (queryTargetWeight != null) {
                // If user typed "2kg" or "2.5kg", match rabbits close to that weight (+/- 0.35 kg)
                abs(rabbit.weight - queryTargetWeight) <= 0.35
            } else {
                rabbit.name.contains(rawQuery, ignoreCase = true) ||
                    rabbit.breed.contains(rawQuery, ignoreCase = true) ||
                    rabbit.cageNumber.contains(rawQuery, ignoreCase = true) ||
                    rabbit.status.contains(rawQuery, ignoreCase = true) ||
                    "cage ${rabbit.cageNumber}".contains(rawQuery, ignoreCase = true) ||
                    rabbit.id.toString() == rawQuery.removePrefix("#")
            }

            matchesArchive && matchesCategory && matchesAge && matchesWeight && matchesSex && matchesSearch
        }.let { list ->
            when (selectedSortOption) {
                RabbitSortOption.NAME_ASC -> list.sortedBy { it.name.lowercase(Locale.getDefault()) }
                RabbitSortOption.AGE_DESC -> list.sortedBy { it.dateOfBirth }
                RabbitSortOption.AGE_ASC -> list.sortedByDescending { it.dateOfBirth }
                RabbitSortOption.WEIGHT_DESC -> list.sortedByDescending { it.weight }
                RabbitSortOption.WEIGHT_ASC -> list.sortedBy { it.weight }
                RabbitSortOption.CAGE -> list.sortedWith(compareBy({ it.cageNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.cageNumber }))
            }
        }
    }

    val lazyListState = rememberLazyListState()
    var isBarsVisible by remember { mutableStateOf(true) }

    val firstVisibleItemIndex = remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val firstVisibleItemScrollOffset = remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    var previousIndex by remember { mutableStateOf(0) }
    var previousScrollOffset by remember { mutableStateOf(0) }

    LaunchedEffect(firstVisibleItemIndex.value, firstVisibleItemScrollOffset.value) {
        val currIndex = firstVisibleItemIndex.value
        val currOffset = firstVisibleItemScrollOffset.value
        if (currIndex > previousIndex) {
            isBarsVisible = false
        } else if (currIndex < previousIndex) {
            isBarsVisible = true
        } else {
            val delta = currOffset - previousScrollOffset
            if (delta > 20) isBarsVisible = false
            else if (delta < -20) isBarsVisible = true
        }
        previousIndex = currIndex
        previousScrollOffset = currOffset
    }

    LaunchedEffect(isBarsVisible) {
        viewModel.setBottomBarVisible(isBarsVisible)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setBottomBarVisible(true)
        }
    }

    val hasActiveCustomFilters = selectedCategoryFilter != "Tous" ||
        selectedAgeFilter != "Tous" ||
        selectedWeightFilter != "Tous" ||
        selectedSexFilter != "Tous" ||
        searchQuery.isNotEmpty() ||
        selectedSortOption != RabbitSortOption.NAME_ASC

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedVisibility(
                visible = isBarsVisible,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Mes Lapins",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            val totalActive = rabbits.count { it.isActive && it.status == "Actif" }
                            val totalArchived = rabbits.count { !it.isActive || it.status != "Actif" }
                            Text(
                                if (isArchiveMode) "$totalArchived archivés" else "$totalActive actifs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val csvUri = viewModel.exportRabbitsCsv(context)
                                    if (csvUri != null) {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_STREAM, csvUri)
                                            type = "text/csv"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Export Tableur Lapins (CSV)")
                                        context.startActivity(shareIntent)
                                    } else {
                                        Toast.makeText(context, "Échec de l'exportation CSV", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Rounded.TableChart,
                                contentDescription = "Exporter en CSV / Tableur",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddRabbit.route) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_rabbit_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter un lapin")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Smart Search Bar (Supports instant text, "2 mois", "2kg", etc.)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 1.dp
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tapez un nom, '2 mois', '2kg', cage...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Effacer",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_field_rabbit_list"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // 2. Quick 1-Tap Filter Shortcuts (No horizontal scrolling, fits cleanly!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Shortcut: 2 mois
                val is2mActive = selectedAgeFilter == "2m"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (is2mActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = if (is2mActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedAgeFilter = if (is2mActive) "Tous" else "2m" }
                ) {
                    Box(modifier = Modifier.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "🌱 2 mois",
                            fontSize = 11.sp,
                            fontWeight = if (is2mActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (is2mActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Shortcut: ~ 2.0 kg
                val is2kgActive = selectedWeightFilter == "around2"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (is2kgActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = if (is2kgActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedWeightFilter = if (is2kgActive) "Tous" else "around2" }
                ) {
                    Box(modifier = Modifier.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "⚖️ ~2 kg",
                            fontSize = 11.sp,
                            fontWeight = if (is2kgActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (is2kgActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Shortcut: 3 mois
                val is3mActive = selectedAgeFilter == "3m"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (is3mActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = if (is3mActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedAgeFilter = if (is3mActive) "Tous" else "3m" }
                ) {
                    Box(modifier = Modifier.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "🐇 3 mois",
                            fontSize = 11.sp,
                            fontWeight = if (is3mActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (is3mActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Shortcut: Reproducteurs
                val isReproActive = selectedCategoryFilter == "Adultes"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isReproActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = if (isReproActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedCategoryFilter = if (isReproActive) "Tous" else "Adultes" }
                ) {
                    Box(modifier = Modifier.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "🏆 Matures",
                            fontSize = 11.sp,
                            fontWeight = if (isReproActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isReproActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 3. Compact Multi-Segment Control Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Segment Sexe (58% of width)
                    Row(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("Tous" to "Tous", "Male" to "Mâles ♂", "Female" to "Femelles ♀").forEach { (filterVal, label) ->
                            val isSelected = selectedSexFilter == filterVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .clickable { selectedSexFilter = filterVal }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Segment Actifs / Archives (42% of width)
                    Row(
                        modifier = Modifier
                            .weight(0.9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(false to "Actifs", true to "Sortis 📁").forEach { (isArch, label) ->
                            val isSelected = isArchiveMode == isArch
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .clickable { isArchiveMode = isArch }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Row 2: Advanced Filter & Sort Summary Button
                val ageText = when (selectedAgeFilter) {
                    "1m" -> "1 mois"
                    "2m" -> "2 mois"
                    "3m" -> "3 mois"
                    "4m" -> "4 mois"
                    "5m" -> "5 mois"
                    "6m_plus" -> "≥ 6 mois"
                    else -> null
                }
                val weightText = when (selectedWeightFilter) {
                    "under1" -> "< 1 kg"
                    "1to2" -> "1-2 kg"
                    "around2" -> "~ 2 kg"
                    "2to3" -> "2-3 kg"
                    "3to4" -> "3-4 kg"
                    "over4" -> "≥ 4 kg"
                    else -> null
                }
                val categoryText = when (selectedCategoryFilter) {
                    "Adultes" -> "Reproducteurs"
                    "Gestation" -> "Gestantes 🤰"
                    "Allaitante" -> "Allaitantes 🍼"
                    "Withdrawal" -> "Carence ⛔"
                    else -> null
                }

                val activeFilterSummary = listOfNotNull(categoryText, ageText, weightText).joinToString(" • ").ifEmpty { "Tous critères" }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasActiveCustomFilters) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (hasActiveCustomFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFilterSheetOpen = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "$activeFilterSummary • ${selectedSortOption.label}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Filtres précis",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Active Filters Count and Reset Button
                if (hasActiveCustomFilters) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredRabbits.size} lapin${if (filteredRabbits.size > 1) "s trouvés" else " trouvé"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "✕ Effacer tous les filtres",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable {
                                searchQuery = ""
                                selectedCategoryFilter = "Tous"
                                selectedAgeFilter = "Tous"
                                selectedWeightFilter = "Tous"
                                selectedSexFilter = "Tous"
                                isArchiveMode = false
                                selectedSortOption = RabbitSortOption.NAME_ASC
                            }
                        )
                    }
                }
            }

            // 4. Rabbits List or Empty State
            if (filteredRabbits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (rabbits.isEmpty()) "Aucun lapin dans l'élevage" else "Aucun lapin ne correspond à ces critères",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasActiveCustomFilters) {
                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedCategoryFilter = "Tous"
                                    selectedAgeFilter = "Tous"
                                    selectedWeightFilter = "Tous"
                                    selectedSexFilter = "Tous"
                                    isArchiveMode = false
                                    selectedSortOption = RabbitSortOption.NAME_ASC
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.FilterAltOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Réinitialiser les filtres")
                            }
                        } else if (rabbits.isEmpty()) {
                            Button(
                                onClick = { navController.navigate(Screen.AddRabbit.route) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ajouter mon premier lapin")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredRabbits, key = { it.id }) { rabbit ->
                        val mating = activeMatingByFemaleId[rabbit.id]
                        val withdrawalEndDate = activeWithdrawalEndMap[rabbit.id]
                        val isUnderWithdrawal = withdrawalEndDate != null
                        val isLactating = lactatingFemaleIds.contains(rabbit.id)

                        val inbreedingF = if (rabbit.fatherId != null && rabbit.motherId != null) {
                            (sharedKinshipEngine.F(rabbit.id) * 100.0).coerceIn(0.0, 100.0)
                        } else 0.0

                        RabbitCardItem(
                            rabbit = rabbit,
                            gestatingMating = mating,
                            isLactating = isLactating,
                            isUnderWithdrawal = isUnderWithdrawal,
                            withdrawalEndDate = withdrawalEndDate,
                            inbreedingF = inbreedingF,
                            onClick = { navController.navigate(Screen.RabbitDetail.createRoute(rabbit.id)) }
                        )
                    }
                }
            }
        }
    }

    // 5. Complete Filter & Sort ModalBottomSheet (Age, Weight, Category, Sorting)
    if (isFilterSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isFilterSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Filtres Précis (Âge, Poids, Stade)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Réinitialiser",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable {
                            selectedCategoryFilter = "Tous"
                            selectedAgeFilter = "Tous"
                            selectedWeightFilter = "Tous"
                            selectedSexFilter = "Tous"
                            selectedSortOption = RabbitSortOption.NAME_ASC
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // SECTION 1: Filtre par Âge précis (1m, 2m, 3m, etc.)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "🎂 Âge du lapin",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Tous" to "Tous âges",
                            "1m" to "1 mois",
                            "2m" to "2 mois",
                            "3m" to "3 mois"
                        ).forEach { (key, label) ->
                            val isSelected = selectedAgeFilter == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedAgeFilter = key }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "4m" to "4 mois",
                            "5m" to "5 mois",
                            "6m_plus" to "≥ 6 mois"
                        ).forEach { (key, label) ->
                            val isSelected = selectedAgeFilter == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedAgeFilter = key }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 2: Filtre par Poids précis (~2kg, 1-2kg, 2-3kg, etc.)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "⚖️ Poids du lapin",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Tous" to "Tous poids",
                            "under1" to "< 1.0 kg",
                            "1to2" to "1.0 - 1.9 kg",
                            "around2" to "~ 2.0 kg"
                        ).forEach { (key, label) ->
                            val isSelected = selectedWeightFilter == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedWeightFilter = key }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "2to3" to "2.0 - 2.9 kg",
                            "3to4" to "3.0 - 3.9 kg",
                            "over4" to "≥ 4.0 kg"
                        ).forEach { (key, label) ->
                            val isSelected = selectedWeightFilter == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedWeightFilter = key }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 3: Stade d'élevage
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "🐾 Stade & Santé",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Tous" to "Tous",
                            "Adultes" to "Reproducteurs",
                            "Gestation" to "Gestantes 🤰",
                            "Withdrawal" to "Carence ⛔"
                        ).forEach { (key, label) ->
                            val isSelected = selectedCategoryFilter == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedCategoryFilter = key }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 4: Tri de la liste
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "🔃 Trier la liste par",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RabbitSortOption.values().take(3).forEach { option ->
                            val isSelected = selectedSortOption == option
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSortOption = option }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RabbitSortOption.values().drop(3).forEach { option ->
                            val isSelected = selectedSortOption == option
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSortOption = option }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Apply Button
                Button(
                    onClick = { isFilterSheetOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(
                        text = "Appliquer (${filteredRabbits.size} lapins trouvés)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Modern, uncluttered rabbit card inspired by the web app layout (rcard).
 */
@Composable
fun RabbitCardItem(
    rabbit: Rabbit,
    gestatingMating: com.example.data.MatingRecord? = null,
    isLactating: Boolean = false,
    isUnderWithdrawal: Boolean = false,
    withdrawalEndDate: Long? = null,
    inbreedingF: Double = 0.0,
    onClick: () -> Unit
) {
    val isMale = rabbit.sex == "Male"
    val isFemale = rabbit.sex == "Female"
    val (containerColor, contentColor) = getRabbitGenderColors(isMale, rabbit.isActive)

    val now = System.currentTimeMillis()
    val ageMs = now - rabbit.dateOfBirth
    val ageDays = (ageMs / (24 * 60 * 60 * 1000L)).toInt()
    val ageMonths = (ageDays / 30.4).toInt()
    val isMature = ageMonths >= 5 && rabbit.isActive

    val ageLabel = when {
        ageDays < 30 -> "$ageDays jours"
        ageMonths < 12 -> "$ageMonths mois"
        else -> {
            val years = ageMonths / 12
            val remM = ageMonths % 12
            if (remM > 0) "${years} ans ${remM} m" else "${years} an${if (years > 1) "s" else ""}"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(1.dp, RoundedCornerShape(18.dp))
            .testTag("rabbit_line_${rabbit.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Avatar/Photo + Name/Eyebrow + Cage Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar / Image Thumbnail
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isMale -> Icons.Rounded.Male
                            isFemale -> Icons.Rounded.Female
                            else -> Icons.Rounded.Pets
                        },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Name & Eyebrow
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${if (isMale) "Mâle" else if (isFemale) "Femelle" else "Indéterminé"} · ${rabbit.breed.ifEmpty { "Race n/c" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = rabbit.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Cage Tag
                if (rabbit.cageNumber.isNotEmpty() && rabbit.isActive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = contentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "CAGE ${rabbit.cageNumber}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Facts Row: Age, Weight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Âge",
                        fontSize = 11.sp,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                    Text(
                        text = ageLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Poids",
                        fontSize = 11.sp,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                    Text(
                        text = if (rabbit.weight > 0) String.format(Locale.getDefault(), "%.2f kg", rabbit.weight) else "—",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }

            // Status Chips Row (Reproducer, Gestation, Withdrawal, Inbreeding)
            val hasChips = isMature || gestatingMating != null || isLactating || isUnderWithdrawal || inbreedingF > 0 || !rabbit.isActive
            if (hasChips) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isUnderWithdrawal) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            val daysLeft = if (withdrawalEndDate != null) {
                                maxOf(1L, ((withdrawalEndDate - now) / 86_400_000L) + 1)
                            } else null
                            Text(
                                text = if (daysLeft != null) "⛔ Délai $daysLeft j" else "⛔ Délai attente",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (gestatingMating != null) {
                        val gestDays = ((now - gestatingMating.matingDate) / (24 * 3600 * 1000L)).toInt()
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF6E3DB)
                        ) {
                            Text(
                                text = "Gestante · J+$gestDays 🤰",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8C3E28),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isLactating) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE1F0FA)
                        ) {
                            Text(
                                text = "Allaitante 🍼",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0B629A),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isMature) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE3F1E5)
                        ) {
                            Text(
                                text = "Reproducteur 🏆",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E6B34),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (inbreedingF > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (inbreedingF > 6.25) Color(0xFFFFEDD5) else Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = String.format(Locale.getDefault(), "F = %.1f%%", inbreedingF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (inbreedingF > 6.25) Color(0xFF9A3412) else Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (!rabbit.isActive || rabbit.status != "Actif") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = rabbit.status,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
