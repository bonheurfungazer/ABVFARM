package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.navigation.Screen
import com.example.data.Reminder
import com.example.viewmodels.FarmViewModel
import com.example.ui.theme.*
import com.example.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import com.example.utils.FarmNotificationManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(navController: NavController, viewModel: FarmViewModel) {
    val reminders by viewModel.reminders.collectAsState()
    val rabbits by viewModel.rabbits.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    // Dialog state variables
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Moyenne") } // "Haute", "Moyenne", "Basse"
    var dateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val formatter = SimpleDateFormat("dd MMMM yyyy 'à' HH:mm", Locale.getDefault())

    // Separate active and historical reminders
    val pendingReminders = reminders.filter { !it.isCompleted }
    val completedReminders = reminders.filter { it.isCompleted }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Calendrier & Alertes 📅", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(
                        onClick = {
                            FarmNotificationManager.triggerTestAlert(context)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("🔔 Bip sonore et vibration déclenchés !")
                            }
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Notifications,
                            contentDescription = "Tester le signal sonore et vibration",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_reminder_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Planifier une tâche")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Push Notification & Haptic Alert Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Système d'Alertes Sonores & Vibreur",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Signal sonore (bip) & vibration active pour les tâches critiques",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    FarmNotificationManager.sendPushNotification(
                                        context = context,
                                        notificationId = 1001,
                                        title = "🔔 Bip Standard ABV Farm",
                                        message = "Vérification du signal sonore et de la vibration normale.",
                                        channelId = FarmNotificationManager.CHANNEL_REMINDERS,
                                        isUrgent = false,
                                        badgeText = "Rappel"
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("🔊 Bip standard + vibration émis")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Normal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    FarmNotificationManager.sendPushNotification(
                                        context = context,
                                        notificationId = 1002,
                                        title = "🚨 ALERTE URGENTE ÉLEVAGE",
                                        message = "Signal critique : double bip aigu et vibration intense répétée !",
                                        channelId = FarmNotificationManager.CHANNEL_CRITICAL_ALERTS,
                                        isUrgent = true,
                                        badgeText = "Urgence"
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("🚨 Double bip critique + vibration intense !")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Urgence", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // Pending checklist Section header
            if (pendingReminders.isNotEmpty()) {
                item {
                    Text(
                        "Tâches planifiées (${pendingReminders.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                items(pendingReminders) { reminder ->
                    val context = LocalContext.current
                    val matchedRabbit = remember(reminder, rabbits) {
                        rabbits.find { r ->
                            r.name.isNotBlank() && (
                                reminder.title.contains(r.name, ignoreCase = true) ||
                                reminder.description.contains(r.name, ignoreCase = true)
                            )
                        }
                    }
                    ReminderItemRow(
                        reminder = reminder,
                        matchedRabbit = matchedRabbit,
                        onRabbitClick = { rabbitId ->
                            navController.navigate(Screen.RabbitDetail.createRoute(rabbitId))
                        },
                        onCompleteChange = { completion ->
                            viewModel.updateReminder(reminder.copy(isCompleted = completion))
                        },
                        onDelete = { viewModel.deleteReminder(reminder) },
                        onTestNotification = {
                            ReminderScheduler.triggerImmediateNotification(context, reminder)
                        }
                    )
                }
            }

            // Completed sections
            if (completedReminders.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Historique des tâches accomplies",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                items(completedReminders) { reminder ->
                    val context = LocalContext.current
                    ReminderItemRow(
                        reminder = reminder,
                        onCompleteChange = { completion ->
                            viewModel.updateReminder(reminder.copy(isCompleted = completion))
                        },
                        onDelete = { viewModel.deleteReminder(reminder) },
                        onTestNotification = {
                            ReminderScheduler.triggerImmediateNotification(context, reminder)
                        }
                    )
                }
            }

            // Absolute empty state
            if (reminders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(60.dp)
                            )
                            Text(
                                "Aucun rappel programmé.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Enregistrez des palpations ou nettoyages pour rester à jour.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Task Planner dialog modal
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Planifier un rappel d'élevage", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titre de la tâche") },
                        placeholder = { Text("Ex: Vaccination VHD, Pesée Goliath...") },
                        modifier = Modifier.fillMaxWidth().testTag("rem_input_title"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Note Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Ex: Vaccin de rappel dans le cou...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Priority toggle options
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Priorité", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val priorities = listOf("Haute", "Moyenne", "Basse")
                            priorities.forEach { p ->
                                val isSelected = priority == p
                                val color = when (p) {
                                    "Haute" -> MaterialTheme.colorScheme.error
                                    "Moyenne" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                                val background = if (isSelected) color else Color.Transparent
                                val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(background)
                                        .clickable { priority = p }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(p, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                            }
                        }
                    }

                    // Date selector row Trigger
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Date d'échéance", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val justDateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                            Text(justDateFormatter.format(Date(dateMs)), fontSize = 13.sp)
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Time selector row Trigger
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Heure de réalisation", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
                        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable {
                                    val hourOf = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                    val minOf = cal.get(java.util.Calendar.MINUTE)
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, selectedHour, selectedMinute ->
                                            val updatedCal = java.util.Calendar.getInstance().apply {
                                                timeInMillis = dateMs
                                                set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
                                                set(java.util.Calendar.MINUTE, selectedMinute)
                                                set(java.util.Calendar.SECOND, 0)
                                                set(java.util.Calendar.MILLISECOND, 0)
                                            }
                                            dateMs = updatedCal.timeInMillis
                                        },
                                        hourOf,
                                        minOf,
                                        true
                                    ).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formattedTime, fontSize = 13.sp)
                            Text("🕒", fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.addReminder(
                                Reminder(
                                    title = title.trim(),
                                    description = description.trim(),
                                    dueDate = dateMs,
                                    priority = priority,
                                    isCompleted = false
                                )
                            )
                            showAddDialog = false
                            title = ""
                            description = ""
                            priority = "Moyenne"
                        }
                    },
                    enabled = title.isNotBlank()
                ) {
                    Text("Confirmer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Material 3 Date Picker dialog within reminders
    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = dateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateMs = dateState.selectedDateMillis ?: System.currentTimeMillis()
                        showDatePicker = false
                    }
                ) {
                    Text("Sélectionner", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Fermer")
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
fun ReminderItemRow(
    reminder: Reminder,
    matchedRabbit: com.example.data.Rabbit? = null,
    onRabbitClick: ((Int) -> Unit)? = null,
    onCompleteChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onTestNotification: () -> Unit
) {
    val formatter = SimpleDateFormat("dd MMMM yyyy 'à' HH:mm", Locale.getDefault())

    var isChecked by remember { mutableStateOf(reminder.isCompleted) }

    val priorityColor = when (reminder.priority) {
        "Haute" -> MaterialTheme.colorScheme.error
        "Moyenne" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(0.5.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { checked ->
                    isChecked = checked
                    onCompleteChange(checked)
                },
                modifier = Modifier.testTag("rem_check_${reminder.id}")
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = reminder.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Tiny priority tag
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(priorityColor, CircleShape)
                    )
                }

                if (reminder.description.isNotEmpty()) {
                    Text(
                        text = reminder.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (matchedRabbit != null && onRabbitClick != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.clickable { onRabbitClick(matchedRabbit.id) }
                    ) {
                        Text(
                            text = "🐰 Fiche de ${matchedRabbit.name}${if (matchedRabbit.cageNumber.isNotBlank()) " (Cage ${matchedRabbit.cageNumber})" else ""} ➔",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Échéance : " + formatter.format(Date(reminder.dueDate)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            if (!isChecked) {
                IconButton(onClick = onTestNotification, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Notifications,
                        contentDescription = "Tester la notification",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
