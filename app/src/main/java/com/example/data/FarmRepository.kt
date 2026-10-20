package com.example.data

import kotlinx.coroutines.flow.Flow

class FarmRepository(private val farmDao: FarmDao) {

    val rabbits: Flow<List<Rabbit>> = farmDao.getAllRabbitsFlow()
    val matingRecords: Flow<List<MatingRecord>> = farmDao.getAllMatingRecordsFlow()
    val inventoryItems: Flow<List<InventoryItem>> = farmDao.getAllInventoryItemsFlow()
    val reminders: Flow<List<Reminder>> = farmDao.getAllRemindersFlow()

    suspend fun getRabbitById(id: Int): Rabbit? = farmDao.getRabbitById(id)

    suspend fun insertRabbit(rabbit: Rabbit): Long = farmDao.insertRabbit(rabbit)
    suspend fun updateRabbit(rabbit: Rabbit) = farmDao.updateRabbit(rabbit)
    suspend fun deleteRabbit(rabbit: Rabbit) = farmDao.deleteRabbit(rabbit)

    suspend fun insertMatingRecord(record: MatingRecord): Long = farmDao.insertMatingRecord(record)
    suspend fun updateMatingRecord(record: MatingRecord) = farmDao.updateMatingRecord(record)
    suspend fun deleteMatingRecord(record: MatingRecord) = farmDao.deleteMatingRecord(record)

    suspend fun insertInventoryItem(item: InventoryItem): Long = farmDao.insertInventoryItem(item)
    suspend fun updateInventoryItem(item: InventoryItem) = farmDao.updateInventoryItem(item)
    suspend fun deleteInventoryItem(item: InventoryItem) = farmDao.deleteInventoryItem(item)

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

    fun getTreatmentsForRabbit(rabbitId: Int): Flow<List<TreatmentRecord>> =
        farmDao.getTreatmentsForRabbitFlow(rabbitId)

    suspend fun insertTreatmentRecord(record: TreatmentRecord): Long =
        farmDao.insertTreatmentRecord(record)

    suspend fun deleteTreatmentRecord(record: TreatmentRecord) =
        farmDao.deleteTreatmentRecord(record)

    // ---- Daily Feed Logs ----
    val dailyFeedLogs: Flow<List<DailyFeedLog>> = farmDao.getAllDailyFeedLogsFlow()

    suspend fun insertDailyFeedLog(log: DailyFeedLog): Long =
        farmDao.insertDailyFeedLog(log)

    suspend fun deleteDailyFeedLog(log: DailyFeedLog) =
        farmDao.deleteDailyFeedLog(log)
}
