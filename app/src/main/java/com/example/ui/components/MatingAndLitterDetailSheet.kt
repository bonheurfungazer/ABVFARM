package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.data.GenealogyHelper
import com.example.data.MatingRecord
import com.example.data.Rabbit
import com.example.ui.navigation.Screen
import com.example.ui.theme.SoftFemalePink
import com.example.ui.theme.SoftMaleBlue
import com.example.ui.theme.DarkFemalePink
import com.example.ui.theme.DarkMaleBlue
import com.example.viewmodels.FarmViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatingAndLitterDetailSheet(
    mating: MatingRecord,
    rabbits: List<Rabbit>,
    viewModel: FarmViewModel,
    navController: NavController,
    onDismiss: () -> Unit
) {
    val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    val shortDateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val maleRabbit = rabbits.find { it.id == mating.maleId }
    val femaleRabbit = rabbits.find { it.id == mating.femaleId }

    // Find all kits born from this specific mating
    // Either by explicit parent matching within the mating gestation window or direct parentage
    val kits = rabbits.filter { rabbit ->
        rabbit.fatherId == mating.maleId && rabbit.motherId == mating.femaleId &&
        rabbit.dateOfBirth >= mating.matingDate &&
        (rabbit.dateOfBirth - mating.matingDate) <= 42L * 24 * 60 * 60 * 1000L
    }.ifEmpty {
        // Fallback: direct father and mother pairing
        rabbits.filter { rabbit ->
            rabbit.fatherId == mating.maleId && rabbit.motherId == mating.femaleId
        }
    }.sortedByDescending { it.dateOfBirth }

    // Inbreeding calculation
    val consanguinity = GenealogyHelper.calculateConsanguinity(mating.maleId, mating.femaleId, rabbits)

    // Gestation calculations
    val now = System.currentTimeMillis()
    val matingDurationDays = ((now - mating.matingDate) / (24 * 60 * 60 * 1000L)).toInt()
    val daysUntilExpected = ((mating.expectedBirthDate - now) / (24 * 60 * 60 * 1000L)).toInt()
    val gestationProgress = (matingDurationDays.toFloat() / 31f).coerceIn(0f, 1f)

    // Sub-dialogs state
    var showEditStatusDialog by remember { mutableStateOf(false) }
    var showRegisterBirthDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            // ---- TOP BAR WITH TITLE & CLOSE ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Fiche Croisement & Portée",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Accouplement #${mating.id} • ${shortDateFormatter.format(Date(mating.matingDate))}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            // ---- SCROLLABLE CONTENT ----
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. STATUS BANNER & ACTION CHIPS
                item {
                    val statusBgColor = when (mating.status) {
                        "Mise bas" -> Color(0xFFE8F5E9)
                        "Gestation confirmée" -> Color(0xFFE1F5FE)
                        "Échoué" -> Color(0xFFFFEBEE)
                        else -> Color(0xFFFFF3E0)
                    }
                    val statusTextColor = when (mating.status) {
                        "Mise bas" -> Color(0xFF1B5E20)
                        "Gestation confirmée" -> Color(0xFF0277BD)
                        "Échoué" -> Color(0xFFC62828)
                        else -> Color(0xFFE65100)
                    }
                    val statusIcon = when (mating.status) {
                        "Mise bas" -> Icons.Rounded.CheckCircle
                        "Gestation confirmée" -> Icons.Rounded.PregnantWoman
                        "Échoué" -> Icons.Rounded.Cancel
                        else -> Icons.Rounded.Schedule
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = statusBgColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = null,
                                    tint = statusTextColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Statut : ${mating.status}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = statusTextColor
                                    )
                                    Text(
                                        text = when (mating.status) {
                                            "Mise bas" -> "Portée née et enregistrée (${kits.size} lapereaux)"
                                            "Gestation confirmée" -> "Gestation en cours • J+$matingDurationDays"
                                            "Échoué" -> "Saillie non féconde / annulée"
                                            else -> "Accouplement effectué • En attente de confirmation"
                                        },
                                        fontSize = 11.5.sp,
                                        color = statusTextColor.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { showEditStatusDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Modifier", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // 2. PARENT GENITORS COMPARISON (MÂLE & FEMELLE)
                item {
                    Text(
                        text = "Géniteurs du Croisement",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Male Card
                        Box(modifier = Modifier.weight(1f)) {
                            ParentGenitorCard(
                                role = "Père ♂️",
                                rabbit = maleRabbit,
                                defaultContainerColor = SoftMaleBlue,
                                defaultTextColor = DarkMaleBlue,
                                onOpenDetail = {
                                    maleRabbit?.let {
                                        onDismiss()
                                        navController.navigate(Screen.RabbitDetail.createRoute(it.id))
                                    }
                                }
                            )
                        }

                        // Female Card
                        Box(modifier = Modifier.weight(1f)) {
                            ParentGenitorCard(
                                role = "Mère ♀️",
                                rabbit = femaleRabbit,
                                defaultContainerColor = SoftFemalePink,
                                defaultTextColor = DarkFemalePink,
                                onOpenDetail = {
                                    femaleRabbit?.let {
                                        onDismiss()
                                        navController.navigate(Screen.RabbitDetail.createRoute(it.id))
                                    }
                                }
                            )
                        }
                    }
                }

                // 3. GENETICS & INBREEDING CARD
                item {
                    val ratingColor = when {
                        consanguinity == 0.0 -> Color(0xFF1B5E20)
                        consanguinity <= 6.25 -> Color(0xFFE65100)
                        else -> Color(0xFFC62828)
                    }
                    val ratingBg = when {
                        consanguinity == 0.0 -> Color(0xFFE8F5E9)
                        consanguinity <= 6.25 -> Color(0xFFFFF8E1)
                        else -> Color(0xFFFFEBEE)
                    }
                    val ratingTitle = when {
                        consanguinity == 0.0 -> "Sécurité Génétique Maximale (0.0%)"
                        consanguinity <= 6.25 -> "Consanguinité Faible (${String.format(Locale.getDefault(), "%.1f", consanguinity)}%)"
                        consanguinity <= 12.5 -> "Consanguinité Modérée (${String.format(Locale.getDefault(), "%.1f", consanguinity)}%)"
                        else -> "Consanguinité Élevée (${String.format(Locale.getDefault(), "%.1f", consanguinity)}%)"
                    }
                    val ratingDescription = when {
                        consanguinity == 0.0 -> "Aucun ancêtre commun identifié dans les 4 générations. Vigueur hybride et hétérosis optimales pour les lapereaux."
                        consanguinity <= 6.25 -> "Croisement toléré (ex: cousins germains). Risque d'homozygotie très faible."
                        else -> "Croisement consanguin étroit (ex: frère-sœur ou parent-enfant). À éviter pour prévenir les anomalies récessives."
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, ratingColor.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(ratingBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Hub,
                                            contentDescription = null,
                                            tint = ratingColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "Indice de Consanguinité",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Surface(
                                    color = ratingBg,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "F = %.2f %%", consanguinity),
                                        color = ratingColor,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Text(
                                text = ratingTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ratingColor
                            )
                            Text(
                                text = ratingDescription,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // 4. REPRODUCTION TIMELINE
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Chronologie de Reproduction",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Timeline items
                            TimelineRow(
                                title = "Date de la saillie",
                                date = dateFormatter.format(Date(mating.matingDate)),
                                subtitle = "Jour J (Début du cycle)",
                                icon = Icons.Rounded.EventAvailable,
                                isCompleted = true
                            )

                            val palpationStart = mating.matingDate + 10L * 24 * 60 * 60 * 1000L
                            val palpationEnd = mating.matingDate + 14L * 24 * 60 * 60 * 1000L
                            val isPalpationDone = now > palpationEnd
                            TimelineRow(
                                title = "Diagnostic / Palpation",
                                date = "${shortDateFormatter.format(Date(palpationStart))} au ${shortDateFormatter.format(Date(palpationEnd))}",
                                subtitle = "Entre J+10 et J+14 pour confirmer la gestation",
                                icon = Icons.Rounded.Search,
                                isCompleted = isPalpationDone
                            )

                            val isBirthDone = mating.status == "Mise bas" || kits.isNotEmpty()
                            TimelineRow(
                                title = "Mise bas (terme 31 jours)",
                                date = dateFormatter.format(Date(mating.expectedBirthDate)),
                                subtitle = if (isBirthDone) "Mise bas complétée" else if (daysUntilExpected > 0) "Échéance dans $daysUntilExpected jour(s)" else "Date d'échéance atteinte",
                                icon = Icons.Rounded.ChildCare,
                                isCompleted = isBirthDone
                            )

                            if (!isBirthDone && mating.status != "Échoué") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Progression de la gestation (${(gestationProgress * 100).toInt()}%)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { gestationProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                // 5. ZOOTECHNICAL LITTER METRICS
                item {
                    Text(
                        text = "Bilan Zootechnique de la Portée",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val malesCount = kits.count { it.sex == "Male" }
                    val femalesCount = kits.count { it.sex == "Female" }
                    val undeterminedCount = kits.count { it.sex != "Male" && it.sex != "Female" }
                    val activeKitsCount = kits.count { it.isActive }
                    val averageWeight = if (kits.isNotEmpty()) kits.map { it.weight }.average() else 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricTile(
                                    title = "Taille portée",
                                    value = "${kits.size} lapereaux",
                                    icon = Icons.Rounded.Pets,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    title = "Sexe ♂ / ♀",
                                    value = "$malesCount ♂ • $femalesCount ♀",
                                    icon = Icons.Rounded.Wc,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricTile(
                                    title = "Lapereaux Actifs",
                                    value = "$activeKitsCount / ${kits.size}",
                                    icon = Icons.Rounded.Verified,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    title = "Poids Moyen",
                                    value = if (kits.isNotEmpty()) String.format(Locale.getDefault(), "%.2f kg", averageWeight) else "-",
                                    icon = Icons.Rounded.Scale,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (undeterminedCount > 0) {
                                Surface(
                                    color = Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🍼", fontSize = 14.sp)
                                        Text(
                                            text = "$undeterminedCount lapereau(x) ont un sexe indéterminé. Pensez à les sexer vers 4 à 6 semaines.",
                                            fontSize = 11.sp,
                                            color = Color(0xFFE65100),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. DETAILED LIST OF KITS
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lapereaux de la Portée (${kits.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        TextButton(
                            onClick = { showRegisterBirthDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ajouter à la portée", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (kits.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChildCare,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Aucun lapereau encore déclaré pour cette portée.",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { showRegisterBirthDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Déclarer la mise bas / Ajouter des lapereaux", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    items(kits, key = { it.id }) { kit ->
                        KitDetailCard(
                            kit = kit,
                            onOpenRabbit = {
                                onDismiss()
                                navController.navigate(Screen.RabbitDetail.createRoute(kit.id))
                            }
                        )
                    }
                }

                // 7. MATING OBSERVATIONS & NOTES
                item {
                    Text(
                        text = "Observations & Notes de l'Éleveur",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (mating.notes.isNotBlank()) {
                                Text(
                                    text = mating.notes,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp
                                )
                            } else {
                                Text(
                                    text = "Aucune remarque spécifique enregistrée pour ce croisement.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showEditStatusDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Modifier les notes", fontSize = 11.5.sp)
                                }

                                TextButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Supprimer", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- DIALOG: EDIT STATUS & NOTES ----
    if (showEditStatusDialog) {
        var selectedStatus by remember { mutableStateOf(mating.status) }
        var notesInput by remember { mutableStateOf(mating.notes) }
        val statusOptions = listOf("Accouplé", "Gestation confirmée", "Mise bas", "Échoué")

        AlertDialog(
            onDismissRequest = { showEditStatusDialog = false },
            title = { Text("Modifier le Croisement", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Statut de la reproduction :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        statusOptions.forEach { status ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedStatus = status },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedStatus == status) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (selectedStatus == status) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedStatus == status,
                                        onClick = { selectedStatus = status }
                                    )
                                    Text(
                                        text = status,
                                        fontWeight = if (selectedStatus == status) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (selectedStatus == status) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Notes & Observations") },
                        placeholder = { Text("Ex: Palpation positive, comportement calme...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateMatingRecord(
                            mating.copy(
                                status = selectedStatus,
                                notes = notesInput.trim()
                            )
                        )
                        showEditStatusDialog = false
                    }
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditStatusDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // ---- DIALOG: REGISTER BIRTH OR ADD KITS ----
    if (showRegisterBirthDialog) {
        var kitsCountText by remember { mutableStateOf("6") }
        var birthDate by remember { mutableLongStateOf(System.currentTimeMillis()) }

        AlertDialog(
            onDismissRequest = { showRegisterBirthDialog = false },
            title = { Text("Enregistrer les lapereaux", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Combien de lapereaux sont nés de ce croisement entre ${maleRabbit?.name ?: "Père"} et ${femaleRabbit?.name ?: "Mère"} ?",
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = kitsCountText,
                        onValueChange = { kitsCountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Nombre de lapereaux nés") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "💡 Les lapereaux seront automatiquement enregistrés dans l'élevage avec filiation complète (Père #${mating.maleId} et Mère #${mating.femaleId}) et le statut passera à 'Mise bas'.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = kitsCountText.toIntOrNull() ?: 0
                        if (count > 0) {
                            viewModel.registerBirth(mating, count, birthDate)
                            showRegisterBirthDialog = false
                        }
                    },
                    enabled = (kitsCountText.toIntOrNull() ?: 0) > 0
                ) {
                    Text("Valider la portée")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterBirthDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // ---- DIALOG: DELETE CONFIRMATION ----
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Supprimer cet accouplement ?") },
            text = {
                Text("Voulez-vous vraiment supprimer cet enregistrement d'accouplement #${mating.id} ? Les lapereaux déjà créés resteront dans l'élevage.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMatingRecord(mating)
                        showDeleteConfirmDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun ParentGenitorCard(
    role: String,
    rabbit: Rabbit?,
    defaultContainerColor: Color,
    defaultTextColor: Color,
    onOpenDetail: () -> Unit
) {
    val exists = rabbit != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = exists) { onOpenDetail() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (exists) defaultContainerColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, if (exists) defaultTextColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                if (rabbit?.imageUri != null) {
                    AsyncImage(
                        model = rabbit.imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = if (role.contains("Père")) "♂️" else "♀️",
                        fontSize = 18.sp
                    )
                }
            }

            Text(
                text = role,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = defaultTextColor
            )

            Text(
                text = rabbit?.name ?: "Non renseigné",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (rabbit != null) {
                Text(
                    text = "${rabbit.breed} • ${rabbit.cageNumber.ifEmpty { "Cage -" }}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Voir fiche ➔",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = defaultTextColor
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    title: String,
    date: String,
    subtitle: String,
    icon: ImageVector,
    isCompleted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isCompleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = date,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1
                )
                Text(
                    text = value,
                    fontSize = 12.5.sp,
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
private fun KitDetailCard(
    kit: Rabbit,
    onOpenRabbit: () -> Unit
) {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val sexBg = when (kit.sex) {
        "Male" -> Color(0xFFE3F2FD)
        "Female" -> Color(0xFFFCE4EC)
        else -> Color(0xFFEDE7F6)
    }
    val sexColor = when (kit.sex) {
        "Male" -> Color(0xFF1976D2)
        "Female" -> Color(0xFFC2185B)
        else -> Color(0xFF7B1FA2)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenRabbit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                        .background(sexBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (kit.sex) {
                            "Male" -> "♂"
                            "Female" -> "♀"
                            else -> "🍼"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = sexColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = kit.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Né le ${formatter.format(Date(kit.dateOfBirth))} • ${kit.weight} kg • ${kit.cageNumber.ifEmpty { "Cage -" }}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = if (kit.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (kit.isActive) "Actif" else kit.status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (kit.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
