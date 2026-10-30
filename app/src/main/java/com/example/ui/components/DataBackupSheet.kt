package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.utils.GoogleAuthHelper
import com.example.utils.GoogleSignInButton
import com.example.viewmodels.FarmViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBackupBottomSheet(
    viewModel: FarmViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rabbits by viewModel.rabbits.collectAsState()
    val matingRecords by viewModel.matingRecords.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val treatmentRecords by viewModel.treatmentRecords.collectAsState()

    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
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
                                imageVector = Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Sauvegarde & Données",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Exportez, partagez ou restaurez l'ensemble de votre élevage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fermer")
                }
            }

            // Summary Stats Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DataSummaryBadge("🐰 Lapins", "${rabbits.filter { it.isActive }.size}")
                    DataSummaryBadge("💕 Saillies", "${matingRecords.size}")
                    DataSummaryBadge("💉 Soins", "${treatmentRecords.size}")
                    DataSummaryBadge("📦 Stocks", "${inventoryItems.size}")
                }
            }

            if (isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = processingMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val isCloudConnected by viewModel.isCloudConnected.collectAsState()
            val currentUserEmail by viewModel.currentUserEmail.collectAsState()
            val syncMessage by viewModel.syncMessage.collectAsState()
            val isSyncing by viewModel.isSyncing.collectAsState()

            // ACTION CARD 0: Google Cloud Real-Time Sync (Drive/Firestore)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCloudConnected) Color(0xFF43A047) else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCloudConnected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCloudConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    tint = if (isCloudConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Cloud Google & Temps Réel",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isCloudConnected) "Connecté • Sync active" else "Non connecté",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCloudConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCloudConnected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (isCloudConnected) "ACTIF 🟢" else "MULTI-APPAREILS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCloudConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (isCloudConnected) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Compte Google : ${currentUserEmail ?: "Connecté"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Deux personnes utilisant ce même compte Google accèdent et mettent à jour les fiches de lapins, saillies, stocks et rappels en temps réel et à distance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                            if (syncMessage.isNotEmpty()) {
                                Text(
                                    text = "Statut : $syncMessage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.syncAllLocalDataToCloud { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Synchro...")
                                } else {
                                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Forcer la synchro")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val credManager = CredentialManager.create(context)
                                    GoogleAuthHelper.signOut(
                                        context = context,
                                        credentialManager = credManager,
                                        onSignOutComplete = {
                                            Toast.makeText(context, "Déconnexion réussie", Toast.LENGTH_SHORT).show()
                                        },
                                        scope = scope
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Déconnexion")
                            }
                        }
                    } else {
                        Text(
                            text = "Connectez votre compte Google pour sauvegarder automatiquement vos données dans le Cloud et permettre à plusieurs personnes d'accéder aux données en temps réel peu importe la distance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        GoogleSignInButton(
                            modifier = Modifier.fillMaxWidth(),
                            onAuthSuccess = {
                                Toast.makeText(context, "Connecté avec succès au Cloud !", Toast.LENGTH_SHORT).show()
                            },
                            onAuthError = { err ->
                                Toast.makeText(context, "Erreur : $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }

            // ACTION CARD 1: Full JSON Backup (lightweight, standard, no zip/photos)
            DataActionCard(
                icon = Icons.Rounded.Code,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBackground = MaterialTheme.colorScheme.primaryContainer,
                badge = "JSON STANDARD",
                badgeColor = MaterialTheme.colorScheme.primary,
                title = "Sauvegarde Complète (Fichier JSON)",
                description = "Exportation propre et complète de toutes les tables (lapins, généalogies, saillies, pesées, soins, bandes d'engraissement, finances, inventaire, rappels) dans un fichier JSON.",
                actionLabel = "Exporter la Sauvegarde JSON",
                isLoading = isProcessing,
                onClick = {
                    if (isProcessing) return@DataActionCard
                    isProcessing = true
                    processingMessage = "Génération du fichier de sauvegarde JSON..."
                    scope.launch {
                        try {
                            val jsonUri = viewModel.exportBackupJsonFile(context)
                            isProcessing = false
                            if (jsonUri != null) {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, jsonUri)
                                    type = "application/json"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Sauvegarde ABVFarm (Fichier JSON)")
                                context.startActivity(shareIntent)
                            } else {
                                Toast.makeText(context, "Échec de l'exportation JSON", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            isProcessing = false
                            Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            // ACTION CARD 2: CSV Spreadsheet
            DataActionCard(
                icon = Icons.Rounded.TableChart,
                iconTint = MaterialTheme.colorScheme.tertiary,
                iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                badge = "EXCEL / SHEETS",
                badgeColor = MaterialTheme.colorScheme.tertiary,
                title = "Export Tableur (Fichier CSV)",
                description = "Tableau lisible sous Microsoft Excel ou Google Sheets avec la liste complète des lapins, âges, cages, poids et filiations.",
                actionLabel = "Exporter au format CSV",
                isLoading = isProcessing,
                onClick = {
                    if (isProcessing) return@DataActionCard
                    isProcessing = true
                    processingMessage = "Génération du fichier tableur CSV..."
                    scope.launch {
                        try {
                            val csvUri = viewModel.exportRabbitsCsv(context)
                            isProcessing = false
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
                        } catch (e: Exception) {
                            isProcessing = false
                            Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            // ACTION CARD 3: Restore JSON Backup
            DataActionCard(
                icon = Icons.Rounded.CloudDownload,
                iconTint = MaterialTheme.colorScheme.secondary,
                iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                badge = "RESTAURATION",
                badgeColor = MaterialTheme.colorScheme.secondary,
                title = "Restaurer une Sauvegarde JSON",
                description = "Importez un fichier .json généré précédemment pour restaurer instantanément l'ensemble de vos données d'élevage.",
                actionLabel = "Sélectionner un fichier .json",
                isOutlined = true,
                isLoading = isProcessing,
                onClick = {
                    if (!isProcessing) {
                        jsonPickerLauncher.launch("*/*")
                    }
                }
            )

            // Security note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Données 100% locales et privées sur votre appareil.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    // Confirmation dialog before restoring
    if (showRestoreConfirmDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Confirmer la Restauration ?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cette action va remplacer les données actuelles de l'application par le contenu du fichier JSON sélectionné (lapins, pesées, soins, saillies, finances, stocks).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "⚠️ Assurez-vous d'avoir sauvegardé vos données récentes si vous souhaitez les conserver.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uriToRestore = pendingRestoreUri
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                        if (uriToRestore != null) {
                            isProcessing = true
                            processingMessage = "Restauration des données JSON en cours..."
                            scope.launch {
                                val success = viewModel.restoreBackupJsonFile(context, uriToRestore)
                                isProcessing = false
                                if (success) {
                                    Toast.makeText(context, "Données d'élevage restaurées avec succès ! 🎉", Toast.LENGTH_LONG).show()
                                    onDismissRequest()
                                } else {
                                    Toast.makeText(context, "Échec : fichier JSON invalide ou corrompu.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Oui, Remplacer & Restaurer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isProcessing
                ) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun DataSummaryBadge(label: String, count: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = count,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun DataActionCard(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    badge: String,
    badgeColor: Color,
    title: String,
    description: String,
    actionLabel: String,
    isOutlined: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            if (isOutlined) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iconTint,
                        contentColor = Color.White
                    ),
                    enabled = !isLoading
                ) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
