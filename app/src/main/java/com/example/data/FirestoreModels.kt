package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreRabbit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Int = 0,
    val name: String = "",
    val sex: String = "Indéterminé",
    val dateOfBirth: Long = 0L,
    val breed: String = "Inconnu",
    val cageNumber: String = "",
    val weight: Double = 0.0,
    val fatherId: Int? = null,
    val motherId: Int? = null,
    val notes: String = "",
    val isActive: Boolean = true,
    val status: String = "Actif",
    val imageUri: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun Rabbit.toFirestore(userId: String): FirestoreRabbit {
    return FirestoreRabbit(
        id = if (id > 0) "rabbit_$id" else "",
        userId = userId,
        localId = id,
        name = name,
        sex = sex,
        dateOfBirth = dateOfBirth,
        breed = breed,
        cageNumber = cageNumber,
        weight = weight,
        fatherId = fatherId,
        motherId = motherId,
        notes = notes,
        isActive = isActive,
        status = status,
        imageUri = imageUri
    )
}

fun FirestoreRabbit.toRoom(): Rabbit {
    return Rabbit(
        id = localId,
        name = name,
        sex = sex,
        dateOfBirth = dateOfBirth,
        breed = breed,
        cageNumber = cageNumber,
        weight = weight,
        fatherId = fatherId,
        motherId = motherId,
        notes = notes,
        isActive = isActive,
        status = status,
        imageUri = imageUri
    )
}

@IgnoreExtraProperties
data class FirestoreMatingRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Int = 0,
    val maleId: Int = 0,
    val femaleId: Int = 0,
    val matingDate: Long = 0L,
    val expectedBirthDate: Long = 0L,
    val status: String = "Accouplé",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun MatingRecord.toFirestore(userId: String): FirestoreMatingRecord {
    return FirestoreMatingRecord(
        id = if (id > 0) "mating_$id" else "",
        userId = userId,
        localId = id,
        maleId = maleId,
        femaleId = femaleId,
        matingDate = matingDate,
        expectedBirthDate = expectedBirthDate,
        status = status,
        notes = notes
    )
}

fun FirestoreMatingRecord.toRoom(): MatingRecord {
    return MatingRecord(
        id = localId,
        maleId = maleId,
        femaleId = femaleId,
        matingDate = matingDate,
        expectedBirthDate = expectedBirthDate,
        status = status,
        notes = notes
    )
}

@IgnoreExtraProperties
data class FirestoreInventoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Int = 0,
    val name: String = "",
    val category: String = "Autre",
    val quantity: Double = 0.0,
    val unit: String = "kg",
    val pricePerUnit: Double = 0.0,
    val totalPrice: Double = 0.0,
    val purchaseDate: Long = 0L,
    val durationMonths: Int = 1,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun InventoryItem.toFirestore(userId: String): FirestoreInventoryItem {
    return FirestoreInventoryItem(
        id = if (id > 0) "item_$id" else "",
        userId = userId,
        localId = id,
        name = name,
        category = category,
        quantity = quantity,
        unit = unit,
        pricePerUnit = pricePerUnit,
        totalPrice = totalPrice,
        purchaseDate = purchaseDate,
        durationMonths = durationMonths,
        notes = notes
    )
}

fun FirestoreInventoryItem.toRoom(): InventoryItem {
    return InventoryItem(
        id = localId,
        name = name,
        category = category,
        quantity = quantity,
        unit = unit,
        pricePerUnit = pricePerUnit,
        totalPrice = totalPrice,
        purchaseDate = purchaseDate,
        durationMonths = durationMonths,
        notes = notes
    )
}

@IgnoreExtraProperties
data class FirestoreReminder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Int = 0,
    val title: String = "",
    val description: String = "",
    val dueDate: Long = 0L,
    val priority: String = "Moyenne",
    val isCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun Reminder.toFirestore(userId: String): FirestoreReminder {
    return FirestoreReminder(
        id = if (id > 0) "rem_$id" else "",
        userId = userId,
        localId = id,
        title = title,
        description = description,
        dueDate = dueDate,
        priority = priority,
        isCompleted = isCompleted
    )
}

fun FirestoreReminder.toRoom(): Reminder {
    return Reminder(
        id = localId,
        title = title,
        description = description,
        dueDate = dueDate,
        priority = priority,
        isCompleted = isCompleted
    )
}

@IgnoreExtraProperties
data class FirestoreTreatmentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Int = 0,
    val rabbitId: Int = 0,
    val type: String = "Autre soin",
    val designation: String = "",
    val date: Long = 0L,
    val dosage: String = "",
    val notes: String = "",
    val nextDueDate: Long? = null,
    val withdrawalDays: Int = 0,
    val withdrawalEndDate: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun TreatmentRecord.toFirestore(userId: String): FirestoreTreatmentRecord {
    return FirestoreTreatmentRecord(
        id = if (id > 0) "treat_$id" else "",
        userId = userId,
        localId = id,
        rabbitId = rabbitId,
        type = type,
        designation = designation,
        date = date,
        dosage = dosage,
        notes = notes,
        nextDueDate = nextDueDate,
        withdrawalDays = withdrawalDays,
        withdrawalEndDate = withdrawalEndDate
    )
}

