package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmDao {
    // ---- Rabbits ----
    @Query("SELECT * FROM rabbits ORDER BY name ASC")
    fun getAllRabbitsFlow(): Flow<List<Rabbit>>

    @Query("SELECT * FROM rabbits WHERE id = :id LIMIT 1")
    suspend fun getRabbitById(id: Int): Rabbit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRabbit(rabbit: Rabbit): Long

    @Update
    suspend fun updateRabbit(rabbit: Rabbit)

    @Delete
    suspend fun deleteRabbit(rabbit: Rabbit)

    // ---- Mating Records ----
    @Query("SELECT * FROM mating_records ORDER BY matingDate DESC")
    fun getAllMatingRecordsFlow(): Flow<List<MatingRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatingRecord(record: MatingRecord): Long

    @Update
    suspend fun updateMatingRecord(record: MatingRecord)

    @Delete
    suspend fun deleteMatingRecord(record: MatingRecord)

    // ---- Inventory ----
    @Query("SELECT * FROM inventory_items ORDER BY purchaseDate DESC")
    fun getAllInventoryItemsFlow(): Flow<List<InventoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: InventoryItem): Long

    @Update
    suspend fun updateInventoryItem(item: InventoryItem)

    @Delete
    suspend fun deleteInventoryItem(item: InventoryItem)

    // ---- Reminders ----
    @Query("SELECT * FROM reminders ORDER BY dueDate ASC")
    fun getAllRemindersFlow(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0")
    suspend fun getAllPendingReminders(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE dueDate < :threshold AND isCompleted = 0")
    suspend fun deleteRemindersBefore(threshold: Long)

    // ---- Weight Records ----
    @Query("SELECT * FROM weight_records WHERE rabbitId = :rabbitId ORDER BY recordDate DESC")
    fun getWeightRecordsForRabbitFlow(rabbitId: Int): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records")
    suspend fun getAllWeightRecords(): List<WeightRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightRecord(weightRecord: WeightRecord): Long

    @Delete
    suspend fun deleteWeightRecord(weightRecord: WeightRecord)

    // ---- Photo Records ----
    @Query("SELECT * FROM photo_records WHERE rabbitId = :rabbitId ORDER BY recordDate DESC")
    fun getPhotoRecordsForRabbitFlow(rabbitId: Int): Flow<List<PhotoRecord>>

    @Query("SELECT * FROM photo_records")
    suspend fun getAllPhotoRecords(): List<PhotoRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotoRecord(photoRecord: PhotoRecord): Long

    @Delete
    suspend fun deletePhotoRecord(photoRecord: PhotoRecord)

    // ---- Treatment Records (Option 2) ----
    @Query("SELECT * FROM treatment_records ORDER BY date DESC")
    fun getAllTreatmentRecordsFlow(): Flow<List<TreatmentRecord>>

    @Query("SELECT * FROM treatment_records WHERE rabbitId = :rabbitId ORDER BY date DESC")
    fun getTreatmentsForRabbitFlow(rabbitId: Int): Flow<List<TreatmentRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTreatmentRecord(record: TreatmentRecord): Long

    @Delete
    suspend fun deleteTreatmentRecord(record: TreatmentRecord)

    // ---- Daily Feed Logs (Option 3) ----
    @Query("SELECT * FROM daily_feed_logs ORDER BY feedDate DESC")
    fun getAllDailyFeedLogsFlow(): Flow<List<DailyFeedLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyFeedLog(log: DailyFeedLog): Long

    @Delete
    suspend fun deleteDailyFeedLog(log: DailyFeedLog)

    // ---- Fattening Batches ----
    @Query("SELECT * FROM fattening_batches ORDER BY weaningDate DESC")
    fun getAllFatteningBatchesFlow(): Flow<List<FatteningBatch>>

    @Query("SELECT * FROM fattening_batches WHERE id = :id LIMIT 1")
    suspend fun getFatteningBatchById(id: Long): FatteningBatch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFatteningBatch(batch: FatteningBatch): Long

    @Update
    suspend fun updateFatteningBatch(batch: FatteningBatch)

    @Delete
    suspend fun deleteFatteningBatch(batch: FatteningBatch)

    // ---- Financial Transactions ----
    @Query("SELECT * FROM financial_transactions ORDER BY date DESC")
    fun getAllFinancialTransactionsFlow(): Flow<List<FinancialTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinancialTransaction(tx: FinancialTransaction): Long

    @Update
    suspend fun updateFinancialTransaction(tx: FinancialTransaction)

    @Delete
    suspend fun deleteFinancialTransaction(tx: FinancialTransaction)
}
