package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.Rabbit
import com.example.ui.theme.SoftFemalePink
import com.example.ui.theme.SoftMaleBlue
import com.example.viewmodels.FarmViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRabbitScreen(navController: NavController, viewModel: FarmViewModel) {
    val rabbits by viewModel.rabbits.collectAsState()

    var name by remember { mutableStateOf("") }
    var selectedSex by remember { mutableStateOf("Male") } // "Male", "Female"

    // Date picker state
    var dateOfBirth by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Auto-populate Name and Code based on selected Sex
    LaunchedEffect(selectedSex, rabbits, dateOfBirth) {
        if (name.isBlank() || name.contains("ABV-")) {
            val namesList = if (selectedSex == "Male") com.example.data.RabbitNames.MALE_NAMES else com.example.data.RabbitNames.FEMALE_NAMES
            val takenNames = rabbits.map { it.name.substringBefore(" (").trim().lowercase() }
            val availableNames = namesList.filter { it.lowercase() !in takenNames }
            val selectedBaseName = availableNames.randomOrNull() ?: namesList.random()
            
            val birthCalendar = java.util.Calendar.getInstance().apply { timeInMillis = dateOfBirth }
            val birthYear = birthCalendar.get(java.util.Calendar.YEAR) % 100
            val countOfRabbits = rabbits.size
            val numStr = (countOfRabbits + 1).toString().padStart(3, '0')
            val code = String.format("ABV-%02d-%s", birthYear, numStr)
            
            name = "$selectedBaseName ($code)"
        }
    }
    var breed by remember { mutableStateOf("") }
    var cageNumber by remember { mutableStateOf("") }
    var weightString by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("Actif") }
    var notes by remember { mutableStateOf("") }

    // Image Picker State
    var imageUri by remember { mutableStateOf<String?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                imageUri = uri.toString()
            }
        }
    )

    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap ->
            if (bitmap != null) {
                val savedUri = saveBitmapToCache(context, bitmap)
                if (savedUri != null) {
                    imageUri = savedUri.toString()
                }
            }
        }
    )

    // Filter available parents to map linage (including external or past parents)
    val eligibleFathers = rabbits.filter { it.sex == "Male" }
    val eligibleMothers = rabbits.filter { it.sex == "Female" }

    var selectedFatherId by remember { mutableStateOf<Int?>(null) }
    var selectedMotherId by remember { mutableStateOf<Int?>(null) }

    var expandedFatherDropdown by remember { mutableStateOf(false) }
    var expandedMotherDropdown by remember { mutableStateOf(false) }

    val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mettre au monde / Enregistrer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Card section 1: Identity Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Informations d'Identité",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Photo input and selection section
                    Text(
                        "Photo du lapin",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUri != null) {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = "Aperçu de la photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AddAPhoto,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        cameraLauncher.launch(null)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Appareil photo non disponible", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth().testTag("btn_take_photo"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Prendre une Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    try {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Sélecteur de photos non disponible", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth().testTag("btn_pick_photo"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Collections,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Importer de la Galerie", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (imageUri != null) {
                                Button(
                                    onClick = { imageUri = null },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Retirer la photo", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Rabbit Name Input
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom et Code de l'animal (Généré)") },
                        placeholder = { Text("Généré automatiquement...") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val namesList = if (selectedSex == "Male") com.example.data.RabbitNames.MALE_NAMES else com.example.data.RabbitNames.FEMALE_NAMES
                                    val takenNames = rabbits.map { it.name.substringBefore(" (").trim().lowercase() }
                                    val availableNames = namesList.filter { it.lowercase() !in takenNames }
                                    val selectedBaseName = availableNames.randomOrNull() ?: namesList.random()
                                    
                                    val birthCalendar = java.util.Calendar.getInstance().apply { timeInMillis = dateOfBirth }
                                    val birthYear = birthCalendar.get(java.util.Calendar.YEAR) % 100
                                    val countOfRabbits = rabbits.size
                                    val numStr = (countOfRabbits + 1).toString().padStart(3, '0')
                                    val code = String.format("ABV-%02d-%s", birthYear, numStr)
                                    name = "$selectedBaseName ($code)"
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Générer une autre idée",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_rabbit_name"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    // Gender Selector (Highly visual toggle)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Sexe de l'animal",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // MALE TAB
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedSex == "Male") SoftMaleBlue else Color.Transparent)
                                    .clickable { selectedSex = "Male" }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Mâle ♂️",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedSex == "Male") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // FEMALE TAB
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedSex == "Female") SoftFemalePink else Color.Transparent)
                                    .clickable { selectedSex = "Female" }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Femelle ♀️",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedSex == "Female") Color(0xFFC2185B) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Breed selection Input
                    OutlinedTextField(
                        value = breed,
                        onValueChange = { breed = it },
                        label = { Text("Race de lapin") },
                        placeholder = { Text("Ex: Géant des Flandres, Fauve de Bourgogne...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_rabbit_breed"),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Card Section 2: Farm Presence & Location
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Présence & Localisation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Status selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Statut de l'animal dans l'élevage",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val statuses = listOf(
                            "Actif" to "🟢 Présent à la ferme (Actif)",
                            "Externe" to "🌐 Externe (Géniteur hors ferme)",
                            "Vendu" to "💰 Vendu",
                            "Mort" to "🕊️ Décédé / Mort",
                            "Abattu" to "🥩 Réformé / Abattu",
                            "Donné" to "🎁 Donné"
                        )
                        var expandedStatusDropdown by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            val currentStatusLabel = statuses.find { it.first == selectedStatus }?.second ?: selectedStatus
                            OutlinedTextField(
                                readOnly = true,
                                value = currentStatusLabel,
                                onValueChange = {},
                                label = { Text("Statut") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.rotate(if (expandedStatusDropdown) 180f else 0f)
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
                                shape = RoundedCornerShape(14.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { expandedStatusDropdown = true }
                            )
                            DropdownMenu(
                                expanded = expandedStatusDropdown,
                                onDismissRequest = { expandedStatusDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                statuses.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.second) },
                                        onClick = {
                                            selectedStatus = item.first
                                            expandedStatusDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cage ID Input
                        OutlinedTextField(
                            value = cageNumber,
                            onValueChange = { cageNumber = it },
                            label = { Text("N° de Cage") },
                            placeholder = { Text("Ex: A-12") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_rabbit_cage"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        // Weight Input
                        OutlinedTextField(
                            value = weightString,
                            onValueChange = { weightString = it },
                            label = { Text("Poids (kg)") },
                            placeholder = { Text("Ex: 4.2") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_rabbit_weight"),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    // Date of birth Card button (beautiful picker trigger)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Date de naissance",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatter.format(Date(dateOfBirth)),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Sélectionner la date",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Card Section 3: Lineage & History
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Filiation & Notes (Optionnel)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // FATHER selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val fatherObj = eligibleFathers.find { it.id == selectedFatherId }
                        val selectedFatherName = if (fatherObj != null) {
                            "${fatherObj.name}${if (!fatherObj.isActive) " (${fatherObj.status})" else ""}"
                        } else "Père Inconnu"

                        OutlinedTextField(
                            readOnly = true,
                            value = selectedFatherName,
                            onValueChange = {},
                            label = { Text("Père Biologique") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (expandedFatherDropdown) 180f else 0f)
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
                            shape = RoundedCornerShape(14.dp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expandedFatherDropdown = true }
                        )
                        DropdownMenu(
                            expanded = expandedFatherDropdown,
                            onDismissRequest = { expandedFatherDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Inconnu / Aucun") },
                                onClick = {
                                    selectedFatherId = null
                                    expandedFatherDropdown = false
                                }
                            )
                            eligibleFathers.forEach { father ->
                                DropdownMenuItem(
                                    text = { Text("${father.name} (${father.breed})${if (!father.isActive) " - [${father.status}]" else ""}") },
                                    onClick = {
                                        selectedFatherId = father.id
                                        expandedFatherDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // MOTHER selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val motherObj = eligibleMothers.find { it.id == selectedMotherId }
                        val selectedMotherName = if (motherObj != null) {
                            "${motherObj.name}${if (!motherObj.isActive) " (${motherObj.status})" else ""}"
                        } else "Mère Inconnue"

                        OutlinedTextField(
                            readOnly = true,
                            value = selectedMotherName,
                            onValueChange = {},
                            label = { Text("Mère Biologique") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (expandedMotherDropdown) 180f else 0f)
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
                            shape = RoundedCornerShape(14.dp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expandedMotherDropdown = true }
                        )
                        DropdownMenu(
                            expanded = expandedMotherDropdown,
                            onDismissRequest = { expandedMotherDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Inconnue / Aucune") },
                                onClick = {
                                    selectedMotherId = null
                                    expandedMotherDropdown = false
                                }
                            )
                            eligibleMothers.forEach { mother ->
                                DropdownMenuItem(
                                    text = { Text("${mother.name} (${mother.breed})${if (!mother.isActive) " - [${mother.status}]" else ""}") },
                                    onClick = {
                                        selectedMotherId = mother.id
                                        expandedMotherDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Health and details notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Remarques / Santé") },
                        placeholder = { Text("Ex: Vacciné le 12/04, tempérament calme...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("input_rabbit_notes"),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Save Action Button
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val parsedWeight = weightString.toDoubleOrNull() ?: 0.0
                        val isRabbitActive = (selectedStatus == "Actif")
                        viewModel.addRabbit(
                            Rabbit(
                                name = name.trim(),
                                sex = selectedSex,
                                breed = if (breed.isNotBlank()) breed.trim() else "Race inconnue",
                                cageNumber = cageNumber.trim(),
                                weight = parsedWeight,
                                fatherId = selectedFatherId,
                                motherId = selectedMotherId,
                                notes = notes.trim(),
                                dateOfBirth = dateOfBirth,
                                isActive = isRabbitActive,
                                status = selectedStatus,
                                imageUri = imageUri
                            )
                        )
                        navController.popBackStack()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("btn_save_rabbit"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Enregistrer le Lapin",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Material 3 Date Picker Dialog
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateOfBirth)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dateOfBirth = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                            showDatePicker = false
                        }
                    ) {
                        Text("Confirmer", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Annuler")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

private fun saveBitmapToCache(context: android.content.Context, bitmap: android.graphics.Bitmap): android.net.Uri? {
    return try {
        val imagesPath = java.io.File(context.filesDir, "images")
        if (!imagesPath.exists()) {
            imagesPath.mkdirs()
        }
        val file = java.io.File(imagesPath, "rabbit_photo_${System.currentTimeMillis()}.jpg")
        val stream = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)
        stream.close()
        android.net.Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
