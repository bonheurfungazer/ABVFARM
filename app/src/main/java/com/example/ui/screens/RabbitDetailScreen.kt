package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.PhotoRecord
import com.example.data.Rabbit
import com.example.data.WeightRecord
import com.example.data.TreatmentRecord
import com.example.data.MatingRecord
import com.example.data.GenealogyHelper
import com.example.ui.components.MatingAndLitterDetailSheet
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import com.example.viewmodels.FarmViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RabbitDetailScreen(navController: NavController, viewModel: FarmViewModel, rabbitId: Int) {
    val rabbits by viewModel.rabbits.collectAsState()
    val subject = rabbits.find { it.id == rabbitId }
    val rabbit = subject ?: return

    val weightRecordsFlow = remember(rabbitId) { viewModel.getWeightForRabbit(rabbitId) }
    val weightRecords by weightRecordsFlow.collectAsState(initial = emptyList())

    val photoRecordsFlow = remember(rabbitId) { viewModel.getPhotoRecordsForRabbit(rabbitId) }
    val photoRecords by photoRecordsFlow.collectAsState(initial = emptyList())

    val treatmentRecordsFlow = remember(rabbitId) { viewModel.getTreatmentsForRabbit(rabbitId) }
    val treatmentRecords by treatmentRecordsFlow.collectAsState(initial = emptyList())

    val matingRecords by viewModel.matingRecords.collectAsState()

    val matingsCount = matingRecords.count { it.maleId == rabbitId || it.femaleId == rabbitId }
    val birthsCount = matingRecords.count { (it.maleId == rabbitId || it.femaleId == rabbitId) && it.status == "Mise bas" }

    val ageMs = System.currentTimeMillis() - rabbit.dateOfBirth
    val ageInDays = (ageMs / (24 * 60 * 60 * 1000L)).toInt()
    val ageInMonths = (ageInDays / 30.4).toInt()
    val ageString = when {
        ageInDays < 30 -> "$ageInDays jours"
        ageInMonths == 1 -> "1 mois (${ageInDays % 30} jours)"
        else -> "$ageInMonths mois (${ageInDays % 30}j)"
    }

    // Interactive image viewing overlay state
    var activeViewerPhotoUri by remember { mutableStateOf<String?>(null) }

    // Direct input weight dialog
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInputText by remember { mutableStateOf("") }

    // Supplementary photos logs dialog states
    var showPhotoDialog by remember { mutableStateOf(false) }
    var supplementaryPhotoUri by remember { mutableStateOf<String?>(null) }
    var photoNoteText by remember { mutableStateOf("") }
    var photoWeightText by remember { mutableStateOf("") }

    // Treatment dialog states
    var showTreatmentDialog by remember { mutableStateOf(false) }
    var treatmentType by remember { mutableStateOf("Vaccin") } // "Vaccin", "Vermifuge", "Anti-coccidien", "Antibiotique", "Vitamines", "Autre soin"
    var treatmentDesignation by remember { mutableStateOf("") }
    var treatmentDosage by remember { mutableStateOf("") }
    var treatmentNotes by remember { mutableStateOf("") }
    var treatmentWithdrawalDays by remember { mutableStateOf("0") }
    var showWithdrawalBlockedDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                supplementaryPhotoUri = uri.toString()
            }
        }
    )

    val context = LocalContext.current
    val cameraLauncherForAlbum = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap ->
            if (bitmap != null) {
                val savedUri = saveBitmapToCache(context, bitmap)
                if (savedUri != null) {
                    supplementaryPhotoUri = savedUri.toString()
                }
            }
        }
    )

    // Rabbit photo presets to easily choose during updates
    val presets = listOf(
        "https://images.unsplash.com/photo-1585110396000-c9ffd4e4b308?w=300" to "Blanc Pur",
        "https://images.unsplash.com/photo-1591561954557-26941169b49e?w=300" to "Brun Pastel",
        "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=300" to "Gris Doux",
        "https://images.unsplash.com/photo-1541364983171-a8ba01d95cfc?w=300" to "Noir Intense"
    )

    var selectedDetailTab by remember { mutableIntStateOf(0) }
    var showCageCardDialog by remember { mutableStateOf(false) }

    // Mating & litter full detail sheet state
    var selectedMatingForDetail by remember { mutableStateOf<MatingRecord?>(null) }
    var matingFilter by remember { mutableStateOf("Tous") }

    val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    // Status management dialog state
    var showStatusDialog by remember { mutableStateOf(false) }

    // Delete confirmation dialog state
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Edit rabbit states
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editBreed by remember { mutableStateOf("") }
    var editSex by remember { mutableStateOf("") }
    var editCageNumber by remember { mutableStateOf("") }
    var editNotes by remember { mutableStateOf("") }
    var editIsActive by remember { mutableStateOf(true) }
    var editStatus by remember { mutableStateOf("Actif") }
    var editFatherId by remember { mutableStateOf<Int?>(null) }
    var editMotherId by remember { mutableStateOf<Int?>(null) }
    var editImageUri by remember { mutableStateOf<String?>(null) }
    var editBirthDate by remember { mutableLongStateOf(0L) }

    fun openEditDialog() {
        editName = rabbit.name
        editBreed = rabbit.breed
        editSex = rabbit.sex
        editCageNumber = rabbit.cageNumber
        editNotes = rabbit.notes
        editIsActive = rabbit.isActive
        editStatus = rabbit.status
        editFatherId = rabbit.fatherId
        editMotherId = rabbit.motherId
        editImageUri = rabbit.imageUri
        editBirthDate = rabbit.dateOfBirth
        showEditDialog = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Fiche du Lapin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCageCardDialog = true }
                    ) {
                        Icon(
                            Icons.Rounded.Assignment,
                            contentDescription = "Fiche de Cage",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { openEditDialog() }
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Modifier",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true }
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        val isMale = rabbit.sex == "Male"
        val containerHeaderColor = if (isMale) SoftMaleBlue else SoftFemalePink
        val textColor = if (isMale) DarkMaleBlue else DarkFemalePink

        // Compute biological age in months manually
        val ageMs = System.currentTimeMillis() - rabbit.dateOfBirth
        val ageInDays = (ageMs / (24 * 60 * 60 * 1000L)).toInt()
        val ageInMonths = (ageInDays / 30.4).toInt()
        val ageString = when {
            ageInDays < 30 -> "$ageInDays jours"
            ageInMonths == 1 -> "1 mois (${ageInDays % 30} jours)"
            else -> "$ageInMonths mois (${ageInDays % 30}j)"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // Profile Card Header: Custom layout with exquisite pastel color coding
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = containerHeaderColor)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.5f))
                            .border(width = 1.dp, color = textColor.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (rabbit.imageUri != null) {
                            AsyncImage(
                                model = rabbit.imageUri,
                                contentDescription = "Photo de ${rabbit.name}",
                                modifier = Modifier.fillMaxSize().clickable { activeViewerPhotoUri = rabbit.imageUri },
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = if (isMale) Icons.Rounded.Male else Icons.Rounded.Female,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = rabbit.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(textColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Cage " + (rabbit.cageNumber.ifEmpty { "N/A" }),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                            Text(
                                "ID: #${rabbit.id}",
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.8f)
                            )
                        }

                        // Status badge in header
                        val statusDisplay = when (rabbit.status) {
                            "Actif" -> if (rabbit.isActive) "🟢 Présent à la ferme" else "⚪ Inactif"
                            "Externe" -> "🌐 Externe (Géniteur)"
                            "Vendu" -> "💰 Vendu"
                            "Mort" -> "🕊️ Décédé"
                            "Abattu" -> "🥩 Réformé / Abattu"
                            "Donné" -> "🎁 Donné"
                            else -> if (rabbit.isActive) "🟢 ${rabbit.status}" else "⚪ ${rabbit.status}"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (rabbit.isActive) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { showStatusDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = statusDisplay,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rabbit.isActive) textColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "Changer statut",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Sanitary Withdrawal Alert Card (Withdrawal Period)
            val activeWithdrawalEnd = treatmentRecords.filter { it.isUnderWithdrawal() }.mapNotNull { it.effectiveWithdrawalEndTime() }.maxOrNull()
            if (activeWithdrawalEnd != null) {
                val remainingDays = maxOf(1L, ((activeWithdrawalEnd - System.currentTimeMillis()) / 86_400_000L) + 1)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Alerte sanitaire",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "⛔ DÉLAI D'ATTENTE SANITAIRE ACTIF ($remainingDays j restant${if (remainingDays > 1) "s" else ""})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Fin du délai : ${SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(Date(activeWithdrawalEnd))}.\nVente et abattage pour consommation interdits suite à un traitement médical.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Quick Actions Row (Fiche de Cage, Changer Statut, Export PDF)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showStatusDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Statut", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { showCageCardDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Badge, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Fiche Cage", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        com.example.utils.PdfExporter.exportRabbitPdf(
                            context = context,
                            rabbit = rabbit,
                            weights = weightRecords,
                            treatments = treatmentRecords,
                            photos = photoRecords,
                            matingRecords = matingRecords,
                            allRabbits = rabbits
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reproduction Statistics calculations
            val matingsCount = matingRecords.count { it.maleId == rabbitId || it.femaleId == rabbitId }
            val birthsCount = matingRecords.count { it.femaleId == rabbitId && it.status == "Mise bas" }

            // Tab navigation bar
            val tabTitles = listOf(
                "📋 Identité",
                "🩺 Soins (${treatmentRecords.size})",
                "⚖️ Poids (${weightRecords.size})",
                "🧬 Repro ($matingsCount)",
                "📷 Photos (${photoRecords.size})"
            )

            ScrollableTabRow(
                selectedTabIndex = selectedDetailTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedDetailTab == index,
                        onClick = { selectedDetailTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedDetailTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                color = if (selectedDetailTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedDetailTab) {
                0 -> {
                    // TAB 0: IDENTITÉ & GÉNÉTIQUE
                    Button(
                        onClick = { navController.navigate(Screen.Genealogy.createRoute(rabbitId)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_view_genealogy"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.AccountTree, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Consulter l'Arbre Généalogique", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

            // Technical details list card
            Text("Attributs Génétiques & État", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailRow("Statut Élevage", rabbit.status, Icons.Rounded.Info)
                    DetailRow("Race / Variété", rabbit.breed, Icons.Rounded.Pets)
                    DetailRow("Poids d'enregistrement", "${rabbit.weight} kg", Icons.Rounded.Scale)
                    DetailRow("Date de Naissance", formatter.format(Date(rabbit.dateOfBirth)), Icons.Rounded.Cake)
                    DetailRow("Âge exact", ageString, Icons.Rounded.HourglassEmpty)
                }
            }

            // Maturity Assessment Card (Explaining criteria: 5 months / 150 days)
            val isMature = ageInDays >= 150
            val isApproachingMaturity = ageInDays in 90..149
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isMature -> Color(0xFFE8F5E9)
                        isApproachingMaturity -> Color(0xFFFFF8E1)
                        else -> Color(0xFFEDE7F6)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isMature -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                    isApproachingMaturity -> Color(0xFFF57F17).copy(alpha = 0.15f)
                                    else -> Color(0xFF5E35B1).copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                isMature -> "🏆"
                                isApproachingMaturity -> "⏳"
                                else -> "🐣"
                            },
                            fontSize = 18.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isMature -> "Adulte Mature Reproducteur"
                                isApproachingMaturity -> "Sub-adulte en croissance"
                                else -> "Jeune Lapereau / Croissance"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = when {
                                isMature -> Color(0xFF1B5E20)
                                isApproachingMaturity -> Color(0xFFE65100)
                                else -> Color(0xFF4527A0)
                            }
                        )
                        Text(
                            text = when {
                                isMature -> "Critère : Âge ≥ 5 mois ($ageInMonths mois révolus). L'animal est physiologiquement apte à la reproduction."
                                isApproachingMaturity -> "Critère : Âge de 3 à 5 mois ($ageInMonths mois). Maturité reproductive très proche (à 5 mois)."
                                else -> "Critère : Moins de 3 mois ($ageInDays jours). Phase d'allaitement/sevrage et engraissement."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Family connection cards: Father & Mother links
            Text("Lignée Biologique", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val fatherRabbit = rabbits.find { it.id == rabbit.fatherId }
                val motherRabbit = rabbits.find { it.id == rabbit.motherId }

                // Father shortcut Card
                Box(modifier = Modifier.weight(1f)) {
                    ParentShortcutCard(
                        role = "Père ♂️",
                        parent = fatherRabbit,
                        defaultColor = SoftMaleBlue,
                        textColor = DarkMaleBlue,
                        onClick = { fatherRabbit?.let { navController.navigate(Screen.RabbitDetail.createRoute(it.id)) } }
                    )
                }

                // Mother shortcut Card
                Box(modifier = Modifier.weight(1f)) {
                    ParentShortcutCard(
                        role = "Mère ♀️",
                        parent = motherRabbit,
                        defaultColor = SoftFemalePink,
                        textColor = DarkFemalePink,
                        onClick = { motherRabbit?.let { navController.navigate(Screen.RabbitDetail.createRoute(it.id)) } }
                    )
                }
            }

            // Recommended Mating Partners Section
            val oppositeSex = if (rabbit.sex == "Male") "Female" else "Male"
            val eligiblePartners = rabbits.filter { it.isActive && it.sex == oppositeSex }
            
            if (eligiblePartners.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Partenaires de Croisement Recommandés", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                Text("Calculé par analyse généalogique automatique sans consanguinité", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(eligiblePartners) { partner ->
                        val father = if (rabbit.sex == "Male") rabbit else partner
                        val mother = if (rabbit.sex == "Female") rabbit else partner
                        val consanguinity = com.example.data.GenealogyHelper.calculateConsanguinity(father.id, mother.id, rabbits)
                        
                        val ratingText = when {
                            consanguinity == 0.0 -> "0% consanguinité (Idéal ✅)"
                            consanguinity <= 6.25 -> "${String.format(Locale.getDefault(), "%,.1f%%", consanguinity)} (Risque faible 🙂)"
                            consanguinity <= 12.5 -> "${String.format(Locale.getDefault(), "%,.1f%%", consanguinity)} (Risque moyen ⚠️)"
                            else -> "${String.format(Locale.getDefault(), "%,.1f%%", consanguinity)} (Risque Élevé ❌)"
                        }
                        
                        val containerColor = when {
                            consanguinity == 0.0 -> Color(0xFFE8F5E9)
                            consanguinity <= 6.25 -> Color(0xFFFFFAD6)
                            else -> Color(0xFFFFEBEE)
                        }
                        
                        val contentColor = when {
                            consanguinity == 0.0 -> Color(0xFF1B5E20)
                            consanguinity <= 6.25 -> Color(0xFFF57F17)
                            else -> Color(0xFFC62828)
                        }

                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .clickable { navController.navigate(Screen.RabbitDetail.createRoute(partner.id)) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (partner.imageUri != null) {
                                        AsyncImage(
                                            model = partner.imageUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Text(if (partner.sex == "Male") "♂️" else "♀️", fontSize = 16.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(partner.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                Text("Cage: ${partner.cageNumber.ifEmpty { "N/A" }}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(containerColor)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(ratingText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = contentColor)
                                }
                            }
                        }
                    }
                }
            }

                    if (rabbit.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Remarques Éleveur", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = rabbit.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: SANTÉ & SOINS (carnet sanitaire)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Carnet Sanitaire & Traitements",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Button(
                            onClick = { showTreatmentDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.MedicalServices,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nouveau Soin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        if (treatmentRecords.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Aucun soin ou traitement enregistré. Cliquez sur \"Nouveau Soin\" pour suivre les vaccins, vermifuges ou anti-coccidiens.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                treatmentRecords.forEachIndexed { index, record ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val (badgeColor, badgeText, icon) = when (record.type) {
                                            "Vaccin" -> Triple(MaterialTheme.colorScheme.errorContainer, "VACCIN 💉", androidx.compose.material.icons.Icons.Rounded.MedicalServices)
                                            "Vermifuge" -> Triple(MaterialTheme.colorScheme.primaryContainer, "VERMIFUGE 🪱", androidx.compose.material.icons.Icons.Rounded.HealthAndSafety)
                                            "Anti-coccidien" -> Triple(MaterialTheme.colorScheme.tertiaryContainer, "ANTI-COCCIDIEN 🔬", androidx.compose.material.icons.Icons.Rounded.Healing)
                                            "Vitamines" -> Triple(Color(0xFFFFF3CD), "VITAMINES ⚡", androidx.compose.material.icons.Icons.Rounded.Spa)
                                            else -> Triple(MaterialTheme.colorScheme.secondaryContainer, "SOIN 🩹", androidx.compose.material.icons.Icons.Rounded.Info)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(badgeColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .background(badgeColor, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                                Text(
                                                    text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(record.date)),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = record.designation,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (record.dosage.isNotBlank()) {
                                                Text(
                                                    text = "Dosage : ${record.dosage}",
                                                    fontSize = 12.sp,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (record.notes.isNotBlank()) {
                                                Text(
                                                    text = record.notes,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (record.isUnderWithdrawal()) {
                                                val endMs = record.effectiveWithdrawalEndTime() ?: 0L
                                                val remaining = maxOf(1L, ((endMs - System.currentTimeMillis()) / 86_400_000L) + 1)
                                                Surface(
                                                    color = MaterialTheme.colorScheme.errorContainer,
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.Warning,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = "⛔ Délai d'attente actif : ${remaining}j restants (fin le ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(endMs))})",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                }
                                            } else if (record.withdrawalDays > 0) {
                                                Text(
                                                    text = "✅ Délai d'attente purgé (${record.withdrawalDays}j)",
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF166534),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteTreatmentRecord(record) }
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Rounded.Delete,
                                                contentDescription = "Supprimer",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    if (index < treatmentRecords.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: POIDS & GMQ
                    val sortedWeights = weightRecords.sortedBy { it.recordDate }
                    val gmqGramsPerDay: Double? = if (sortedWeights.size >= 2) {
                        val first = sortedWeights.first()
                        val last = sortedWeights.last()
                        val daysDiff = ((last.recordDate - first.recordDate) / (24 * 3600 * 1000L)).toDouble()
                        if (daysDiff >= 1.0) {
                            ((last.weight - first.weight) * 1000.0) / daysDiff
                        } else null
                    } else null

                    if (gmqGramsPerDay != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = if (gmqGramsPerDay >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(if (gmqGramsPerDay >= 0) "📈" else "📉", fontSize = 24.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Gain Moyen Quotidien (GMQ)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (gmqGramsPerDay >= 0) Color(0xFF1B5E20) else Color(0xFFC62828))
                                    Text(
                                        "${String.format(Locale.getDefault(), "%+.1f", gmqGramsPerDay)} g / jour sur ${((sortedWeights.last().recordDate - sortedWeights.first().recordDate) / (24 * 3600 * 1000L))} jours",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (gmqGramsPerDay >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Suivi de Poids",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Button(
                            onClick = { showWeightDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nouvelle Pesée", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (weightRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Aucun historique de pesée enregistré.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(modifier = Modifier.padding(16.dp)) {
                        weightRecords.forEachIndexed { index, weightRecord ->
                            val nextRecord = weightRecords.getOrNull(index + 1)
                            val diff = if (nextRecord != null) weightRecord.weight - nextRecord.weight else null
                            val diffText = if (diff != null) {
                                val rounded = (diff * 100).toInt() / 100.0
                                if (rounded > 0) "+$rounded kg" else if (rounded < 0) "$rounded kg" else "Stable"
                            } else null

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Scale,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${weightRecord.weight} kg",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    if (diff != null && diffText != null) {
                                        val badgeColor = if (diff > 0.0) Color(0xFFE2EDE4) else if (diff < 0.0) Color(0xFFFFDAD6) else Color(0xFFF2EFEA)
                                        val badgeTextColor = if (diff > 0.0) Color(0xFF233527) else if (diff < 0.0) Color(0xFFBA1A1A) else Color(0xFF4A5551)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeColor)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = diffText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeTextColor
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(weightRecord.recordDate)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (index < weightRecords.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

                }

                3 -> {
                    // TAB 3: REPRODUCTION, CROISEMENTS & PORTÉES
                    val myMatings = matingRecords.filter { it.maleId == rabbitId || it.femaleId == rabbitId }.sortedByDescending { it.matingDate }
                    val directOffspring = rabbits.filter { it.fatherId == rabbitId || it.motherId == rabbitId }.sortedByDescending { it.dateOfBirth }

                    val successfulMatings = myMatings.count { it.status == "Mise bas" }
                    val inGestationMatings = myMatings.count { it.status == "Gestation confirmée" || it.status == "Accouplé" }
                    val failedMatings = myMatings.count { it.status == "Échoué" }
                    val successRate = if (myMatings.isNotEmpty()) (successfulMatings.toFloat() / myMatings.size * 100).toInt() else 0
                    val avgLitterSize = if (successfulMatings > 0) String.format(Locale.getDefault(), "%.1f", directOffspring.size.toFloat() / successfulMatings) else "0.0"

                    Text(
                        text = "Performances de Reproduction",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // 4-GRID ZOOTECHNICAL KPI CARD
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Saillies totales", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("${myMatings.size}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Mises bas réussies", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Text("$successfulMatings", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFE8F5E9)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Taux de réussite", fontSize = 10.5.sp, color = Color(0xFF1B5E20))
                                        Text("$successRate %", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF2E7D32))
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Lapereaux nés", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Text("${directOffspring.size} (moy. $avgLitterSize)", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // SECTION: HISTORIQUE COMPLET DES CROISEMENTS & PORTÉES
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Historique des Croisements",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Appuyez sur un croisement pour ouvrir la vue détaillée",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { navController.navigate(Screen.Mating.route) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Saillie", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Filter Chips Row
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filterList = listOf(
                            "Tous" to "Tous (${myMatings.size})",
                            "Mises bas" to "Mises bas ($successfulMatings)",
                            "En gestation" to "En gestation ($inGestationMatings)",
                            "Échoués" to "Échoués ($failedMatings)"
                        )

                        items(filterList) { (key, label) ->
                            FilterChip(
                                selected = matingFilter == key,
                                onClick = { matingFilter = key },
                                label = { Text(label, fontSize = 11.5.sp, fontWeight = if (matingFilter == key) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }

                    val filteredMatings = when (matingFilter) {
                        "Mises bas" -> myMatings.filter { it.status == "Mise bas" }
                        "En gestation" -> myMatings.filter { it.status == "Gestation confirmée" || it.status == "Accouplé" }
                        "Échoués" -> myMatings.filter { it.status == "Échoué" }
                        else -> myMatings
                    }

                    if (filteredMatings.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (myMatings.isEmpty()) "Aucun accouplement enregistré pour ce lapin." else "Aucun accouplement ne correspond au filtre sélectionné.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            filteredMatings.forEach { mating ->
                                val partnerId = if (mating.maleId == rabbitId) mating.femaleId else mating.maleId
                                val partner = rabbits.find { it.id == partnerId }
                                val partnerRole = if (mating.maleId == rabbitId) "Femelle ♀️" else "Mâle ♂️"

                                val litterKits = rabbits.filter { k ->
                                    k.fatherId == mating.maleId && k.motherId == mating.femaleId &&
                                    k.dateOfBirth >= mating.matingDate &&
                                    (k.dateOfBirth - mating.matingDate) <= 42L * 24 * 60 * 60 * 1000L
                                }.ifEmpty {
                                    rabbits.filter { k -> k.fatherId == mating.maleId && k.motherId == mating.femaleId }
                                }

                                val consanguinity = GenealogyHelper.calculateConsanguinity(mating.maleId, mating.femaleId, rabbits)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedMatingForDetail = mating },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Header: Partner info & Status
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(if (mating.maleId == rabbitId) SoftFemalePink else SoftMaleBlue),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (mating.maleId == rabbitId) "♀" else "♂",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = if (mating.maleId == rabbitId) DarkFemalePink else DarkMaleBlue
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = "$partnerRole : ${partner?.name ?: "Inconnu (#$partnerId)"}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${partner?.breed ?: "Race non spécifiée"} • ${partner?.cageNumber?.ifEmpty { "Cage -" } ?: "Cage -"}",
                                                        fontSize = 10.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = when (mating.status) {
                                                    "Mise bas" -> Color(0xFFE8F5E9)
                                                    "Gestation confirmée" -> Color(0xFFE1F5FE)
                                                    "Échoué" -> Color(0xFFFFEBEE)
                                                    else -> Color(0xFFFFF3E0)
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = mating.status,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (mating.status) {
                                                        "Mise bas" -> Color(0xFF1B5E20)
                                                        "Gestation confirmée" -> Color(0xFF0277BD)
                                                        "Échoué" -> Color(0xFFC62828)
                                                        else -> Color(0xFFE65100)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                        // Dates & Inbreeding
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "Saillie : ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(mating.matingDate))}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Mise bas : ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(mating.expectedBirthDate))}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Inbreeding chip
                                            val inbreedingColor = if (consanguinity == 0.0) Color(0xFF1B5E20) else if (consanguinity <= 6.25) Color(0xFFE65100) else Color(0xFFC62828)
                                            val inbreedingBg = if (consanguinity == 0.0) Color(0xFFE8F5E9) else if (consanguinity <= 6.25) Color(0xFFFFF8E1) else Color(0xFFFFEBEE)
                                            Surface(
                                                color = inbreedingBg,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = String.format(Locale.getDefault(), "F = %.1f%%", consanguinity),
                                                    color = inbreedingColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.5.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // Litter info preview
                                        if (litterKits.isNotEmpty()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("🍼", fontSize = 12.sp)
                                                    Text(
                                                        text = "Portée associée : ${litterKits.size} lapereaux (${litterKits.count { it.sex == "Male" }} ♂, ${litterKits.count { it.sex == "Female" }} ♀)",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }

                                        // Detailed View Action Button
                                        FilledTonalButton(
                                            onClick = { selectedMatingForDetail = mating },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Voir la fiche détaillée du croisement & portée ➔", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SECTION 2: PORTÉES & DESCENDANCE DIRECTE
                    Text(
                        text = "Tous les Descendants (${directOffspring.size} lapereaux)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (directOffspring.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("Aucun lapereau issu de ce reproducteur pour l'instant.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            directOffspring.forEach { child ->
                                val otherParentId = if (rabbit.sex == "Male") child.motherId else child.fatherId
                                val otherParent = rabbits.find { it.id == otherParentId }
                                val otherParentLabel = if (rabbit.sex == "Male") "Mère" else "Père"

                                // Find matching mating for this offspring
                                val parentMating = myMatings.find {
                                    (it.maleId == child.fatherId && it.femaleId == child.motherId) ||
                                    (it.femaleId == child.fatherId && it.maleId == child.motherId)
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { navController.navigate(Screen.RabbitDetail.createRoute(child.id)) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (child.sex == "Male") Color(0xFFE3F2FD) else if (child.sex == "Female") Color(0xFFFCE4EC) else Color(0xFFEDE7F6)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (child.sex == "Male") "♂" else if (child.sex == "Female") "♀" else "🍼",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (child.sex == "Male") Color(0xFF1976D2) else if (child.sex == "Female") Color(0xFFC2185B) else Color(0xFF7B1FA2)
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = child.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Né le ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(child.dateOfBirth))} • ${child.weight} kg",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (otherParent != null) {
                                                    Text(
                                                        text = "$otherParentLabel : ${otherParent.name} (${otherParent.cageNumber.ifEmpty { "Cage -" }})",
                                                        fontSize = 10.5.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                color = if (child.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (child.isActive) "Actif" else child.status,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (child.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }

                                            if (parentMating != null) {
                                                Text(
                                                    text = "Croisement ➔",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.clickable { selectedMatingForDetail = parentMating }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> {
                    // TAB 4: PHOTOS & ÉVOLUTION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Journal des Photos",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Button(
                            onClick = { showPhotoDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nouvelle Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        if (photoRecords.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Aucun historique de photo supplémentaire enregistré. Ajoutez une photo pour suivre l'évolution de la croissance.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                photoRecords.forEachIndexed { index, photoRecord ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { activeViewerPhotoUri = photoRecord.imageUri }
                                        ) {
                                            AsyncImage(
                                                model = photoRecord.imageUri,
                                                contentDescription = "Photo d'évolution",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = SimpleDateFormat("dd MMMM yyyy 'à' HH:mm", Locale.getDefault()).format(Date(photoRecord.recordDate)),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (photoRecord.note.isNotBlank()) photoRecord.note else "Mise à jour périodique",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deletePhotoRecord(photoRecord) }
                                        ) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = "Supprimer de l'historique",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    if (index < photoRecords.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Modal dialog to quickly add breeding rabbit weight
    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("Nouvelle pesée", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saisissez le poids mesuré en kilogrammes pour ${rabbit.name}:")
                    OutlinedTextField(
                        value = weightInputText,
                        onValueChange = { weightInputText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text("Poids (kg)") },
                        modifier = Modifier.fillMaxWidth().testTag("weight_dialog_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedWeight = weightInputText.toDoubleOrNull()
                        if (parsedWeight != null) {
                            viewModel.addWeightRecord(
                                WeightRecord(
                                    rabbitId = rabbitId,
                                    weight = parsedWeight,
                                    recordDate = System.currentTimeMillis()
                                )
                            )
                            // Also update rabbit primary weights
                            viewModel.updateRabbit(rabbit.copy(weight = parsedWeight))
                            showWeightDialog = false
                            weightInputText = ""
                        }
                    },
                    enabled = weightInputText.toDoubleOrNull() != null
                ) {
                    Text("Ajouter", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Modal Dialog to upload / add supplementary photo updates
    if (showPhotoDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPhotoDialog = false
                supplementaryPhotoUri = null
                photoNoteText = ""
            },
            title = { Text("Nouvelle photo d'évolution", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Ajoutez une photo supplémentaire pour documenter la croissance de ${rabbit.name} après une période donnée :")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (supplementaryPhotoUri != null) {
                                AsyncImage(
                                    model = supplementaryPhotoUri,
                                    contentDescription = "Aperçu de l'évolution",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        cameraLauncherForAlbum.launch(null)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Appareil photo non disponible", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Prendre une Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Text("Depuis la Galerie", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Presets horizontal selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ou utiliser un modèle :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presets) { (presetUrl, _) ->
                                Card(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .clickable { supplementaryPhotoUri = presetUrl }
                                        .border(
                                            width = if (supplementaryPhotoUri == presetUrl) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(modifier = Modifier.size(54.dp)) {
                                        AsyncImage(
                                            model = presetUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = photoNoteText,
                        onValueChange = { photoNoteText = it },
                        label = { Text("Note d'évolution (Optionnel)") },
                        placeholder = { Text("Ex: Croissance à 3 mois, pelage soyeux") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = photoWeightText,
                        onValueChange = { photoWeightText = it },
                        label = { Text("Mettre à jour le poids de croissance (kg - Optionnel)") },
                        placeholder = { Text("Ex: 1.6") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uriToSave = supplementaryPhotoUri
                        if (uriToSave != null) {
                            val now = System.currentTimeMillis()
                            viewModel.addPhotoRecord(
                                PhotoRecord(
                                    rabbitId = rabbitId,
                                    imageUri = uriToSave,
                                    recordDate = now,
                                    note = photoNoteText.trim()
                                )
                            )
                            
                            // Optional weight update triggered dynamically from the photo panel
                            val parsedWeight = photoWeightText.toDoubleOrNull()
                            if (parsedWeight != null) {
                                viewModel.addWeightRecord(
                                    WeightRecord(
                                        rabbitId = rabbitId,
                                        weight = parsedWeight,
                                        recordDate = now
                                    )
                                )
                                viewModel.updateRabbit(rabbit.copy(weight = parsedWeight))
                            }
                            
                            showPhotoDialog = false
                            supplementaryPhotoUri = null
                            photoNoteText = ""
                            photoWeightText = ""
                        }
                    },
                    enabled = supplementaryPhotoUri != null
                ) {
                    Text("Ajouter", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPhotoDialog = false
                        supplementaryPhotoUri = null
                        photoNoteText = ""
                        photoWeightText = ""
                    }
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showTreatmentDialog) {
        AlertDialog(
            onDismissRequest = {
                showTreatmentDialog = false
                treatmentDesignation = ""
                treatmentDosage = ""
                treatmentNotes = ""
            },
            title = { Text("Log de Traitement ou Soin", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Saisissez un soin préventif ou curatif administré à ${rabbit.name} :", fontSize = 13.sp)

                    Text("Type d'intervention :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    
                    val types = listOf("Vaccin", "Vermifuge", "Anti-coccidien", "Vitamines", "Autre soin")
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            types.take(3).forEach { type ->
                                val isSelected = treatmentType == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { treatmentType = type },
                                    label = { Text(type, fontSize = 11.sp) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            types.drop(3).forEach { type ->
                                val isSelected = treatmentType == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { treatmentType = type },
                                    label = { Text(type, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = treatmentDesignation,
                        onValueChange = { treatmentDesignation = it },
                        label = { Text("Nom du Produit / Soin") },
                        placeholder = { Text("Ex: Lapinject, Lestat, Vitamix...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = treatmentDosage,
                        onValueChange = { treatmentDosage = it },
                        label = { Text("Dosage") },
                        placeholder = { Text("Ex: 0.5 mL, 1g/L d'eau...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = treatmentNotes,
                        onValueChange = { treatmentNotes = it },
                        label = { Text("Notes / Observations") },
                        placeholder = { Text("Ex: Prochain rappel dans 6 mois...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Sanitary Withdrawal Period (Délai d'attente sanitaire)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Délai d'attente sanitaire (jours) :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("0", "3", "7", "14", "28").forEach { d ->
                                FilterChip(
                                    selected = treatmentWithdrawalDays == d,
                                    onClick = { treatmentWithdrawalDays = d },
                                    label = { Text(if (d == "0") "Aucun" else "${d}j", fontSize = 11.sp) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = treatmentWithdrawalDays,
                            onValueChange = { treatmentWithdrawalDays = it.filter { c -> c.isDigit() } },
                            label = { Text("Nombre de jours de rémanence") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        if ((treatmentWithdrawalDays.toIntOrNull() ?: 0) > 0) {
                            Text(
                                "⛔ Vente et abattage bloqués pendant $treatmentWithdrawalDays jours après injection.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (treatmentDesignation.isNotBlank()) {
                            val days = treatmentWithdrawalDays.toIntOrNull() ?: 0
                            val now = System.currentTimeMillis()
                            val endMs = if (days > 0) now + (days * 86_400_000L) else null

                            viewModel.addTreatmentRecord(
                                TreatmentRecord(
                                    rabbitId = rabbitId,
                                    type = treatmentType,
                                    designation = treatmentDesignation.trim(),
                                    date = now,
                                    dosage = treatmentDosage.trim(),
                                    notes = treatmentNotes.trim(),
                                    withdrawalDays = days,
                                    withdrawalEndDate = endMs
                                )
                            )
                            showTreatmentDialog = false
                            treatmentDesignation = ""
                            treatmentDosage = ""
                            treatmentNotes = ""
                            treatmentWithdrawalDays = "0"
                        }
                    },
                    enabled = treatmentDesignation.isNotBlank()
                ) {
                    Text("Ajouter", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTreatmentDialog = false
                        treatmentDesignation = ""
                        treatmentDosage = ""
                        treatmentNotes = ""
                        treatmentWithdrawalDays = "0"
                    }
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    // Grand Format Image Viewer with fast WhatsApp/Social sharing
    if (activeViewerPhotoUri != null) {
        val viewerUri = activeViewerPhotoUri!!
        AlertDialog(
            onDismissRequest = { activeViewerPhotoUri = null },
            title = { Text("Visualisation d'Image 🖼️", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = viewerUri,
                            contentDescription = "Image agrandie",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    Text(
                        text = "Suivi de croissance et identification d'élevage d'ABV Ferme.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    if (viewerUri.startsWith("content://")) {
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(viewerUri))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    } else {
                                        putExtra(Intent.EXTRA_TEXT, "Regardez la photo de mon lapin ${rabbit.name} : $viewerUri")
                                    }
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                val chooser = Intent.createChooser(sendIntent, "Partager la photo via")
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erreur de partage de photo : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Partager 💬", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { activeViewerPhotoUri = null },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Text("Fermer", fontSize = 11.sp)
                    }
                }
            }
        )
    }

    if (showEditDialog) {
        var expandedFatherDropdownEdit by remember { mutableStateOf(false) }
        var expandedMotherDropdownEdit by remember { mutableStateOf(false) }
        val editPhotoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                if (uri != null) {
                    editImageUri = uri.toString()
                }
            }
        )
        val editCameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview(),
            onResult = { bitmap ->
                if (bitmap != null) {
                    val savedUri = saveBitmapToCache(context, bitmap)
                    if (savedUri != null) {
                        editImageUri = savedUri.toString()
                    }
                }
            }
        )
        val eligibleFathersEdit = rabbits.filter { it.sex == "Male" && it.id != rabbitId }
        val eligibleMothersEdit = rabbits.filter { it.sex == "Female" && it.id != rabbitId }
        var showDatePickerEdit by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Modifier la Fiche du Lapin", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Photo de profil (Prendre ou sélectionner)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (editImageUri != null) {
                                AsyncImage(
                                    model = editImageUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AddAPhoto,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        editCameraLauncher.launch(null)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Appareil photo non disponible", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prendre Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    try {
                                        editPhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Sélecteur de photos non disponible", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Galerie", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nom ou Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editBreed,
                        onValueChange = { editBreed = it },
                        label = { Text("Race") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editCageNumber,
                        onValueChange = { editCageNumber = it },
                        label = { Text("Cage / Box") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Column {
                        Text("Sexe", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = editSex == "Male",
                                onClick = { editSex = "Male" },
                                label = { Text("Mâle (Buck)") }
                            )
                            FilterChip(
                                selected = editSex == "Female",
                                onClick = { editSex = "Female" },
                                label = { Text("Femelle (Doe)") }
                            )
                        }
                    }

                    // Status Dropdown selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Statut Élevage", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        var expandedEditStatusDropdown by remember { mutableStateOf(false) }
                        val statusList = listOf(
                            "Actif" to "🟢 Présent à la ferme (Actif)",
                            "Externe" to "🌐 Externe (Géniteur hors ferme)",
                            "Vendu" to "💰 Vendu",
                            "Mort" to "🕊️ Décédé / Mort",
                            "Abattu" to "🥩 Réformé / Abattu",
                            "Donné" to "🎁 Donné"
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val currentStatusLabel = statusList.find { it.first == editStatus }?.second ?: editStatus
                            OutlinedTextField(
                                readOnly = true,
                                value = currentStatusLabel,
                                onValueChange = {},
                                label = { Text("Statut") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.rotate(if (expandedEditStatusDropdown) 180f else 0f)
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
                                    .clickable { expandedEditStatusDropdown = true }
                            )
                            DropdownMenu(
                                expanded = expandedEditStatusDropdown,
                                onDismissRequest = { expandedEditStatusDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                statusList.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.second) },
                                        onClick = {
                                            editStatus = item.first
                                            editIsActive = (item.first == "Actif")
                                            expandedEditStatusDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column {
                        Text("Date de Naissance :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showDatePickerEdit = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatter.format(Date(editBirthDate)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Sélectionner la date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selectedFatherName = eligibleFathersEdit.find { it.id == editFatherId }?.name ?: "Inconnu / Aucun"
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedFatherName,
                            onValueChange = {},
                            label = { Text("Père Biologique") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (expandedFatherDropdownEdit) 180f else 0f)
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
                                .clickable { expandedFatherDropdownEdit = true }
                        )
                        DropdownMenu(
                            expanded = expandedFatherDropdownEdit,
                            onDismissRequest = { expandedFatherDropdownEdit = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Inconnu / Aucun") },
                                onClick = {
                                    editFatherId = null
                                    expandedFatherDropdownEdit = false
                                }
                            )
                            eligibleFathersEdit.forEach { father ->
                                DropdownMenuItem(
                                    text = { Text("${father.name} (${father.breed})${if (!father.isActive) " - [${father.status}]" else if (father.status == "Externe") " - [Externe]" else ""}") },
                                    onClick = {
                                        editFatherId = father.id
                                        expandedFatherDropdownEdit = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selectedMotherObj = eligibleMothersEdit.find { it.id == editMotherId }
                        val selectedMotherName = if (selectedMotherObj != null) {
                            "${selectedMotherObj.name}${if (!selectedMotherObj.isActive) " (${selectedMotherObj.status})" else if (selectedMotherObj.status == "Externe") " (Externe)" else ""}"
                        } else {
                            "Inconnue / Aucune"
                        }
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedMotherName,
                            onValueChange = {},
                            label = { Text("Mère Biologique") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (expandedMotherDropdownEdit) 180f else 0f)
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
                                .clickable { expandedMotherDropdownEdit = true }
                        )
                        DropdownMenu(
                            expanded = expandedMotherDropdownEdit,
                            onDismissRequest = { expandedMotherDropdownEdit = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Inconnue / Aucune") },
                                onClick = {
                                    editMotherId = null
                                    expandedMotherDropdownEdit = false
                                }
                            )
                            eligibleMothersEdit.forEach { mother ->
                                DropdownMenuItem(
                                    text = { Text("${mother.name} (${mother.breed})${if (!mother.isActive) " - [${mother.status}]" else if (mother.status == "Externe") " - [Externe]" else ""}") },
                                    onClick = {
                                        editMotherId = mother.id
                                        expandedMotherDropdownEdit = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Notes / Observations") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            val isWithdrawalActive = viewModel.isRabbitUnderWithdrawal(rabbit.id)
                            if (isWithdrawalActive && (editStatus == "Vendu" || editStatus == "Abattu")) {
                                showWithdrawalBlockedDialog = true
                            } else {
                                val finalIsActive = (editStatus == "Actif")
                                val updatedRabbit = rabbit.copy(
                                    name = editName.trim(),
                                    breed = editBreed.trim().ifEmpty { "Inconnu" },
                                    sex = editSex,
                                    cageNumber = editCageNumber.trim(),
                                    notes = editNotes.trim(),
                                    isActive = finalIsActive,
                                    status = editStatus,
                                    fatherId = editFatherId,
                                    motherId = editMotherId,
                                    imageUri = editImageUri,
                                    dateOfBirth = editBirthDate
                                )
                                viewModel.updateRabbit(updatedRabbit)
                                showEditDialog = false
                            }
                        }
                    },
                    enabled = editName.isNotBlank()
                ) {
                    Text("Enregistrer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Annuler")
                }
            }
        )

        if (showDatePickerEdit) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = editBirthDate)
            DatePickerDialog(
                onDismissRequest = { showDatePickerEdit = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let {
                                editBirthDate = it
                            }
                            showDatePickerEdit = false
                        }
                    ) {
                        Text("Confirmer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerEdit = false }) {
                        Text("Annuler")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    // Modal Dialog for Printable Cage Card ("Fiche de Cage")
    if (showCageCardDialog) {
        AlertDialog(
            onDismissRequest = { showCageCardDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏷️ Fiche de Cage Virtuelle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showCageCardDialog = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer")
                    }
                }
            },
            text = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header: Name & Cage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = rabbit.name.uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "ID: #${rabbit.id} • ${rabbit.sex.uppercase(Locale.getDefault())}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "CAGE: ${rabbit.cageNumber.ifBlank { "N/A" }}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), thickness = 1.dp)

                        // Characteristics
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Race", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(rabbit.breed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("Âge", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ageString, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("Poids Actuel", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format(Locale.getDefault(), "%.2f", rabbit.weight)} kg", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        // Pedigree / Parents
                        val father = rabbits.find { it.id == rabbit.fatherId }
                        val mother = rabbits.find { it.id == rabbit.motherId }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PÈRE ♂️", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                    Text(father?.name ?: "Non renseigné", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("MÈRE ♀️", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2185B))
                                    Text(mother?.name ?: "Non renseignée", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Repro Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Accouplements : $matingsCount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Portées : $birthsCount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (rabbit.notes.isNotBlank()) {
                            Text(
                                text = "Notes : ${rabbit.notes}",
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCageCardDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fermer")
                }
            }
        )
    }

    // Modal Dialog for Changing Rabbit Farm Status
    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = {
                Text(
                    text = "⚙️ Modifier le Statut de ${rabbit.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Choisissez la situation de ce lapin dans votre cheptel :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val statusOptions = listOf(
                        Triple("Actif", "🟢 Présent à l'élevage (Actif)", "Lapin vivant actuellement dans la ferme, apte à la reproduction ou croissance."),
                        Triple("Externe", "🌐 Externe (Géniteur hors ferme)", "Lapin appartenant à un tiers ou utilisé comme étalon/géniteur externe. Ne compte pas dans le cheptel physique mais conserve son arbre généalogique."),
                        Triple("Vendu", "💰 Vendu / Cédé", "Lapin vendu. Toute son historique de portées, pesées et généalogie reste archivé et accessible."),
                        Triple("Mort", "🕊️ Décédé / Mort", "Lapin mort. Fiche technique et données généalogiques conservées intactes."),
                        Triple("Abattu", "🥩 Réformé / Abattu", "Lapin abattu pour la viande ou réformé. Données préservées."),
                        Triple("Donné", "🎁 Donné / Transféré", "Lapin donné à un autre éleveur. Données archivées.")
                    )

                    statusOptions.forEach { (code, title, desc) ->
                        val isSelected = rabbit.status == code
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val isWithdrawalActive = viewModel.isRabbitUnderWithdrawal(rabbit.id)
                                    if (isWithdrawalActive && (code == "Vendu" || code == "Abattu")) {
                                        showWithdrawalBlockedDialog = true
                                    } else {
                                        val newIsActive = (code == "Actif")
                                        viewModel.updateRabbit(
                                            rabbit.copy(
                                                status = code,
                                                isActive = newIsActive
                                            )
                                        )
                                        showStatusDialog = false
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = "Sélectionné",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showWithdrawalBlockedDialog) {
        val endMs = viewModel.getActiveWithdrawalEndDate(rabbit.id)
        val formattedDate = if (endMs != null) SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(Date(endMs)) else "inconnue"
        AlertDialog(
            onDismissRequest = { showWithdrawalBlockedDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "⛔ Action Bloquée : Délai Sanitaire",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    "Ce lapin est actuellement sous traitement médical (antibiotique / antiparasitaire).\n\n" +
                    "La vente et l'abattage pour consommation humaine sont formellement INTERDITS jusqu'au $formattedDate pour garantir l'absence de résidus chimiques dans la viande.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showWithdrawalBlockedDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Compris", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modal Confirmation Dialog for Rabbit Deletion
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Supprimer définitivement ${rabbit.name} ?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Cette action supprimera irréversiblement ce lapin (#${rabbit.id}) de votre base de données locale.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("💡", fontSize = 16.sp)
                            Column {
                                Text(
                                    "Recommandation Élevage",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Si ce lapin a été vendu, est décédé ou est un géniteur extérieur, privilégiez de changer son statut plutôt que de le supprimer. Ainsi, vous conservez son arbre généalogique et l'historique de ses portées.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            showStatusDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Changer le statut plutôt", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRabbit(rabbit)
                        showDeleteConfirmDialog = false
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Supprimer définitivement", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Modal sheet for detailed mating and litters history
    if (selectedMatingForDetail != null) {
        MatingAndLitterDetailSheet(
            mating = selectedMatingForDetail!!,
            rabbits = rabbits,
            viewModel = viewModel,
            navController = navController,
            onDismiss = { selectedMatingForDetail = null }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ParentShortcutCard(
    role: String,
    parent: Rabbit?,
    defaultColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val exists = parent != null
    val background = if (exists) defaultColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val textThemeColor = if (exists) textColor else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = exists) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(role, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textThemeColor.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = parent?.name ?: "Inconnu",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textThemeColor,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun saveBitmapToCache(context: android.content.Context, bitmap: android.graphics.Bitmap): android.net.Uri? {
    return try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "rabbit_photo_${System.currentTimeMillis()}.jpg")
        val stream = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, stream)
        stream.close()
        android.net.Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