fun FirestoreTreatmentRecord.toRoom(): TreatmentRecord {
    return TreatmentRecord(
        id = localId,
        rabbitId = rabbitId,
        type = type,
        designation = designation,
        date = date,
        dosage = dosage,
        notes = notes,
        nextDueDate = nextDueDate,
        withdrawalDays = withdrawalDays,
        withdrawalEndDate = withdrawalEndDate
    )
}

@IgnoreExtraProperties
data class FirestoreWeightRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Int = 0,
    val rabbitId: Int = 0,
    val weight: Double = 0.0,
    val recordDate: Long = 0L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun WeightRecord.toFirestore(userId: String): FirestoreWeightRecord {
    return FirestoreWeightRecord(
        id = if (id > 0) "wt_$id" else "",
        userId = userId,
        localId = id,
        rabbitId = rabbitId,
        weight = weight,
        recordDate = recordDate,
        notes = notes
    )
}

fun FirestoreWeightRecord.toRoom(): WeightRecord {
    return WeightRecord(
        id = localId,
        rabbitId = rabbitId,
        weight = weight,
        recordDate = recordDate,
        notes = notes
    )
}

@IgnoreExtraProperties
data class FirestoreDailyFeedLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Int = 0,
    val quantityGrams: Double = 0.0,
    val feedDate: Long = 0L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun DailyFeedLog.toFirestore(userId: String): FirestoreDailyFeedLog {
    return FirestoreDailyFeedLog(
        id = if (id > 0) "feed_$id" else "",
        userId = userId,
        localId = id,
        quantityGrams = quantityGrams,
        feedDate = feedDate,
        notes = notes
    )
}

fun FirestoreDailyFeedLog.toRoom(): DailyFeedLog {
    return DailyFeedLog(
        id = localId,
        quantityGrams = quantityGrams,
        feedDate = feedDate,
        notes = notes
    )
}

@IgnoreExtraProperties
data class FirestoreFatteningBatch(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val batchCode: String = "",
    val matingRecordId: Int? = null,
    val weaningDate: Long = 0L,
    val initialCount: Int = 0,
    val currentCount: Int = 0,
    val mortalityCount: Int = 0,
    val cageLocation: String = "",
    val averageWeightGrams: Int = 0,
    val targetSaleDate: Long = 0L,
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun FatteningBatch.toFirestore(userId: String): FirestoreFatteningBatch {
    return FirestoreFatteningBatch(
        id = if (id > 0) "batch_$id" else "",
        userId = userId,
        localId = id,
        batchCode = batchCode,
        matingRecordId = matingRecordId,
        weaningDate = weaningDate,
        initialCount = initialCount,
        currentCount = currentCount,
        mortalityCount = mortalityCount,
        cageLocation = cageLocation,
        averageWeightGrams = averageWeightGrams,
        targetSaleDate = targetSaleDate,
        status = status
    )
}

fun FirestoreFatteningBatch.toRoom(): FatteningBatch {
    return FatteningBatch(
        id = localId,
        batchCode = batchCode,
        matingRecordId = matingRecordId,
        weaningDate = weaningDate,
        initialCount = initialCount,
        currentCount = currentCount,
        mortalityCount = mortalityCount,
        cageLocation = cageLocation,
        averageWeightGrams = averageWeightGrams,
        targetSaleDate = targetSaleDate,
        status = status
    )
}

@IgnoreExtraProperties
data class FirestoreFinancialTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val date: Long = 0L,
    val type: String = "EXPENSE",
    val category: String = "ALIMENT",
    val amountFcfa: Double = 0.0,
    val description: String = "",
    val relatedBatchId: Long? = null,
    val relatedRabbitId: Int? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun FinancialTransaction.toFirestore(userId: String): FirestoreFinancialTransaction {
    return FirestoreFinancialTransaction(
        id = if (id > 0) "tx_$id" else "",
        userId = userId,
        localId = id,
        date = date,
        type = type,
        category = category,
        amountFcfa = amountFcfa,
        description = description,
        relatedBatchId = relatedBatchId,
        relatedRabbitId = relatedRabbitId
    )
}

fun FirestoreFinancialTransaction.toRoom(): FinancialTransaction {
    return FinancialTransaction(
        id = localId,
        date = date,
        type = type,
        category = category,
        amountFcfa = amountFcfa,
        description = description,
        relatedBatchId = relatedBatchId,
        relatedRabbitId = relatedRabbitId
    )
}
