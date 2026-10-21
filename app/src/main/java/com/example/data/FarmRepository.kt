package com.example.data

import kotlinx.coroutines.flow.Flow

class FarmRepository(private val farmDao: FarmDao) {

    val rabbits: Flow<List<Rabbit>> = farmDao.getAllRabbitsFlow()
    val matingRecords: Flow<List<MatingRecord>> = farmDao.getAllMatingRecordsFlow()
    val inventoryItems: Flow<List<InventoryItem>> = farmDao.getAllInventoryItemsFlow()
    val reminders: Flow<List<Reminder>> = farmDao.getAllRemindersFlow()

    suspend fun getAllRabbits(): List<Rabbit> = farmDao.getAllRabbits()
    suspend fun getRabbitById(id: Int): Rabbit? = farmDao.getRabbitById(id)
    suspend fun getMaxRabbitId(): Int? = farmDao.getMaxRabbitId()

    suspend fun insertRabbit(rabbit: Rabbit): Long = farmDao.insertRabbit(rabbit)
    suspend fun updateRabbit(rabbit: Rabbit) = farmDao.updateRabbit(rabbit)
    suspend fun deleteRabbit(rabbit: Rabbit) = farmDao.deleteRabbit(rabbit)

    suspend fun getAllMatingRecords(): List<MatingRecord> = farmDao.getAllMatingRecords()
    suspend fun insertMatingRecord(record: MatingRecord): Long = farmDao.insertMatingRecord(record)
    suspend fun updateMatingRecord(record: MatingRecord) = farmDao.updateMatingRecord(record)
    suspend fun deleteMatingRecord(record: MatingRecord) = farmDao.deleteMatingRecord(record)

    suspend fun getAllInventoryItems(): List<InventoryItem> = farmDao.getAllInventoryItems()
    suspend fun insertInventoryItem(item: InventoryItem): Long = farmDao.insertInventoryItem(item)
    suspend fun updateInventoryItem(item: InventoryItem) = farmDao.updateInventoryItem(item)
    suspend fun deleteInventoryItem(item: InventoryItem) = farmDao.deleteInventoryItem(item)

    suspend fun getAllReminders(): List<Reminder> = farmDao.getAllReminders()
    suspend fun insertReminder(reminder: Reminder): Long = farmDao.insertReminder(reminder)
    suspend fun updateReminder(reminder: Reminder) = farmDao.updateReminder(reminder)
    suspend fun deleteReminder(reminder: Reminder) = farmDao.deleteReminder(reminder)
    suspend fun deleteRemindersBefore(threshold: Long) = farmDao.deleteRemindersBefore(threshold)

    fun getWeightRecordsForRabbit(rabbitId: Int): Flow<List<WeightRecord>> =
        farmDao.getWeightRecordsForRabbitFlow(rabbitId)

    suspend fun getAllWeightRecords(): List<WeightRecord> = farmDao.getAllWeightRecords()

    suspend fun insertWeightRecord(weightRecord: WeightRecord): Long =
        farmDao.insertWeightRecord(weightRecord)

    suspend fun deleteWeightRecord(weightRecord: WeightRecord) =
        farmDao.deleteWeightRecord(weightRecord)

    fun getPhotoRecordsForRabbit(rabbitId: Int): Flow<List<PhotoRecord>> =
        farmDao.getPhotoRecordsForRabbitFlow(rabbitId)

    suspend fun getAllPhotoRecords(): List<PhotoRecord> = farmDao.getAllPhotoRecords()

    suspend fun insertPhotoRecord(photoRecord: PhotoRecord): Long =
        farmDao.insertPhotoRecord(photoRecord)

    suspend fun deletePhotoRecord(photoRecord: PhotoRecord) =
        farmDao.deletePhotoRecord(photoRecord)

    // ---- Treatment Records ----
    val treatmentRecords: Flow<List<TreatmentRecord>> = farmDao.getAllTreatmentRecordsFlow()

    suspend fun getAllTreatmentRecords(): List<TreatmentRecord> =
        farmDao.getAllTreatmentRecords()

    fun getTreatmentsForRabbit(rabbitId: Int): Flow<List<TreatmentRecord>> =
        farmDao.getTreatmentsForRabbitFlow(rabbitId)

    suspend fun insertTreatmentRecord(record: TreatmentRecord): Long =
        farmDao.insertTreatmentRecord(record)

    suspend fun deleteTreatmentRecord(record: TreatmentRecord) =
        farmDao.deleteTreatmentRecord(record)

    // ---- Daily Feed Logs ----
    val dailyFeedLogs: Flow<List<DailyFeedLog>> = farmDao.getAllDailyFeedLogsFlow()

    suspend fun getAllDailyFeedLogs(): List<DailyFeedLog> =
        farmDao.getAllDailyFeedLogs()

    suspend fun insertDailyFeedLog(log: DailyFeedLog): Long =
        farmDao.insertDailyFeedLog(log)

    suspend fun deleteDailyFeedLog(log: DailyFeedLog) =
        farmDao.deleteDailyFeedLog(log)

    // ---- Fattening Batches ----
    val fatteningBatches: Flow<List<FatteningBatch>> = farmDao.getAllFatteningBatchesFlow()

    suspend fun getAllFatteningBatches(): List<FatteningBatch> =
        farmDao.getAllFatteningBatches()

    suspend fun getFatteningBatchById(id: Long): FatteningBatch? =
        farmDao.getFatteningBatchById(id)

    suspend fun insertFatteningBatch(batch: FatteningBatch): Long =
        farmDao.insertFatteningBatch(batch)

    suspend fun updateFatteningBatch(batch: FatteningBatch) =
        farmDao.updateFatteningBatch(batch)

    suspend fun deleteFatteningBatch(batch: FatteningBatch) =
        farmDao.deleteFatteningBatch(batch)

    // ---- Financial Transactions ----
    val financialTransactions: Flow<List<FinancialTransaction>> = farmDao.getAllFinancialTransactionsFlow()

    suspend fun getAllFinancialTransactions(): List<FinancialTransaction> =
        farmDao.getAllFinancialTransactions()

    suspend fun insertFinancialTransaction(tx: FinancialTransaction): Long =
        farmDao.insertFinancialTransaction(tx)

    suspend fun updateFinancialTransaction(tx: FinancialTransaction) =
        farmDao.updateFinancialTransaction(tx)

    suspend fun deleteFinancialTransaction(tx: FinancialTransaction) =
        farmDao.deleteFinancialTransaction(tx)
}
