package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreFarmRepository(private val db: FirebaseFirestore) {

    // Convenience constructor resolving named database ID from strings
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun isUserSignedIn(): Boolean = auth.currentUser != null

    fun getCurrentUserEmail(): String? = auth.currentUser?.email
    fun getCurrentUserDisplayName(): String? = auth.currentUser?.displayName

    suspend fun recordUserSync(displayName: String?, email: String?) {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        val data = mapOf(
            "userId" to uid,
            "email" to email,
            "displayName" to displayName,
            "farmName" to "Élevage ABV Farm",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            userDoc.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
        }
    }

    // ---------------- RABBITS ----------------
    fun observeRabbits(): Flow<List<FirestoreRabbit>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/rabbits"
        emitAll(
            db.collection("users").document(uid).collection("rabbits")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreRabbit::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveRabbit(rabbit: FirestoreRabbit, docId: String? = null): String {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("rabbits")
        val targetId = if (!docId.isNullOrBlank()) docId else if (rabbit.id.isNotBlank()) rabbit.id else "rabbit_${System.currentTimeMillis()}"
        val docRef = col.document(targetId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "localId" to rabbit.localId,
            "name" to rabbit.name,
            "sex" to rabbit.sex,
            "dateOfBirth" to rabbit.dateOfBirth,
            "breed" to rabbit.breed,
            "cageNumber" to rabbit.cageNumber,
            "weight" to rabbit.weight,
            "notes" to rabbit.notes,
            "isActive" to rabbit.isActive,
            "status" to rabbit.status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (rabbit.fatherId != null) payload["fatherId"] = rabbit.fatherId
        if (rabbit.motherId != null) payload["motherId"] = rabbit.motherId
        if (!rabbit.imageUri.isNullOrBlank()) payload["imageUri"] = rabbit.imageUri

        try {
            val exists = try { docRef.get().await().exists() } catch (e: Exception) { false }
            if (!exists) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            return targetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteRabbit(docId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("rabbits").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ---------------- MATING RECORDS ----------------
    fun observeMatingRecords(): Flow<List<FirestoreMatingRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/mating_records"
        emitAll(
            db.collection("users").document(uid).collection("mating_records")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreMatingRecord::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveMatingRecord(record: FirestoreMatingRecord, docId: String? = null): String {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("mating_records")
        val targetId = if (!docId.isNullOrBlank()) docId else if (record.id.isNotBlank()) record.id else "mating_${System.currentTimeMillis()}"
        val docRef = col.document(targetId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "localId" to record.localId,
            "maleId" to record.maleId,
            "femaleId" to record.femaleId,
            "matingDate" to record.matingDate,
            "expectedBirthDate" to record.expectedBirthDate,
            "status" to record.status,
            "notes" to record.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val exists = try { docRef.get().await().exists() } catch (e: Exception) { false }
            if (!exists) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            return targetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMatingRecord(docId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("mating_records").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ---------------- INVENTORY ITEMS ----------------
    fun observeInventory(): Flow<List<FirestoreInventoryItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/inventory_items"
        emitAll(
            db.collection("users").document(uid).collection("inventory_items")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreInventoryItem::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveInventoryItem(item: FirestoreInventoryItem, docId: String? = null): String {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("inventory_items")
        val targetId = if (!docId.isNullOrBlank()) docId else if (item.id.isNotBlank()) item.id else "item_${System.currentTimeMillis()}"
        val docRef = col.document(targetId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "localId" to item.localId,
            "name" to item.name,
            "category" to item.category,
            "quantity" to item.quantity,
            "unit" to item.unit,
            "pricePerUnit" to item.pricePerUnit,
            "totalPrice" to item.totalPrice,
            "purchaseDate" to item.purchaseDate,
            "durationMonths" to item.durationMonths,
            "notes" to item.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val exists = try { docRef.get().await().exists() } catch (e: Exception) { false }
            if (!exists) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            return targetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteInventoryItem(docId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("inventory_items").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ---------------- REMINDERS ----------------
    fun observeReminders(): Flow<List<FirestoreReminder>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/reminders"
        emitAll(
            db.collection("users").document(uid).collection("reminders")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreReminder::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveReminder(reminder: FirestoreReminder, docId: String? = null): String {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("reminders")
        val targetId = if (!docId.isNullOrBlank()) docId else if (reminder.id.isNotBlank()) reminder.id else "rem_${System.currentTimeMillis()}"
        val docRef = col.document(targetId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "localId" to reminder.localId,
            "title" to reminder.title,
            "description" to reminder.description,
            "dueDate" to reminder.dueDate,
            "priority" to reminder.priority,
            "isCompleted" to reminder.isCompleted,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val exists = try { docRef.get().await().exists() } catch (e: Exception) { false }
            if (!exists) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            return targetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteReminder(docId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("reminders").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ---------------- TREATMENTS ----------------
    fun observeTreatments(): Flow<List<FirestoreTreatmentRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/treatments"
        emitAll(
            db.collection("users").document(uid).collection("treatments")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreTreatmentRecord::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveTreatment(treatment: FirestoreTreatmentRecord, docId: String? = null): String {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("treatments")
        val targetId = if (!docId.isNullOrBlank()) docId else if (treatment.id.isNotBlank()) treatment.id else "treat_${System.currentTimeMillis()}"
        val docRef = col.document(targetId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "localId" to treatment.localId,
            "rabbitId" to treatment.rabbitId,
            "type" to treatment.type,
            "designation" to treatment.designation,
            "date" to treatment.date,
            "dosage" to treatment.dosage,
            "notes" to treatment.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (treatment.nextDueDate != null) payload["nextDueDate"] = treatment.nextDueDate

        try {
            val exists = try { docRef.get().await().exists() } catch (e: Exception) { false }
            if (!exists) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            return targetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTreatment(docId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("treatments").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ---------------- WEIGHTS ----------------
    fun observeWeights(): Flow<List<FirestoreWeightRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/weights"
        emitAll(
            db.collection("users").document(uid).collection("weights")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreWeightRecord::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveWeight(weight: FirestoreWeightRecord, docId: String? = null): String {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("weights")
        val targetId = if (!docId.isNullOrBlank()) docId else if (weight.id.isNotBlank()) weight.id else "wt_${System.currentTimeMillis()}"
        val docRef = col.document(targetId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "localId" to weight.localId,
            "rabbitId" to weight.rabbitId,
            "weight" to weight.weight,
            "recordDate" to weight.recordDate,
            "notes" to weight.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val exists = try { docRef.get().await().exists() } catch (e: Exception) { false }
            if (!exists) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            return targetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteWeight(docId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("weights").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ---------------- FEED LOGS ----------------
    fun observeFeedLogs(): Flow<List<FirestoreDailyFeedLog>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/feed_logs"
        emitAll(
            db.collection("users").document(uid).collection("feed_logs")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreDailyFeedLog::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveFeedLog(feedLog: FirestoreDailyFeedLog, docId: String? = null): String {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("feed_logs")
        val targetId = if (!docId.isNullOrBlank()) docId else if (feedLog.id.isNotBlank()) feedLog.id else "feed_${System.currentTimeMillis()}"
        val docRef = col.document(targetId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "localId" to feedLog.localId,
            "feedDate" to feedLog.feedDate,
            "quantityGrams" to feedLog.quantityGrams,
            "notes" to feedLog.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val exists = try { docRef.get().await().exists() } catch (e: Exception) { false }
            if (!exists) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            return targetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFeedLog(docId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("feed_logs").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ---------------- FATTENING BATCHES ----------------
    fun observeFatteningBatches(): Flow<List<FirestoreFatteningBatch>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/fattening_batches"
        emitAll(
            db.collection("users").document(uid).collection("fattening_batches")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreFatteningBatch::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveFatteningBatch(batch: FirestoreFatteningBatch, docId: String? = null): String {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("fattening_batches")
        val targetId = if (!docId.isNullOrBlank()) docId else if (batch.id.isNotBlank()) batch.id else "batch_${System.currentTimeMillis()}"
        val docRef = col.document(targetId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "localId" to batch.localId,
            "batchCode" to batch.batchCode,
            "weaningDate" to batch.weaningDate,
            "initialCount" to batch.initialCount,
            "currentCount" to batch.currentCount,
            "mortalityCount" to batch.mortalityCount,
            "cageLocation" to batch.cageLocation,
            "averageWeightGrams" to batch.averageWeightGrams,
            "targetSaleDate" to batch.targetSaleDate,
            "status" to batch.status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (batch.matingRecordId != null) payload["matingRecordId"] = batch.matingRecordId

        try {
            val exists = try { docRef.get().await().exists() } catch (e: Exception) { false }
            if (!exists) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            return targetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFatteningBatch(docId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("fattening_batches").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ---------------- FINANCIAL TRANSACTIONS ----------------
    fun observeFinancialTransactions(): Flow<List<FirestoreFinancialTransaction>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/financial_transactions"
        emitAll(
            db.collection("users").document(uid).collection("financial_transactions")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreFinancialTransaction::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveFinancialTransaction(tx: FirestoreFinancialTransaction, docId: String? = null): String {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("financial_transactions")
        val targetId = if (!docId.isNullOrBlank()) docId else if (tx.id.isNotBlank()) tx.id else "tx_${System.currentTimeMillis()}"
        val docRef = col.document(targetId)

        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "localId" to tx.localId,
            "date" to tx.date,
            "type" to tx.type,
            "category" to tx.category,
            "amountFcfa" to tx.amountFcfa,
            "description" to tx.description,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (tx.relatedBatchId != null) payload["relatedBatchId"] = tx.relatedBatchId
        if (tx.relatedRabbitId != null) payload["relatedRabbitId"] = tx.relatedRabbitId

        try {
            val exists = try { docRef.get().await().exists() } catch (e: Exception) { false }
            if (!exists) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            return targetId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFinancialTransaction(docId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("financial_transactions").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
