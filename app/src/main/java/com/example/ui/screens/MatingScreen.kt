package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PregnantWoman
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.navigation.Screen
import com.example.data.MatingRecord
import com.example.data.Rabbit
import com.example.viewmodels.FarmViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatingScreen(navController: NavController, viewModel: FarmViewModel) {
    val rabbits by viewModel.rabbits.collectAsState()
    val matingRecords by viewModel.matingRecords.collectAsState()

    var showPairDialog by remember { mutableStateOf(false) }
    var showDatePickerMating by remember { mutableStateOf(false) }
    var showBirthDialogRecord by remember { mutableStateOf<MatingRecord?>(null) }
    var showBirthDatePicker by remember { mutableStateOf(false) }

    var activeTab by remember { mutableIntStateOf(0) } // 0: En cours & Gestations, 1: Mises bas archivées, 2: Toutes
    var searchQuery by remember { mutableStateOf("") }
    var selectedMonthFilter by remember { mutableStateOf("Tous") }
    var showMonthDropdown by remember { mutableStateOf(false) }

    // Dialog form state
    var selectedMaleId by remember { mutableStateOf<Int?>(null) }
    var selectedFemaleId by remember { mutableStateOf<Int?>(null) }
    var matingDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var blockNotes by remember { mutableStateOf("") }

    var expandedBuck by remember { mutableStateOf(false) }
    var expandedDoe by remember { mutableStateOf(false) }

    val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    // Filter mature bucks and does (active or external breeders)
    val eligibleBucks = rabbits.filter { it.sex == "Male" && (it.isActive || it.status == "Externe") }
    val eligibleDoes = rabbits.filter { it.sex == "Female" && (it.isActive || it.status == "Externe") }

    // Distinct available months for filtering
    val availableMonths = remember(matingRecords) {
        val months = matingRecords.map { record ->
            val cal = Calendar.getInstance().apply { timeInMillis = record.matingDate }
            SimpleDateFormat("MMMM yyyy", Locale.FRENCH).format(cal.time).replaceFirstChar { it.uppercase() }
        }.distinct()
        listOf("Tous") + months
    }

    // Partition matings into Active/Pending vs Completed/Validated Births
    val pendingRecords = remember(matingRecords) { matingRecords.filter { it.status != "Mise bas" } }
    val birthRecords = remember(matingRecords) { matingRecords.filter { it.status == "Mise bas" } }

    val tabRecords = when (activeTab) {
        0 -> pendingRecords
        1 -> birthRecords
        else -> matingRecords
    }

    val displayedRecords = remember(tabRecords, searchQuery, selectedMonthFilter, rabbits) {
        tabRecords.filter { record ->
            val buck = rabbits.find { it.id == record.maleId }
            val doe = rabbits.find { it.id == record.femaleId }
            val matchesSearch = searchQuery.isBlank() ||
                (buck?.name?.contains(searchQuery, ignoreCase = true) == true) ||
                (doe?.name?.contains(searchQuery, ignoreCase = true) == true) ||
                (buck?.cageNumber?.contains(searchQuery, ignoreCase = true) == true) ||
                (doe?.cageNumber?.contains(searchQuery, ignoreCase = true) == true) ||
                record.notes.contains(searchQuery, ignoreCase = true)

            val matchesMonth = if (selectedMonthFilter == "Tous") true else {
                val cal = Calendar.getInstance().apply { timeInMillis = record.matingDate }
                val monthStr = SimpleDateFormat("MMMM yyyy", Locale.FRENCH).format(cal.time).replaceFirstChar { it.uppercase() }
                monthStr == selectedMonthFilter
            }

            matchesSearch && matchesMonth
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Accouplements & Gestations", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    matingDateMs = System.currentTimeMillis()
                    showPairDialog = true 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_mating_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Enregistrer un accouplement")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            if (matingRecords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PregnantWoman,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "Aucun accouplement enregistré",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Enregistrez les accouplements pour planifier le suivi des naissances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                matingDateMs = System.currentTimeMillis()
                                showPairDialog = true 
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Déclarer une saillie")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Segmented Filter Tabs
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val tabs = listOf(
                                    "En cours (${pendingRecords.size})",
                                    "Mises bas (${birthRecords.size})",
                                    "Toutes (${matingRecords.size})"
                                )
                                tabs.forEachIndexed { index, label ->
                                    val isSelected = activeTab == index
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { activeTab = index }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Search & Month Filter Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search Field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Rechercher un lapin, cage...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Filled.Clear, contentDescription = "Effacer", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            // Month Filter Button / Menu
                            Box {
                                OutlinedButton(
                                    onClick = { showMonthDropdown = true },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selectedMonthFilter != "Tous") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedMonthFilter == "Tous") "Période" else selectedMonthFilter,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }

                                DropdownMenu(
                                    expanded = showMonthDropdown,
                                    onDismissRequest = { showMonthDropdown = false }
                                ) {
                                    availableMonths.forEach { month ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = month,
                                                    fontWeight = if (selectedMonthFilter == month) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedMonthFilter == month) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                selectedMonthFilter = month
                                                showMonthDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (displayedRecords.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = when (activeTab) {
                                            0 -> Icons.Rounded.CheckCircle
                                            1 -> Icons.Rounded.History
                                            else -> Icons.Rounded.Info
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = when (activeTab) {
                                            0 -> "Aucune gestation en cours"
                                            1 -> "Aucune mise bas archivée"
                                            else -> "Aucun résultat trouvé"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = when (activeTab) {
                                            0 -> "Toutes les portées ont été validées et sont archivées dans l'onglet 'Mises bas'."
                                            1 -> "Les mises bas déclarées s'afficheront ici avec le récapitulatif des portées."
                                            else -> "Essayez de modifier votre recherche ou vos filtres de période."
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(displayedRecords, key = { it.id }) { record ->
                            val buck = rabbits.find { it.id == record.maleId }
                            val doe = rabbits.find { it.id == record.femaleId }
                            val offspring = rabbits.filter { 
                                it.fatherId == record.maleId && 
                                it.motherId == record.femaleId &&
                                it.dateOfBirth >= record.matingDate &&
                                (it.dateOfBirth - record.matingDate) <= 36L * 24 * 60 * 60 * 1000L
                            }

                            MatingRecordItem(
                                navController = navController,
                                record = record,
                                buck = buck,
                                doe = doe,
                                offspring = offspring,
                                onUpdateStatus = { updatedRecord ->
                                    viewModel.updateMatingRecord(updatedRecord)
                                },
                                onRegisterBirthClick = {
                                    showBirthDialogRecord = record
                                },
                                onDelete = {
                                    viewModel.deleteMatingRecord(record)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Birth Registration Dialog (Offspring count setup & Date selection)
    if (showBirthDialogRecord != null) {
        val record = showBirthDialogRecord!!
        val buckName = rabbits.find { it.id == record.maleId }?.name ?: "mâle inconnu"
        val doeName = rabbits.find { it.id == record.femaleId }?.name ?: "femelle inconnue"
        var offspringCount by remember { mutableIntStateOf(5) } // Standard litter average estimate is 5
        var birthDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

        AlertDialog(
            onDismissRequest = { showBirthDialogRecord = null },
            title = { Text("Déclarer la Mise bas 🎉", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Félicitations pour cette portée de $doeName accouplée avec $buckName !",
                        fontSize = 13.sp
                    )

                    // Date of birth selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Date de la mise bas :",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showBirthDatePicker = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatter.format(Date(birthDateMs)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Choisir la date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "Nombre de lapereaux nés vivants :",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (offspringCount > 1) offspringCount-- },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Text("-", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        
                        Text(
                            text = "$offspringCount",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        
                        IconButton(
                            onClick = { if (offspringCount < 18) offspringCount++ },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                    
                    Text(
                        text = "Note : $offspringCount lapereaux (sexe indéterminé à la naissance) seront enregistrés dans votre cheptel pour le suivi de croissance.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.registerBirth(record, offspringCount, birthDateMs)
                        showBirthDialogRecord = null
                    }
                ) {
                    Text("Enregistrer la Portée 🍼", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBirthDialogRecord = null }) {
                    Text("Annuler")
                }
            }
        )

        if (showBirthDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = birthDateMs)
            DatePickerDialog(
                onDismissRequest = { showBirthDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let {
                                birthDateMs = it
                            }
                            showBirthDatePicker = false
                        }
                    ) {
                        Text("Confirmer", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBirthDatePicker = false }) {
                        Text("Annuler")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    // Pair Declarations Dialog with Date of Mating selector
    if (showPairDialog) {
        val estimatedBirthDate = matingDateMs + 31L * 24 * 60 * 60 * 1000L

        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = { Text("Déclarer une saillie", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Associez les reproducteurs et définissez la date de la saillie.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // BUCK select
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selectedBuckName = eligibleBucks.find { it.id == selectedMaleId }?.name ?: "Sélectionner le Mâle"
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedBuckName,
                            onValueChange = {},
                            label = { Text("Buck (Mâle reproducteur)") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (expandedBuck) 180f else 0f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expandedBuck = true }
                        )
                        DropdownMenu(
                            expanded = expandedBuck,
                            onDismissRequest = { expandedBuck = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            if (eligibleBucks.isEmpty()) {
                                DropdownMenuItem(text = { Text("Aucun Mâle disponible") }, onClick = {})
                            } else {
                                eligibleBucks.forEach { buck ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (buck.imageUri != null) {
                                                    coil.compose.AsyncImage(
                                                        model = buck.imageUri,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp).clip(CircleShape),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(SoftMaleBlue), contentAlignment = Alignment.Center) {
                                                        Text("♂️", fontSize = 10.sp, color = DarkMaleBlue)
                                                    }
                                                }
                                                Column {
                                                    Text(buck.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("Cage ${buck.cageNumber.ifEmpty { "N/A" }} • ${buck.breed}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedMaleId = buck.id
                                            expandedBuck = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // DOE select
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selectedDoeName = eligibleDoes.find { it.id == selectedFemaleId }?.name ?: "Sélectionner la Femelle"
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedDoeName,
                            onValueChange = {},
                            label = { Text("Doe (Femelle reproductrice)") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (expandedDoe) 180f else 0f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expandedDoe = true }
                        )
                        DropdownMenu(
                            expanded = expandedDoe,
                            onDismissRequest = { expandedDoe = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            if (eligibleDoes.isEmpty()) {
                                DropdownMenuItem(text = { Text("Aucune Femelle disponible") }, onClick = {})
                            } else {
                                eligibleDoes.forEach { doe ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (doe.imageUri != null) {
                                                    coil.compose.AsyncImage(
                                                        model = doe.imageUri,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp).clip(CircleShape),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(SoftFemalePink), contentAlignment = Alignment.Center) {
                                                        Text("♀️", fontSize = 10.sp, color = DarkFemalePink)
                                                    }
                                                }
                                                Column {
                                                    Text(doe.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("Cage ${doe.cageNumber.ifEmpty { "N/A" }} • ${doe.breed}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedFemaleId = doe.id
                                            expandedDoe = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Consanguinity alert check
                    val buck = eligibleBucks.find { it.id == selectedMaleId }
                    val doe = eligibleDoes.find { it.id == selectedFemaleId }
                    val consanguinityRate = if (buck != null && doe != null) {
                        com.example.data.GenealogyHelper.calculateConsanguinity(buck.id, doe.id, rabbits)
                    } else 0.0

                    if (buck != null && doe != null) {
                        val isHighRisk = consanguinityRate >= 10.0
                        val cardBg = if (isHighRisk) MaterialTheme.colorScheme.errorContainer else if (consanguinityRate > 0.0) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                        val textColor = if (isHighRisk) MaterialTheme.colorScheme.onErrorContainer else if (consanguinityRate > 0.0) Color(0xFF92400E) else Color(0xFF166534)
                        val iconColor = if (isHighRisk) MaterialTheme.colorScheme.error else if (consanguinityRate > 0.0) Color(0xFFB45309) else Color(0xFF15803D)
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg, contentColor = textColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "Consanguinité",
                                    tint = iconColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isHighRisk) {
                                        String.format(Locale.getDefault(), "🚨 ATTENTION : Risque de consanguinité élevé de %,.1f%% ! Ce croisement est contre-indiqué.", consanguinityRate)
                                    } else if (consanguinityRate > 0.0) {
                                        String.format(Locale.getDefault(), "⚠️ Consanguinité faible de %,.1f%%. À surveiller.", consanguinityRate)
                                    } else {
                                        "✅ Aucun risque de consanguinité (0.0%). Reproducteurs non apparentés."
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Mating Date Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Date de l'accouplement :",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showDatePickerMating = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatter.format(Date(matingDateMs)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Choisir la date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Quick date shortcut chips (horizontal row)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val todayMs = System.currentTimeMillis()
                            val yesterdayMs = todayMs - 24 * 60 * 60 * 1000L
                            val threeDaysAgoMs = todayMs - 3 * 24 * 60 * 60 * 1000L
                            val sevenDaysAgoMs = todayMs - 7 * 24 * 60 * 60 * 1000L

                            FilterChip(
                                selected = Math.abs(matingDateMs - todayMs) < 12 * 60 * 60 * 1000L,
                                onClick = { matingDateMs = todayMs },
                                label = { Text("Aujourd'hui", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            FilterChip(
                                selected = Math.abs(matingDateMs - yesterdayMs) < 12 * 60 * 60 * 1000L,
                                onClick = { matingDateMs = yesterdayMs },
                                label = { Text("Hier", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            FilterChip(
                                selected = Math.abs(matingDateMs - threeDaysAgoMs) < 12 * 60 * 60 * 1000L,
                                onClick = { matingDateMs = threeDaysAgoMs },
                                label = { Text("Il y a 3j", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            FilterChip(
                                selected = Math.abs(matingDateMs - sevenDaysAgoMs) < 12 * 60 * 60 * 1000L,
                                onClick = { matingDateMs = sevenDaysAgoMs },
                                label = { Text("Il y a 7j", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            AssistChip(
                                onClick = { showDatePickerMating = true },
                                label = { Text("📅 Autre date", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        // Real-time Gestation Delivery Preview Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChildCare,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Mise bas estimée : ${formatter.format(Date(estimatedBirthDate))}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Durée de gestation standard : 31 jours",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Notes
                    OutlinedTextField(
                        value = blockNotes,
                        onValueChange = { blockNotes = it },
                        label = { Text("Remarques éventuelles") },
                        placeholder = { Text("Ex: Première saillie de Bella...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val maleId = selectedMaleId
                        val femaleId = selectedFemaleId
                        if (maleId != null && femaleId != null) {
                            // Gestation duration: exactly 31 days
                            val gestationDuration = 31L * 24 * 60 * 60 * 1000
                            val estimatedBirth = matingDateMs + gestationDuration

                            viewModel.addMatingRecord(
                                MatingRecord(
                                    maleId = maleId,
                                    femaleId = femaleId,
                                    matingDate = matingDateMs,
                                    expectedBirthDate = estimatedBirth,
                                    status = "Accouplé",
                                    notes = blockNotes.trim()
                                )
                            )

                            showPairDialog = false
                            selectedMaleId = null
                            selectedFemaleId = null
                            blockNotes = ""
                        }
                    },
                    enabled = selectedMaleId != null && selectedFemaleId != null
                ) {
                    Text("Confirmer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairDialog = false }) {
                    Text("Annuler")
                }
            }
        )

        if (showDatePickerMating) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = matingDateMs)
            DatePickerDialog(
                onDismissRequest = { showDatePickerMating = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let {
                                matingDateMs = it
                            }
                            showDatePickerMating = false
                        }
                    ) {
                        Text("Confirmer", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerMating = false }) {
                        Text("Annuler")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun MatingRecordItem(
    navController: NavController,
    record: MatingRecord,
    buck: Rabbit?,
    doe: Rabbit?,
    offspring: List<Rabbit>,
    onUpdateStatus: (MatingRecord) -> Unit,
    onRegisterBirthClick: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Gestation progress calculation
    val matingTime = record.matingDate
    val expectedTime = record.expectedBirthDate
    val currentTime = System.currentTimeMillis()

    val totalDuration = (expectedTime - matingTime).toFloat()
    val elapsed = (currentTime - matingTime).toFloat()
    val progress = if (totalDuration > 0) (elapsed / totalDuration).coerceIn(0f, 1f) else 0f
    val daysRemaining = (((expectedTime - currentTime) / (24 * 60 * 60 * 1000L)).toInt()).coerceAtLeast(0)

    val currentStatus = record.status

    // Pastel backgrounds based on status
    val statusColor = when (currentStatus) {
        "Mise bas" -> MaterialTheme.colorScheme.primaryContainer
        "Échoué" -> MaterialTheme.colorScheme.errorContainer
        "Gestation confirmée" -> Color(0xFFFDE8E4) // Terracota light
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val themeAccentColor = when (currentStatus) {
        "Mise bas" -> MaterialTheme.colorScheme.primary
        "Échoué" -> MaterialTheme.colorScheme.error
        "Gestation confirmée" -> Terracotta
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Status Badge & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentStatus.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeAccentColor
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Supprimer",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Central layout: Husband & Wife connection representation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Father Card representation
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = buck != null) {
                            buck?.let { navController.navigate(Screen.RabbitDetail.createRoute(it.id)) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (buck?.imageUri != null) {
                        coil.compose.AsyncImage(
                            model = buck.imageUri,
                            contentDescription = "Photo reproducteur",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(SoftMaleBlue), contentAlignment = Alignment.Center) {
                            Text("♂️", fontSize = 16.sp, color = DarkMaleBlue)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        buck?.name ?: "Inconnu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Cage: ${buck?.cageNumber?.ifEmpty { "N/A" } ?: "N/A"}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Visual Heart vector connect
                Icon(
                    imageVector = Icons.Rounded.Pets,
                    contentDescription = null,
                    tint = themeAccentColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                // Mother Card representation
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = doe != null) {
                            doe?.let { navController.navigate(Screen.RabbitDetail.createRoute(it.id)) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (doe?.imageUri != null) {
                        coil.compose.AsyncImage(
                            model = doe.imageUri,
                            contentDescription = "Photo reproductrice",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(SoftFemalePink), contentAlignment = Alignment.Center) {
                            Text("♀️", fontSize = 16.sp, color = DarkFemalePink)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        doe?.name ?: "Inconnu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Cage: ${doe?.cageNumber?.ifEmpty { "N/A" } ?: "N/A"}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Linear Progress gestational tracker if still expecting
            if (currentStatus != "Mise bas" && currentStatus != "Échoué") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Progression de gestation",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (daysRemaining > 0) "$daysRemaining jours restants" else "Mise bas attendue !",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (daysRemaining <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (daysRemaining <= 3) Color(0xFFC2410C) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Timestamps Metadata
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Date de saillie", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatter.format(Date(record.matingDate)), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Accouchement attendu", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatter.format(Date(record.expectedBirthDate)), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Terracotta)
                }
            }

            // Status modifiers action bar to alter records
            if (currentStatus != "Mise bas" && currentStatus != "Échoué") {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Fail Button
                    OutlinedButton(
                        onClick = { onUpdateStatus(record.copy(status = "Échoué")) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.HeartBroken, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Manqué", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Gestation confirmed
                    if (currentStatus == "Accouplé") {
                        Button(
                            onClick = { onUpdateStatus(record.copy(status = "Gestation confirmée")) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            modifier = Modifier.weight(1.1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Confirmée ✅", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Born Successful Button
                    Button(
                        onClick = onRegisterBirthClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.ChildCare, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Accouché 🎉", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (offspring.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "🍼 Portée : Descendance (${offspring.size} Lapereaux)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        offspring.forEach { kit ->
                            val ageDays = (System.currentTimeMillis() - kit.dateOfBirth) / (24 * 60 * 60 * 1000L)
                            val ageStr = if (ageDays < 7) "$ageDays j" else "${ageDays / 7} sem"
                            Card(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable { navController.navigate(Screen.RabbitDetail.createRoute(kit.id)) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = kit.name.replace(Regex("Portée\\s+"), ""),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("Sexe : ${if (kit.sex == "Indéterminé") "Indéterminé 🍼" else kit.sex}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Poids : ${kit.weight} kg", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("Âge : $ageStr", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (record.notes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = record.notes,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

