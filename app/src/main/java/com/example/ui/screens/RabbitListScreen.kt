package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.Rabbit
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.ui.platform.LocalContext
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import com.example.viewmodels.FarmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RabbitListScreen(navController: NavController, viewModel: FarmViewModel) {
    val rabbits by viewModel.rabbits.collectAsState()
    val matingRecords by viewModel.matingRecords.collectAsState()
    val treatmentRecords by viewModel.treatmentRecords.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterChip by remember { mutableStateOf("Tous") } // "Tous", "Males", "Femelles", "Adultes", "Gestation", "Allaitante", "Jeunes", "Lapereaux"
    var selectedSexFilter by remember { mutableStateOf("Tous") }
    var selectedAgeFilter by remember { mutableStateOf("Tous") }

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

    // Filtered lists
    val isArchiveView = (selectedFilterChip == "Archives")
    val filteredRabbits = rabbits.filter { rabbit ->
        val ageMs = now - rabbit.dateOfBirth
        val ageDays = ageMs / (24 * 60 * 60 * 1000L)
        val ageMonths = ageDays / 30.0

        val matchesChip = when (selectedFilterChip) {
            "Males" -> rabbit.isActive && rabbit.sex == "Male"
            "Femelles" -> rabbit.isActive && rabbit.sex == "Female"
            "Adultes" -> rabbit.isActive && ageMonths >= 5.0
            "Gestation" -> rabbit.isActive && rabbit.sex == "Female" && gestatingFemaleIds.contains(rabbit.id)
            "Allaitante" -> rabbit.isActive && rabbit.sex == "Female" && lactatingFemaleIds.contains(rabbit.id)
            "Jeunes" -> rabbit.isActive && ageMonths >= 2.0 && ageMonths < 5.0
            "Lapereaux" -> rabbit.isActive && ageMonths < 2.0
            "Archives" -> !rabbit.isActive || rabbit.status != "Actif"
            "Tout_Historique" -> true
            else -> rabbit.isActive
        }

        val matchesSex = when (selectedSexFilter) {
            "Male" -> rabbit.sex == "Male"
            "Female" -> rabbit.sex == "Female"
            else -> true
        }

        val matchesAge = when (selectedAgeFilter) {
            "Adultes" -> ageMonths >= 5.0
            "Jeunes" -> ageMonths >= 2.0 && ageMonths < 5.0
            "Lapereaux" -> ageMonths < 2.0
            else -> true
        }

        val matchesSearch = (rabbit.name.contains(searchQuery, ignoreCase = true) ||
         rabbit.breed.contains(searchQuery, ignoreCase = true) ||
         rabbit.cageNumber.contains(searchQuery, ignoreCase = true) ||
         rabbit.status.contains(searchQuery, ignoreCase = true) ||
         "Cage ${rabbit.cageNumber}".contains(searchQuery, ignoreCase = true) ||
         rabbit.id.toString() == searchQuery.trim().removePrefix("#"))

        matchesChip && matchesSex && matchesAge && matchesSearch
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
            if (delta > 15) {
                isBarsVisible = false
            } else if (delta < -15) {
                isBarsVisible = true
            }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = isBarsVisible,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut()
            ) {
                TopAppBar(
                    title = { Text("Mes Lapins Élite (${filteredRabbits.size})", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val csvUri = viewModel.exportRabbitsCsv(context)
                                if (csvUri != null) {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_STREAM, csvUri)
                                        type = "text/csv"
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Export Tableur Lapins (CSV)")
                                    context.startActivity(shareIntent)
                                } else {
                                    android.widget.Toast.makeText(context, "Échec de l'exportation CSV", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.TableChart, contentDescription = "Exporter en CSV / Tableur", tint = MaterialTheme.colorScheme.primary)
                        }

                        if (searchQuery.isNotEmpty() || selectedFilterChip != "Tous" || selectedSexFilter != "Tous" || selectedAgeFilter != "Tous") {
                            IconButton(onClick = {
                                searchQuery = ""
                                selectedFilterChip = "Tous"
                                selectedSexFilter = "Tous"
                                selectedAgeFilter = "Tous"
                            }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Réinitialiser", tint = MaterialTheme.colorScheme.error)
                            }
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
            // Instant search bar on top
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 1.dp
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Recherche rapide (Nom, N° Cage, Race...)", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Effacer", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("search_field_rabbit_list"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            val filterChips = remember(rabbits, gestatingFemaleIds, lactatingFemaleIds, now) {
                val activeCount = rabbits.count { it.isActive }
                val matureCount = rabbits.count { it.isActive && (now - it.dateOfBirth) >= 150L * 24 * 3600 * 1000L }
                val archiveCount = rabbits.count { !it.isActive || it.status != "Actif" }
                val youngCount = rabbits.count { it.isActive && (now - it.dateOfBirth) in (60L * 24 * 3600 * 1000L until 150L * 24 * 3600 * 1000L) }
                val babyCount = rabbits.count { it.isActive && (now - it.dateOfBirth) < 60L * 24 * 3600 * 1000L }
                listOf(
                    "Tous" to "Actifs 🟢 ($activeCount)",
                    "Adultes" to "Matures 🏆 ($matureCount)",
                    "Males" to "Mâles ♂️ (${rabbits.count { it.isActive && it.sex == "Male" }})",
                    "Femelles" to "Femelles ♀️ (${rabbits.count { it.isActive && it.sex == "Female" }})",
                    "Gestation" to "En gestation 🤰 (${rabbits.count { it.isActive && it.sex == "Female" && gestatingFemaleIds.contains(it.id) }})",
                    "Allaitante" to "Allaitantes 🍼 (${rabbits.count { it.isActive && it.sex == "Female" && lactatingFemaleIds.contains(it.id) }})",
                    "Jeunes" to "Jeunes 🌱 ($youngCount)",
                    "Lapereaux" to "Lapereaux 🐣 ($babyCount)",
                    "Archives" to "Sortis / Archives 📁 ($archiveCount)"
                )
            }

            // Quick Horizontal Filter Chips Row
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterChips, key = { it.first }) { chip ->
                    val key = chip.first
                    val label = chip.second
                    val isSelected = selectedFilterChip == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilterChip = key },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            // Modern Bottom Sheet for Search & Filters (closes view clutter on-screen)
            if (isFilterSheetOpen) {
                ModalBottomSheet(
                    onDismissRequest = { isFilterSheetOpen = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recherche & Filtres Avancés 🐰",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (selectedSexFilter != "Tous" || selectedAgeFilter != "Tous" || searchQuery.isNotEmpty()) {
                                Text(
                                    text = "Réinitialiser tout 🔄",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable {
                                        selectedSexFilter = "Tous"
                                        selectedAgeFilter = "Tous"
                                        searchQuery = ""
                                    }
                                )
                            }
                        }

                        // Search Input Textfield
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Nom, numéro de cage, race...") },
                            label = { Text("Rechercher un lapin") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Text("✕", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_field_sheet"),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )

                        // Sex Category Picker Title
                        Text(
                            text = "Filtrer par Sexe",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Sex Selectors Segmented buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val filters = listOf("Tous", "Male", "Female")
                            val labels = listOf("Tous", "Mâles ♂️", "Femelles ♀️")

                            filters.forEachIndexed { index, filter ->
                                val isSelected = selectedSexFilter == filter
                                val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(containerColor)
                                        .clickable { selectedSexFilter = filter }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = labels[index],
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = textColor
                                    )
                                }
                            }
                        }

                        // Age Category Picker Title
                        Text(
                            text = "Catégorie d'Âge",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Age Category Grid selector Layout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val ageOptions = listOf("Tous" to "Tout", "Adultes" to "🏆 Adultes", "Jeunes" to "🌱 Jeunes", "Lapereaux" to "🐣 Lap.")
                            ageOptions.forEach { (filterVal, label) ->
                                val isSelected = selectedAgeFilter == filterVal
                                val containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                val textColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(containerColor)
                                        .clickable { selectedAgeFilter = filterVal }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = textColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Large beautiful Apply Button
                        Button(
                            onClick = { isFilterSheetOpen = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Text("Appliquer & Fermer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Quick Active Filters Status info chip bar
            if (selectedSexFilter != "Tous" || selectedAgeFilter != "Tous" || searchQuery.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sexLabel = if (selectedSexFilter == "Male") "Mâles ♂️" else if (selectedSexFilter == "Female") "Femelles ♀️" else ""
                    val ageLabel = if (selectedAgeFilter != "Tous") "Catégorie $selectedAgeFilter" else ""
                    val searchLabel = if (searchQuery.isNotEmpty()) "\"$searchQuery\"" else ""
                    
                    val activeDesc = listOf(sexLabel, ageLabel, searchLabel).filter { it.isNotEmpty() }.joinToString(" + ")
                    
                    Text(
                        text = "Filtre: $activeDesc",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            selectedSexFilter = "Tous"
                            selectedAgeFilter = "Tous"
                            searchQuery = ""
                        }
                    ) {
                        Text(
                            text = "Effacer ✕",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (filteredRabbits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "Aucun lapin trouvé !",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Commencez par ajouter un lapin reproducteur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { navController.navigate(Screen.AddRabbit.route) },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Ajouter un lapin")
                        }
                    }
                }
            } else {
                val ageOrder = if (isArchiveView) {
                    listOf(
                        "Vendus 💰",
                        "Décédés / Morts 🕊️",
                        "Abattus / Réformés 🥩",
                        "Externes 🌐",
                        "Autres Archives 📁"
                    )
                } else {
                    listOf(
                        "Adultes Matures (≥ 5 mois) 🏆",
                        "Jeunes en croissance (2 à 5 mois) 🌱",
                        "Lapereaux au nid (< 2 mois) 🐣"
                    )
                }

                val groupedRabbits = filteredRabbits.groupBy { rabbit ->
                    if (isArchiveView) {
                        when (rabbit.status) {
                            "Vendu" -> "Vendus 💰"
                            "Mort" -> "Décédés / Morts 🕊️"
                            "Abattu" -> "Abattus / Réformés 🥩"
                            "Externe" -> "Externes 🌐"
                            else -> "Autres Archives 📁"
                        }
                    } else {
                        val ageMs = System.currentTimeMillis() - rabbit.dateOfBirth
                        val ageDays = ageMs / (24 * 60 * 60 * 1000L)
                        val ageMonths = ageDays / 30.4
                        when {
                            ageMonths >= 5.0 -> "Adultes Matures (≥ 5 mois) 🏆"
                            ageMonths >= 2.0 -> "Jeunes en croissance (2 à 5 mois) 🌱"
                            else -> "Lapereaux au nid (< 2 mois) 🐣"
                        }
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ageOrder.forEach { category ->
                        val listInCategory = groupedRabbits[category] ?: emptyList()
                        if (listInCategory.isNotEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "$category (${listInCategory.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            
                            items(listInCategory) { rabbit ->
                                val mating = matingRecords.find { it.femaleId == rabbit.id && it.status in listOf("Accouplé", "Gestation confirmée") && it.expectedBirthDate > now }
                                val isUnderWithdrawal = treatmentRecords.any { it.rabbitId == rabbit.id && it.isUnderWithdrawal(now) }
                                val statusBadge = if (isUnderWithdrawal) {
                                    "⛔ Délai Sanitaire"
                                } else if (!rabbit.isActive || rabbit.status != "Actif") {
                                    when (rabbit.status) {
                                        "Vendu" -> "Vendu 💰"
                                        "Mort" -> "Décédé 🕊️"
                                        "Abattu" -> "Abattu 🥩"
                                        "Externe" -> "Externe 🌐"
                                        "Donné" -> "Donné 🎁"
                                        else -> "Inactif 📁"
                                    }
                                } else if (mating != null) {
                                    val dayOfGestation = ((now - mating.matingDate) / (24 * 3600 * 1000L)).toInt()
                                    "Gestation J+$dayOfGestation 🤰"
                                } else if (lactatingFemaleIds.contains(rabbit.id)) {
                                    "Allaitante 🍼"
                                } else null

                                RabbitCardItem(
                                    rabbit = rabbit,
                                    statusBadge = statusBadge,
                                    onClick = { navController.navigate(Screen.RabbitDetail.createRoute(rabbit.id)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RabbitCardItem(rabbit: Rabbit, statusBadge: String? = null, onClick: () -> Unit) {
    val isMale = rabbit.sex == "Male"
    val (containerColor, contentColor) = getRabbitGenderColors(isMale, rabbit.isActive)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(1.dp, RoundedCornerShape(22.dp))
            .testTag("rabbit_line_${rabbit.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Photo or Sex Indicator Badge
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (rabbit.imageUri != null) {
                    coil.compose.AsyncImage(
                        model = rabbit.imageUri,
                        contentDescription = "Photo de ${rabbit.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isMale) Icons.Rounded.Male else Icons.Rounded.Female,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = rabbit.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Cage Tag
                    if (rabbit.cageNumber.isNotEmpty() && rabbit.isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(contentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Home,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "Cage ${rabbit.cageNumber}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }

                if (statusBadge != null) {
                    Surface(
                        color = if (!rabbit.isActive) MaterialTheme.colorScheme.surface else Color.White.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!rabbit.isActive) MaterialTheme.colorScheme.primary else contentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = rabbit.breed.ifEmpty { "Race n/c" },
                        fontSize = 11.sp,
                        color = contentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(modifier = Modifier.size(3.dp).background(contentColor.copy(alpha = 0.4f), CircleShape))
                    Text(
                        text = "${rabbit.weight} kg",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                    
                    val ageMs = System.currentTimeMillis() - rabbit.dateOfBirth
                    val ageInDays = (ageMs / (24 * 60 * 60 * 1000L)).toInt()
                    val ageInMonths = (ageInDays / 30.4).toInt()
                    val ageString = when {
                        ageInDays < 30 -> "$ageInDays j"
                        ageInMonths < 12 -> "$ageInMonths mois"
                        else -> {
                            val years = ageInMonths / 12
                            val remM = ageInMonths % 12
                            if (remM > 0) "${years}a ${remM}m" else "${years} an${if (years > 1) "s" else ""}"
                        }
                    }
                    Box(modifier = Modifier.size(3.dp).background(contentColor.copy(alpha = 0.4f), CircleShape))
                    Text(
                        text = ageString,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                }
            }

            Icon(
                Icons.Rounded.Pets,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
