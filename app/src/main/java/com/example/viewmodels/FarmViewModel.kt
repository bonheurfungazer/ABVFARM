package com.example.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class FarmViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = FarmRepository(db.farmDao())
    val firestoreRepo = FirestoreFarmRepository(application)
    private val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()

    private var cloudJob: kotlinx.coroutines.Job? = null
    private var isSyncingFromCloud = false

    private val _isCloudConnected = kotlinx.coroutines.flow.MutableStateFlow(firebaseAuth.currentUser != null)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected

    private val _currentUserEmail = kotlinx.coroutines.flow.MutableStateFlow(firebaseAuth.currentUser?.email)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail

    private val _isSyncing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _syncMessage = kotlinx.coroutines.flow.MutableStateFlow("Prêt")
    val syncMessage: StateFlow<String> = _syncMessage

    private val prefs = application.getSharedPreferences("farm_prefs", android.content.Context.MODE_PRIVATE)
    private val _manualFeedStock = kotlinx.coroutines.flow.MutableStateFlow(prefs.getFloat("manual_feed_stock", -1f).toDouble())
    val manualFeedStock: StateFlow<Double> = _manualFeedStock

    fun setManualFeedStock(value: Double) {
        prefs.edit().putFloat("manual_feed_stock", value.toFloat()).apply()
        _manualFeedStock.value = value
    }

    // Theme mode: 0 = Auto / System, 1 = Light, 2 = Dark
    private val _themeMode = kotlinx.coroutines.flow.MutableStateFlow(prefs.getInt("app_theme_mode", 0))
    val themeMode: StateFlow<Int> = _themeMode

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt("app_theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun toggleDarkMode(isCurrentDark: Boolean) {
        val nextMode = if (isCurrentDark) 1 else 2 // 1 = Light, 2 = Dark
        setThemeMode(nextMode)
    }

    private val _isBottomBarVisible = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isBottomBarVisible: StateFlow<Boolean> = _isBottomBarVisible

    fun setBottomBarVisible(visible: Boolean) {
        _isBottomBarVisible.value = visible
    }

    val rabbits: StateFlow<List<Rabbit>> = repository.rabbits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val matingRecords: StateFlow<List<MatingRecord>> = repository.matingRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val inventoryItems: StateFlow<List<InventoryItem>> = repository.inventoryItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val reminders: StateFlow<List<Reminder>> = repository.reminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val treatmentRecords: StateFlow<List<TreatmentRecord>> = repository.treatmentRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dailyFeedLogs: StateFlow<List<DailyFeedLog>> = repository.dailyFeedLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val fatteningBatches: StateFlow<List<FatteningBatch>> = repository.fatteningBatches
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val financialTransactions: StateFlow<List<FinancialTransaction>> = repository.financialTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        _isCloudConnected.value = user != null
        _currentUserEmail.value = user?.email
        if (user != null) {
            startCloudSync(user.uid, user.displayName, user.email)
        } else {
            stopCloudSync()
        }
    }

    init {
        // App starts with clean reminders maintenance and one-time initial alarm scheduling
        cleanExpiredReminders()
        viewModelScope.launch {
            try {
                val list = repository.reminders.first()
                val now = System.currentTimeMillis()
                list.filter { !it.isCompleted && it.dueDate > now }.forEach { rem ->
                    ReminderScheduler.scheduleReminder(getApplication(), rem)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        firebaseAuth.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        super.onCleared()
        firebaseAuth.removeAuthStateListener(authListener)
        cloudJob?.cancel()
    }

    fun startCloudSync(uid: String, displayName: String?, email: String?) {
        cloudJob?.cancel()
        cloudJob = viewModelScope.launch {
            try {
                firestoreRepo.recordUserSync(displayName, email)
                _syncMessage.value = "Connecté au Cloud (Temps réel)"
            } catch (e: Exception) {
                _syncMessage.value = "Erreur de connexion Cloud: ${e.message}"
            }

            // Launch listener for rabbits
            launch {
                firestoreRepo.observeRabbits().collect { cloudRabbits ->
                    if (cloudRabbits.isNotEmpty()) {
                        isSyncingFromCloud = true
                        try {
                            val localList = rabbits.value
                            for (cr in cloudRabbits) {
                                val roomRabbit = cr.toRoom()
                                val existing = localList.find { it.id == roomRabbit.id || (it.name == roomRabbit.name && it.dateOfBirth == roomRabbit.dateOfBirth) }
                                if (existing != null) {
                                    if (existing != roomRabbit) {
                                        repository.updateRabbit(roomRabbit.copy(id = existing.id))
                                    }
                                } else {
                                    repository.insertRabbit(roomRabbit)
                                }
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }

            // Launch listener for mating records
            launch {
                firestoreRepo.observeMatingRecords().collect { cloudMatings ->
                    if (cloudMatings.isNotEmpty()) {
                        isSyncingFromCloud = true
                        try {
                            val localList = matingRecords.value
                            for (cm in cloudMatings) {
                                val roomMating = cm.toRoom()
                                val existing = localList.find { it.id == roomMating.id || (it.maleId == roomMating.maleId && it.femaleId == roomMating.femaleId && it.matingDate == roomMating.matingDate) }
                                if (existing != null) {
                                    if (existing != roomMating) {
                                        repository.updateMatingRecord(roomMating.copy(id = existing.id))
                                    }
                                } else {
                                    repository.insertMatingRecord(roomMating)
                                }
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }

            // Launch listener for inventory
            launch {
                firestoreRepo.observeInventory().collect { cloudItems ->
                    if (cloudItems.isNotEmpty()) {
                        isSyncingFromCloud = true
                        try {
                            val localList = inventoryItems.value
                            for (ci in cloudItems) {
                                val roomItem = ci.toRoom()
                                val existing = localList.find { it.id == roomItem.id || it.name == roomItem.name }
                                if (existing != null) {
                                    if (existing != roomItem) {
                                        repository.updateInventoryItem(roomItem.copy(id = existing.id))
                                    }
                                } else {
                                    repository.insertInventoryItem(roomItem)
                                }
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }

            // Launch listener for reminders
            launch {
                firestoreRepo.observeReminders().collect { cloudReminders ->
                    if (cloudReminders.isNotEmpty()) {
                        isSyncingFromCloud = true
                        try {
                            val localList = reminders.value
                            for (cr in cloudReminders) {
                                val roomRem = cr.toRoom()
                                val existing = localList.find { it.id == roomRem.id || (it.title == roomRem.title && it.dueDate == roomRem.dueDate) }
                                if (existing != null) {
                                    if (existing != roomRem) {
                                        repository.updateReminder(roomRem.copy(id = existing.id))
                                    }
                                } else {
                                    repository.insertReminder(roomRem)
                                }
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }

            // Launch listener for treatments
            launch {
                firestoreRepo.observeTreatments().collect { cloudTreatments ->
                    if (cloudTreatments.isNotEmpty()) {
                        isSyncingFromCloud = true
                        try {
                            val localList = treatmentRecords.value
                            for (ct in cloudTreatments) {
                                val roomTreat = ct.toRoom()
                                val existing = localList.find { it.id == roomTreat.id || (it.rabbitId == roomTreat.rabbitId && it.designation == roomTreat.designation && it.date == roomTreat.date) }
                                if (existing == null) {
                                    repository.insertTreatmentRecord(roomTreat)
                                }
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }

            // Launch listener for weights
            launch {
                firestoreRepo.observeWeights().collect { cloudWeights ->
                    if (cloudWeights.isNotEmpty()) {
                        isSyncingFromCloud = true
                        try {
                            val localList = repository.getAllWeightRecords()
                            for (cw in cloudWeights) {
                                val roomWeight = cw.toRoom()
                                val existing = localList.find { it.id == roomWeight.id || (it.rabbitId == roomWeight.rabbitId && it.recordDate == roomWeight.recordDate) }
                                if (existing == null) {
                                    repository.insertWeightRecord(roomWeight)
                                }
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }

            // Launch listener for feed logs
            launch {
                firestoreRepo.observeFeedLogs().collect { cloudLogs ->
                    if (cloudLogs.isNotEmpty()) {
                        isSyncingFromCloud = true
                        try {
                            val localList = dailyFeedLogs.value
                            for (cl in cloudLogs) {
                                val roomLog = cl.toRoom()
                                val existing = localList.find { it.id == roomLog.id || (it.feedDate == roomLog.feedDate && it.quantityGrams == roomLog.quantityGrams) }
                                if (existing == null) {
                                    repository.insertDailyFeedLog(roomLog)
                                }
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }

            // Launch listener for fattening batches
            launch {
                firestoreRepo.observeFatteningBatches().collect { cloudBatches ->
                    if (cloudBatches.isNotEmpty()) {
                        isSyncingFromCloud = true
                        try {
                            val localList = repository.getAllFatteningBatches()
                            for (cb in cloudBatches) {
                                val roomBatch = cb.toRoom()
                                val existing = localList.find { it.id == roomBatch.id || it.batchCode == roomBatch.batchCode }
                                if (existing != null) {
                                    if (existing != roomBatch) {
                                        repository.updateFatteningBatch(roomBatch.copy(id = existing.id))
                                    }
                                } else {
                                    repository.insertFatteningBatch(roomBatch)
                                }
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }

            // Launch listener for financial transactions
            launch {
                firestoreRepo.observeFinancialTransactions().collect { cloudTxs ->
                    if (cloudTxs.isNotEmpty()) {
                        isSyncingFromCloud = true
                        try {
                            val localList = repository.getAllFinancialTransactions()
                            for (ctx in cloudTxs) {
                                val roomTx = ctx.toRoom()
                                val existing = localList.find { it.id == roomTx.id || (it.date == roomTx.date && it.amountFcfa == roomTx.amountFcfa && it.description == roomTx.description) }
                                if (existing != null) {
                                    if (existing != roomTx) {
                                        repository.updateFinancialTransaction(roomTx.copy(id = existing.id))
                                    }
                                } else {
                                    repository.insertFinancialTransaction(roomTx)
                                }
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }
        }
    }

    fun stopCloudSync() {
        cloudJob?.cancel()
        cloudJob = null
        _syncMessage.value = "Hors-ligne"
    }

    fun syncAllLocalDataToCloud(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = firebaseAuth.currentUser
        if (user == null) {
            onComplete(false, "Veuillez vous connecter avec votre compte Google")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Sauvegarde en cours vers le Cloud..."
            try {
                val uid = user.uid
                firestoreRepo.recordUserSync(user.displayName, user.email)

                // Sync Rabbits
                repository.getAllRabbits().forEach { r ->
                    firestoreRepo.saveRabbit(r.toFirestore(uid))
                }
                // Sync Mating
                repository.getAllMatingRecords().forEach { m ->
                    firestoreRepo.saveMatingRecord(m.toFirestore(uid))
                }
                // Sync Inventory
                repository.getAllInventoryItems().forEach { i ->
                    firestoreRepo.saveInventoryItem(i.toFirestore(uid))
                }
                // Sync Reminders
                repository.getAllReminders().forEach { rem ->
                    firestoreRepo.saveReminder(rem.toFirestore(uid))
                }
                // Sync Treatments
                repository.getAllTreatmentRecords().forEach { t ->
                    firestoreRepo.saveTreatment(t.toFirestore(uid))
                }
                // Sync Weights
                repository.getAllWeightRecords().forEach { w ->
                    firestoreRepo.saveWeight(w.toFirestore(uid))
                }
                // Sync Feed logs
                repository.getAllDailyFeedLogs().forEach { f ->
                    firestoreRepo.saveFeedLog(f.toFirestore(uid))
                }
                // Sync Fattening Batches
                repository.getAllFatteningBatches().forEach { b ->
                    firestoreRepo.saveFatteningBatch(b.toFirestore(uid))
                }
                // Sync Financial Transactions
                repository.getAllFinancialTransactions().forEach { tx ->
                    firestoreRepo.saveFinancialTransaction(tx.toFirestore(uid))
                }

                _syncMessage.value = "Sauvegarde Cloud réussie et synchronisée en temps réel !"
                onComplete(true, "Toutes les données sont sauvegardées et synchronisées en temps réel !")
            } catch (e: Exception) {
                _syncMessage.value = "Erreur: ${e.localizedMessage}"
                onComplete(false, "Erreur de sauvegarde: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun cleanExpiredReminders() = viewModelScope.launch {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        repository.deleteRemindersBefore(todayStart)
    }

    private suspend fun seedDatabase() {
        val calendar = Calendar.getInstance()

        // 1. Seed Rabbits
        calendar.add(Calendar.MONTH, -6) // 6 months old
        val dob6m = calendar.timeInMillis
        calendar.apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.MONTH, -4) // 4 months old
        }
        val dob4m = calendar.timeInMillis
        calendar.apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.MONTH, -2) // 2 months old
        }
        val dob2m = calendar.timeInMillis

        val r1Id = repository.insertRabbit(Rabbit(name = "Goliath", sex = "Male", dateOfBirth = dob6m, breed = "Géant des Flandres", cageNumber = "A-01", weight = 5.2, notes = "Très robuste, excellent reproducteur."))
        val r2Id = repository.insertRabbit(Rabbit(name = "Blanchette", sex = "Female", dateOfBirth = dob6m, breed = "Néo-Zélandais", cageNumber = "B-03", weight = 4.8, notes = "Douce et maternelle."))
        val r3Id = repository.insertRabbit(Rabbit(name = "Chocolat", sex = "Male", dateOfBirth = dob4m, breed = "Fauve de Bourgogne", cageNumber = "A-02", weight = 3.9, notes = "Actif et agile."))
        val r4Id = repository.insertRabbit(Rabbit(name = "Bella", sex = "Female", dateOfBirth = dob4m, breed = "Fauve de Bourgogne", cageNumber = "B-04", weight = 4.1, notes = "Première portée à surveiller."))
        
        // Feed baby rabbits (children of r1 and r2)
        val r5Id = repository.insertRabbit(Rabbit(name = "Flocon", sex = "Female", dateOfBirth = dob2m, breed = "Croisé", cageNumber = "C-01", weight = 1.8, fatherId = r1Id.toInt(), motherId = r2Id.toInt(), notes = "Lapereaux de Goliath et Blanchette."))
        val r6Id = repository.insertRabbit(Rabbit(name = "Caramel", sex = "Male", dateOfBirth = dob2m, breed = "Croisé", cageNumber = "C-02", weight = 2.0, fatherId = r1Id.toInt(), motherId = r2Id.toInt(), notes = "Amical."))

        // 2. Seed Mating Logic
        // Gestation takes about 31 days
        val matingDay = System.currentTimeMillis() - 15 * 24 * 60 * 60 * 1000L // 15 days ago
        val expectedBirth = matingDay + 31 * 24 * 60 * 60 * 1000L
        repository.insertMatingRecord(MatingRecord(
            maleId = r1Id.toInt(),
            femaleId = r2Id.toInt(),
            matingDate = matingDay,
            expectedBirthDate = expectedBirth,
            status = "Gestation confirmée",
            notes = "Accouplement réussi au premier essai. Blanchette prend du poids."
        ))

        // 3. Seed Food Stock & inventory
        repository.insertInventoryItem(InventoryItem(
            name = "Provende Lapin Élite",
            category = "Nourriture",
            quantity = 50.0,
            unit = "kg",
            pricePerUnit = 0.8,
            totalPrice = 40.0,
            purchaseDate = System.currentTimeMillis() - 25 * 24 * 60 * 60 * 1000L,
            durationMonths = 1,
            notes = "Achat de sac de 50kg."
        ))
        
        repository.insertInventoryItem(InventoryItem(
            name = "Vitamines Liquides",
            category = "Médicament",
            quantity = 5.0,
            unit = "Litre",
            pricePerUnit = 4.0,
            totalPrice = 20.0,
            purchaseDate = System.currentTimeMillis() - 5 * 24 * 60 * 60 * 1000L,
            durationMonths = 6,
            notes = "Flacon de supplément de croissance."
        ))

        // 4. Seed Reminders
        repository.insertReminder(Reminder(
            title = "Palpation Blanchette",
            description = "Vérification de la gestation à 14 jours (palpation abdominale).",
            dueDate = expectedBirth - 17 * 24 * 60 * 60 * 1000L,
            priority = "Haute",
            isCompleted = true
        ))

        repository.insertReminder(Reminder(
            title = "Nid d'accouchement de Blanchette",
            description = "Installer la boîte à nid avec de la paille propre dans la cage B-03.",
            dueDate = expectedBirth - 3 * 24 * 60 * 60 * 1000L,
            priority = "Haute",
            isCompleted = false
        ))

        repository.insertReminder(Reminder(
            title = "Vaccination Lapereaux",
            description = "Vaccin VHD + Myxomatose pour Flocon et Caramel.",
            dueDate = System.currentTimeMillis() + 10 * 24 * 60 * 60 * 1000L,
            priority = "Moyenne",
            isCompleted = false
        ))

        // 5. Weight records
        repository.insertWeightRecord(WeightRecord(rabbitId = r1Id.toInt(), weight = 5.0, recordDate = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L, notes = "Pesée mensuelle"))
        repository.insertWeightRecord(WeightRecord(rabbitId = r1Id.toInt(), weight = 5.2, recordDate = System.currentTimeMillis(), notes = "Bonne croissance"))
    }

    // ---- Operations ----
    fun addRabbit(rabbit: Rabbit) = viewModelScope.launch {
        val newId = repository.insertRabbit(rabbit)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                val savedRabbit = rabbit.copy(id = newId.toInt())
                firestoreRepo.saveRabbit(savedRabbit.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateRabbit(rabbit: Rabbit) = viewModelScope.launch {
        repository.updateRabbit(rabbit)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveRabbit(rabbit.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteRabbit(rabbit: Rabbit) = viewModelScope.launch {
        repository.deleteRabbit(rabbit)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.deleteRabbit("rabbit_${rabbit.id}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addMatingRecord(record: MatingRecord) = viewModelScope.launch {
        val newId = repository.insertMatingRecord(record)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                val saved = record.copy(id = newId.toInt())
                firestoreRepo.saveMatingRecord(saved.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Auto-Reminder: Installer le nid d'accouchement 1 jour avant la mise bas
        val female = repository.getRabbitById(record.femaleId)
        val femaleName = female?.name ?: "Femelle"
        val nestBoxDueDate = record.expectedBirthDate - 1 * 24 * 60 * 60 * 1000L
        val r = Reminder(
            title = "Installer le nid d'accouchement : $femaleName",
            description = "Placer la boîte à nid propre dans la cage de $femaleName (1 jour avant la mise bas prévue).",
            dueDate = nestBoxDueDate,
            priority = "Haute",
            isCompleted = false
        )
        val id = repository.insertReminder(r)
        val savedReminder = r.copy(id = id.toInt())
        ReminderScheduler.scheduleReminder(getApplication(), savedReminder)
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveReminder(savedReminder.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun registerBirth(record: MatingRecord, offspringCount: Int, birthDate: Long = System.currentTimeMillis()) = viewModelScope.launch {
        // 1. Update status and notes
        val countNotes = "Mise bas de $offspringCount lapereaux sains"
        val updatedRecord = record.copy(
            status = "Mise bas",
            notes = if (record.notes.isEmpty()) countNotes else "${record.notes} • $countNotes"
        )
        repository.updateMatingRecord(updatedRecord)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveMatingRecord(updatedRecord.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 2. Fetch mother
        val mother = repository.getRabbitById(record.femaleId)
        val breedToUse = mother?.breed ?: "Inconnu"
        
        val birthCalendar = java.util.Calendar.getInstance().apply { timeInMillis = birthDate }
        val birthYear = birthCalendar.get(java.util.Calendar.YEAR) % 100
        
        val maxId = repository.getMaxRabbitId() ?: 0
        
        // 3. Insert kits
        for (i in 1..offspringCount) {
            val sequentialNumber = maxId + i
            val code = String.format(java.util.Locale.US, "ABV-%02d-%03d", birthYear, sequentialNumber)
            val baby = Rabbit(
                name = String.format(java.util.Locale.US, "Lapereau n°%d (Portée %s)", i, code),
                sex = "Indéterminé",
                dateOfBirth = birthDate,
                breed = breedToUse,
                cageNumber = mother?.cageNumber ?: "",
                weight = 0.1, // Birth standard kitten weight
                fatherId = record.maleId,
                motherId = record.femaleId,
                notes = "Sexe indéterminé à la naissance. À sexer et nommer après 4-6 semaines.",
                isActive = true
            )
            val kitId = repository.insertRabbit(baby)
            if (user != null && !isSyncingFromCloud) {
                try {
                    firestoreRepo.saveRabbit(baby.copy(id = kitId.toInt()).toFirestore(user.uid))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateMatingRecord(record: MatingRecord) = viewModelScope.launch {
        repository.updateMatingRecord(record)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveMatingRecord(record.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMatingRecord(record: MatingRecord) = viewModelScope.launch {
        repository.deleteMatingRecord(record)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.deleteMatingRecord("mating_${record.id}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---- Treatment Operations ----
    fun getTreatmentsForRabbit(rabbitId: Int) = repository.getTreatmentsForRabbit(rabbitId)

    fun addTreatmentRecord(record: TreatmentRecord) = viewModelScope.launch {
        val newId = repository.insertTreatmentRecord(record)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveTreatment(record.copy(id = newId.toInt()).toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteTreatmentRecord(record: TreatmentRecord) = viewModelScope.launch {
        repository.deleteTreatmentRecord(record)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.deleteTreatment("treat_${record.id}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---- Daily Feed Operations ----
    fun addDailyFeedLog(log: DailyFeedLog) = viewModelScope.launch {
        val newId = repository.insertDailyFeedLog(log)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveFeedLog(log.copy(id = newId.toInt()).toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteDailyFeedLog(log: DailyFeedLog) = viewModelScope.launch {
        repository.deleteDailyFeedLog(log)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.deleteFeedLog("feed_${log.id}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---- Fattening Batches Operations ----
    fun addFatteningBatch(batch: FatteningBatch) = viewModelScope.launch {
        val newId = repository.insertFatteningBatch(batch)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveFatteningBatch(batch.copy(id = newId).toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateFatteningBatch(batch: FatteningBatch) = viewModelScope.launch {
        repository.updateFatteningBatch(batch)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveFatteningBatch(batch.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteFatteningBatch(batch: FatteningBatch) = viewModelScope.launch {
        repository.deleteFatteningBatch(batch)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.deleteFatteningBatch("batch_${batch.id}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---- Financial Transactions Operations ----
    fun addFinancialTransaction(tx: FinancialTransaction) = viewModelScope.launch {
        val newId = repository.insertFinancialTransaction(tx)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveFinancialTransaction(tx.copy(id = newId).toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateFinancialTransaction(tx: FinancialTransaction) = viewModelScope.launch {
        repository.updateFinancialTransaction(tx)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveFinancialTransaction(tx.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteFinancialTransaction(tx: FinancialTransaction) = viewModelScope.launch {
        repository.deleteFinancialTransaction(tx)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.deleteFinancialTransaction("tx_${tx.id}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---- Sanitary Withdrawal Helpers ----
    fun isRabbitUnderWithdrawal(rabbitId: Int): Boolean {
        val now = System.currentTimeMillis()
        val list = treatmentRecords.value.filter { it.rabbitId == rabbitId }
        return list.any { it.isUnderWithdrawal(now) }
    }

    fun getActiveWithdrawalEndDate(rabbitId: Int): Long? {
        val now = System.currentTimeMillis()
        val list = treatmentRecords.value.filter { it.rabbitId == rabbitId && it.isUnderWithdrawal(now) }
        return list.mapNotNull { it.effectiveWithdrawalEndTime() }.maxOrNull()
    }

    fun addInventoryItem(item: InventoryItem) = viewModelScope.launch {
        val newId = repository.insertInventoryItem(item)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveInventoryItem(item.copy(id = newId.toInt()).toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateInventoryItem(item: InventoryItem) = viewModelScope.launch {
        repository.updateInventoryItem(item)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveInventoryItem(item.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteInventoryItem(item: InventoryItem) = viewModelScope.launch {
        repository.deleteInventoryItem(item)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.deleteInventoryItem("item_${item.id}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addReminder(reminder: Reminder) = viewModelScope.launch {
        val id = repository.insertReminder(reminder)
        val savedReminder = reminder.copy(id = id.toInt())
        ReminderScheduler.scheduleReminder(getApplication(), savedReminder)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveReminder(savedReminder.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateReminder(reminder: Reminder) = viewModelScope.launch {
        repository.updateReminder(reminder)
        if (reminder.isCompleted) {
            ReminderScheduler.cancelReminder(getApplication(), reminder)
        } else {
            ReminderScheduler.scheduleReminder(getApplication(), reminder)
        }
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.saveReminder(reminder.toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteReminder(reminder: Reminder) = viewModelScope.launch {
        repository.deleteReminder(reminder)
        ReminderScheduler.cancelReminder(getApplication(), reminder)
        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                firestoreRepo.deleteReminder("rem_${reminder.id}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getWeightForRabbit(rabbitId: Int) = repository.getWeightRecordsForRabbit(rabbitId)

    fun addWeightRecord(weightRecord: WeightRecord) = viewModelScope.launch {
        val newId = repository.insertWeightRecord(weightRecord)
        val rabbit = repository.getRabbitById(weightRecord.rabbitId)
        val updatedRabbit = if (rabbit != null) {
            val updated = rabbit.copy(weight = weightRecord.weight)
            repository.updateRabbit(updated)
            updated
        } else null

        val user = firebaseAuth.currentUser
        if (user != null && !isSyncingFromCloud) {
            try {
                if (updatedRabbit != null) {
                    firestoreRepo.saveRabbit(updatedRabbit.toFirestore(user.uid))
                }
                firestoreRepo.saveWeight(weightRecord.copy(id = newId.toInt()).toFirestore(user.uid))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---- Photo Records Manager Operations ----
    fun getPhotoRecordsForRabbit(rabbitId: Int) = repository.getPhotoRecordsForRabbit(rabbitId)

    fun addPhotoRecord(photoRecord: PhotoRecord) = viewModelScope.launch {
        repository.insertPhotoRecord(photoRecord)
        // Also update the main imageUri of the rabbit to the updated photo
        val rabbit = repository.getRabbitById(photoRecord.rabbitId)
        if (rabbit != null) {
            repository.updateRabbit(rabbit.copy(imageUri = photoRecord.imageUri))
        }
    }

    fun deletePhotoRecord(photoRecord: PhotoRecord) = viewModelScope.launch {
        repository.deletePhotoRecord(photoRecord)
    }

    // ---- Backup and Restore operations ----
    suspend fun exportBackupJson(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val root = org.json.JSONObject()
        
        // 1. Rabbits
        val rabbitsArr = org.json.JSONArray()
        repository.getAllRabbits().forEach { r ->
            val obj = org.json.JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("sex", r.sex)
                put("dateOfBirth", r.dateOfBirth)
                put("breed", r.breed)
                put("cageNumber", r.cageNumber)
                put("weight", r.weight)
                put("motherId", r.motherId ?: org.json.JSONObject.NULL)
                put("fatherId", r.fatherId ?: org.json.JSONObject.NULL)
                put("imageUri", r.imageUri ?: "")
                put("isActive", r.isActive)
                put("status", r.status)
                put("notes", r.notes)
            }
            rabbitsArr.put(obj)
        }
        root.put("rabbits", rabbitsArr)

        // 2. Mating records
        val matingArr = org.json.JSONArray()
        repository.getAllMatingRecords().forEach { m ->
            val obj = org.json.JSONObject().apply {
                put("id", m.id)
                put("maleId", m.maleId)
                put("femaleId", m.femaleId)
                put("matingDate", m.matingDate)
                put("expectedBirthDate", m.expectedBirthDate)
                put("status", m.status)
                put("notes", m.notes)
            }
            matingArr.put(obj)
        }
        root.put("matingRecords", matingArr)

        // 3. Inventory items
        val invArr = org.json.JSONArray()
        repository.getAllInventoryItems().forEach { i ->
            val obj = org.json.JSONObject().apply {
                put("id", i.id)
                put("name", i.name)
                put("category", i.category)
                put("quantity", i.quantity)
                put("unit", i.unit)
                put("pricePerUnit", i.pricePerUnit)
                put("totalPrice", i.totalPrice)
                put("purchaseDate", i.purchaseDate)
                put("notes", i.notes)
            }
            invArr.put(obj)
        }
        root.put("inventoryItems", invArr)

        // 4. Reminders
        val remArr = org.json.JSONArray()
        repository.getAllReminders().forEach { r ->
            val obj = org.json.JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("description", r.description)
                put("dueDate", r.dueDate)
                put("priority", r.priority)
                put("isCompleted", r.isCompleted)
            }
            remArr.put(obj)
        }
        root.put("reminders", remArr)

        // 5. Treatment Records
        val treatArr = org.json.JSONArray()
        repository.getAllTreatmentRecords().forEach { t ->
            val obj = org.json.JSONObject().apply {
                put("id", t.id)
                put("rabbitId", t.rabbitId)
                put("type", t.type)
                put("designation", t.designation)
                put("date", t.date)
                put("dosage", t.dosage)
                put("notes", t.notes)
                put("nextDueDate", t.nextDueDate ?: org.json.JSONObject.NULL)
                put("withdrawalDays", t.withdrawalDays)
                put("withdrawalEndDate", t.withdrawalEndDate ?: org.json.JSONObject.NULL)
            }
            treatArr.put(obj)
        }
        root.put("treatmentRecords", treatArr)

        // 6. Daily Feed Logs
        val feedArr = org.json.JSONArray()
        repository.getAllDailyFeedLogs().forEach { f ->
            val obj = org.json.JSONObject().apply {
                put("id", f.id)
                put("quantityGrams", f.quantityGrams)
                put("feedDate", f.feedDate)
                put("notes", f.notes)
            }
            feedArr.put(obj)
        }
        root.put("dailyFeedLogs", feedArr)

        // 7. Weight Records
        val weightArr = org.json.JSONArray()
        repository.getAllWeightRecords().forEach { w ->
            val obj = org.json.JSONObject().apply {
                put("id", w.id)
                put("rabbitId", w.rabbitId)
                put("weight", w.weight)
                put("recordDate", w.recordDate)
                put("notes", w.notes)
            }
            weightArr.put(obj)
        }
        root.put("weightRecords", weightArr)

        // 8. Photo Records
        val photoArr = org.json.JSONArray()
        repository.getAllPhotoRecords().forEach { p ->
            val obj = org.json.JSONObject().apply {
                put("id", p.id)
                put("rabbitId", p.rabbitId)
                put("imageUri", p.imageUri)
                put("recordDate", p.recordDate)
                put("note", p.note)
            }
            photoArr.put(obj)
        }
        root.put("photoRecords", photoArr)

        // 9. Fattening Batches
        val batchArr = org.json.JSONArray()
        repository.getAllFatteningBatches().forEach { b ->
            val obj = org.json.JSONObject().apply {
                put("id", b.id)
                put("batchCode", b.batchCode)
                put("matingRecordId", b.matingRecordId ?: org.json.JSONObject.NULL)
                put("weaningDate", b.weaningDate)
                put("initialCount", b.initialCount)
                put("currentCount", b.currentCount)
                put("mortalityCount", b.mortalityCount)
                put("cageLocation", b.cageLocation)
                put("averageWeightGrams", b.averageWeightGrams)
                put("targetSaleDate", b.targetSaleDate)
                put("status", b.status)
            }
            batchArr.put(obj)
        }
        root.put("fatteningBatches", batchArr)

        // 10. Financial Transactions
        val txArr = org.json.JSONArray()
        repository.getAllFinancialTransactions().forEach { tx ->
            val obj = org.json.JSONObject().apply {
                put("id", tx.id)
                put("date", tx.date)
                put("type", tx.type)
                put("category", tx.category)
                put("amountFcfa", tx.amountFcfa)
                put("description", tx.description)
                put("relatedBatchId", tx.relatedBatchId ?: org.json.JSONObject.NULL)
                put("relatedRabbitId", tx.relatedRabbitId ?: org.json.JSONObject.NULL)
            }
            txArr.put(obj)
        }
        root.put("financialTransactions", txArr)

        root.toString(2)
    }

    fun rewriteUriPath(context: android.content.Context, uriString: String?): String? {
        if (uriString == null || uriString.isEmpty()) return null
        if (uriString.contains("/cache/images/") || uriString.contains("/files/images/")) {
            val fileName = uriString.substringAfterLast("/")
            val currentImagesDir = java.io.File(context.filesDir, "images")
            if (!currentImagesDir.exists()) {
                currentImagesDir.mkdirs()
            }
            val currentFile = java.io.File(currentImagesDir, fileName)
            // Migrate from legacy cacheDir if needed
            if (!currentFile.exists()) {
                val legacyFile = java.io.File(java.io.File(context.cacheDir, "images"), fileName)
                if (legacyFile.exists()) {
                    try {
                        legacyFile.copyTo(currentFile, overwrite = true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            return android.net.Uri.fromFile(currentFile).toString()
        }
        return uriString
    }

    suspend fun restoreBackupRaw(jsonString: String): Boolean {
        return try {
            val root = org.json.JSONObject(jsonString)
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.clearAllTables()
                
                // 1. Rabbits
                if (root.has("rabbits")) {
                    val arr = root.getJSONArray("rabbits")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val originalUri = if (obj.has("imageUri") && obj.getString("imageUri").isNotEmpty()) obj.getString("imageUri") else null
                        val currentUri = rewriteUriPath(getApplication(), originalUri)
                        repository.insertRabbit(Rabbit(
                            id = obj.optInt("id", 0),
                            name = obj.optString("name", "Inconnu"),
                            sex = obj.optString("sex", "Male"),
                            dateOfBirth = obj.optLong("dateOfBirth", System.currentTimeMillis()),
                            breed = obj.optString("breed", ""),
                            cageNumber = obj.optString("cageNumber", ""),
                            weight = obj.optDouble("weight", 0.0),
                            motherId = if (obj.has("motherId") && !obj.isNull("motherId")) obj.optInt("motherId") else null,
                            fatherId = if (obj.has("fatherId") && !obj.isNull("fatherId")) obj.optInt("fatherId") else null,
                            imageUri = currentUri,
                            isActive = obj.optBoolean("isActive", true),
                            status = obj.optString("status", if (obj.optBoolean("isActive", true)) "Actif" else "Inactif"),
                            notes = obj.optString("notes", "")
                        ))
                    }
                }

                // 2. Mating records
                if (root.has("matingRecords")) {
                    val arr = root.getJSONArray("matingRecords")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertMatingRecord(MatingRecord(
                            id = obj.optInt("id", 0),
                            maleId = obj.optInt("maleId", 0),
                            femaleId = obj.optInt("femaleId", 0),
                            matingDate = obj.optLong("matingDate", System.currentTimeMillis()),
                            expectedBirthDate = obj.optLong("expectedBirthDate", System.currentTimeMillis()),
                            status = obj.optString("status", "Accouplé"),
                            notes = obj.optString("notes", "")
                        ))
                    }
                }

                // 3. Inventory items
                if (root.has("inventoryItems")) {
                    val arr = root.getJSONArray("inventoryItems")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertInventoryItem(InventoryItem(
                            id = obj.optInt("id", 0),
                            name = obj.optString("name", ""),
                            category = obj.optString("category", ""),
                            quantity = obj.optDouble("quantity", 0.0),
                            unit = obj.optString("unit", ""),
                            pricePerUnit = obj.optDouble("pricePerUnit", 0.0),
                            totalPrice = obj.optDouble("totalPrice", 0.0),
                            purchaseDate = obj.optLong("purchaseDate", System.currentTimeMillis()),
                            notes = obj.optString("notes", "")
                        ))
                    }
                }

                // 4. Reminders
                if (root.has("reminders")) {
                    val arr = root.getJSONArray("reminders")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val r = Reminder(
                            id = obj.optInt("id", 0),
                            title = obj.optString("title", ""),
                            description = obj.optString("description", ""),
                            dueDate = obj.optLong("dueDate", System.currentTimeMillis()),
                            priority = obj.optString("priority", "Moyenne"),
                            isCompleted = obj.optBoolean("isCompleted", false)
                        )
                        repository.insertReminder(r)
                        if (!r.isCompleted) {
                            ReminderScheduler.scheduleReminder(getApplication(), r)
                        }
                    }
                }

                // 5. Treatment Records
                if (root.has("treatmentRecords")) {
                    val arr = root.getJSONArray("treatmentRecords")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertTreatmentRecord(TreatmentRecord(
                            id = obj.optInt("id", 0),
                            rabbitId = obj.optInt("rabbitId", 0),
                            type = obj.optString("type", ""),
                            designation = obj.optString("designation", ""),
                            date = obj.optLong("date", System.currentTimeMillis()),
                            dosage = obj.optString("dosage", ""),
                            notes = obj.optString("notes", ""),
                            nextDueDate = if (obj.has("nextDueDate") && !obj.isNull("nextDueDate")) obj.optLong("nextDueDate") else null,
                            withdrawalDays = obj.optInt("withdrawalDays", 0),
                            withdrawalEndDate = if (obj.has("withdrawalEndDate") && !obj.isNull("withdrawalEndDate")) obj.optLong("withdrawalEndDate") else null
                        ))
                    }
                }

                // 6. Daily Feed Logs
                if (root.has("dailyFeedLogs")) {
                    val arr = root.getJSONArray("dailyFeedLogs")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertDailyFeedLog(DailyFeedLog(
                            id = obj.optInt("id", 0),
                            quantityGrams = obj.optDouble("quantityGrams", 0.0),
                            feedDate = obj.optLong("feedDate", System.currentTimeMillis()),
                            notes = obj.optString("notes", "")
                        ))
                    }
                }

                // 7. Weight Records
                if (root.has("weightRecords")) {
                    val arr = root.getJSONArray("weightRecords")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertWeightRecord(WeightRecord(
                            id = obj.optInt("id", 0),
                            rabbitId = obj.optInt("rabbitId", 0),
                            weight = obj.optDouble("weight", 0.0),
                            recordDate = obj.optLong("recordDate", System.currentTimeMillis()),
                            notes = obj.optString("notes", "")
                        ))
                    }
                }

                // 8. Photo Records
                if (root.has("photoRecords")) {
                    val arr = root.getJSONArray("photoRecords")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val originalUri = obj.optString("imageUri", "")
                        val currentUri = rewriteUriPath(getApplication(), originalUri) ?: ""
                        repository.insertPhotoRecord(PhotoRecord(
                            id = obj.optInt("id", 0),
                            rabbitId = obj.optInt("rabbitId", 0),
                            imageUri = currentUri,
                            recordDate = obj.optLong("recordDate", System.currentTimeMillis()),
                            note = obj.optString("note", "")
                        ))
                    }
                }

                // 9. Fattening Batches
                if (root.has("fatteningBatches")) {
                    val arr = root.getJSONArray("fatteningBatches")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertFatteningBatch(FatteningBatch(
                            id = obj.optLong("id", 0L),
                            batchCode = obj.optString("batchCode", ""),
                            matingRecordId = if (obj.has("matingRecordId") && !obj.isNull("matingRecordId")) obj.optInt("matingRecordId") else null,
                            weaningDate = obj.optLong("weaningDate", System.currentTimeMillis()),
                            initialCount = obj.optInt("initialCount", 0),
                            currentCount = obj.optInt("currentCount", 0),
                            mortalityCount = obj.optInt("mortalityCount", 0),
                            cageLocation = obj.optString("cageLocation", ""),
                            averageWeightGrams = obj.optInt("averageWeightGrams", 0),
                            targetSaleDate = obj.optLong("targetSaleDate", System.currentTimeMillis()),
                            status = obj.optString("status", "ACTIVE")
                        ))
                    }
                }

                // 10. Financial Transactions
                if (root.has("financialTransactions")) {
                    val arr = root.getJSONArray("financialTransactions")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertFinancialTransaction(FinancialTransaction(
                            id = obj.optLong("id", 0L),
                            date = obj.optLong("date", System.currentTimeMillis()),
                            type = obj.optString("type", "EXPENSE"),
                            category = obj.optString("category", "ALIMENT"),
                            amountFcfa = obj.optDouble("amountFcfa", 0.0),
                            description = obj.optString("description", ""),
                            relatedBatchId = if (obj.has("relatedBatchId") && !obj.isNull("relatedBatchId")) obj.optLong("relatedBatchId") else null,
                            relatedRabbitId = if (obj.has("relatedRabbitId") && !obj.isNull("relatedRabbitId")) obj.optInt("relatedRabbitId") else null
                        ))
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Exports a ZIP file containing the full database JSON and all rabbit photos.
     * Returns the Uri of the exported ZIP file.
     */
    suspend fun exportBackupZip(context: android.content.Context): android.net.Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val jsonString = exportBackupJson()
            
            val imagesDir = java.io.File(context.filesDir, "images")
            val imageFiles = if (imagesDir.exists() && imagesDir.isDirectory) {
                imagesDir.listFiles() ?: emptyArray()
            } else {
                val legacyImagesDir = java.io.File(context.cacheDir, "images")
                if (legacyImagesDir.exists() && legacyImagesDir.isDirectory) {
                    legacyImagesDir.listFiles() ?: emptyArray()
                } else {
                    emptyArray()
                }
            }
            
            val zipFile = java.io.File(context.filesDir, "ABVFarm_Sauvegarde_${System.currentTimeMillis()}.zip")
            if (zipFile.exists()) {
                zipFile.delete()
            }
            
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                val jsonEntry = java.util.zip.ZipEntry("backup.json")
                zos.putNextEntry(jsonEntry)
                zos.write(jsonString.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                
                imageFiles.forEach { file ->
                    if (file.isFile) {
                        val entryName = "images/" + file.name
                        val imageEntry = java.util.zip.ZipEntry(entryName)
                        zos.putNextEntry(imageEntry)
                        file.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            
            val authority = "${context.packageName}.fileprovider"
            androidx.core.content.FileProvider.getUriForFile(context, authority, zipFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Exports a clean CSV spreadsheet of all rabbits formatted with UTF-8 BOM for Excel/Sheets.
     */
    suspend fun exportRabbitsCsv(context: android.content.Context): android.net.Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            val csvBuilder = StringBuilder()
            // UTF-8 BOM for seamless opening in Microsoft Excel
            csvBuilder.append("\uFEFF")
            csvBuilder.append("ID;Nom;Sexe;Race;Cage;Poids (kg);Date Naissance;Pere;Mere;Statut;Notes\n")

            val rabbitList = repository.getAllRabbits()
            rabbitList.forEach { r ->
                val fatherName = rabbitList.find { it.id == r.fatherId }?.name ?: ""
                val motherName = rabbitList.find { it.id == r.motherId }?.name ?: ""
                val birthDateStr = sdf.format(java.util.Date(r.dateOfBirth))
                val cleanNotes = r.notes.replace(";", ",").replace("\n", " ")
                val cleanBreed = r.breed.replace(";", ",")
                val cleanCage = r.cageNumber.replace(";", ",")
                val cleanName = r.name.replace(";", ",")
                val status = r.status

                csvBuilder.append("${r.id};\"$cleanName\";\"${r.sex}\";\"$cleanBreed\";\"$cleanCage\";${String.format(java.util.Locale.US, "%.2f", r.weight)};\"$birthDateStr\";\"$fatherName\";\"$motherName\";\"$status\";\"$cleanNotes\"\n")
            }

            val fileName = "ABVFarm_Lapins_${java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())}.csv"
            val csvFile = java.io.File(context.filesDir, fileName)
            csvFile.writeText(csvBuilder.toString(), Charsets.UTF_8)

            val authority = "${context.packageName}.fileprovider"
            androidx.core.content.FileProvider.getUriForFile(context, authority, csvFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Restores backup from a selected ZIP file. Exports all images and reads the backup JSON.
     */
    suspend fun restoreBackupZip(context: android.content.Context, zipUri: android.net.Uri): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var jsonString: String? = null
            val imagesDir = java.io.File(context.filesDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            
            try {
                context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                    java.util.zip.ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == "backup.json") {
                                jsonString = zis.bufferedReader(Charsets.UTF_8).readText()
                            } else if (entry.name.startsWith("images/") && !entry.isDirectory) {
                                val fileName = entry.name.substringAfterLast("/")
                                if (fileName.isNotEmpty()) {
                                    val targetFile = java.io.File(imagesDir, fileName)
                                    java.io.FileOutputStream(targetFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                
                if (jsonString != null) {
                    restoreBackupRaw(jsonString!!)
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
