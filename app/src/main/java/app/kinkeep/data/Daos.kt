package app.kinkeep.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(contact: ContactEntity): Long
    @Update suspend fun update(contact: ContactEntity)
    @Query("SELECT * FROM contacts WHERE circle != 'IGNORED' ORDER BY displayName") fun observeTracked(): Flow<List<ContactEntity>>
    @Query("SELECT * FROM contacts WHERE circle != 'IGNORED'") suspend fun getTracked(): List<ContactEntity>
    @Query("SELECT * FROM contacts") suspend fun all(): List<ContactEntity>
    @Query("SELECT * FROM contacts WHERE circle = 'IGNORED' ORDER BY displayName") fun observeUntracked(): Flow<List<ContactEntity>>
    @Query("SELECT * FROM contacts WHERE id = :id") fun observeById(id: Long): Flow<ContactEntity?>
    @Query("SELECT * FROM contacts WHERE id = :id") suspend fun get(id: Long): ContactEntity?
    @Query("SELECT * FROM contacts WHERE androidContactId = :androidId") suspend fun getByAndroidId(androidId: Long): ContactEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertNumbers(numbers: List<PhoneNumberEntity>)
    @Query("SELECT * FROM phone_numbers") suspend fun allNumbers(): List<PhoneNumberEntity>
    @Query("SELECT * FROM phone_numbers WHERE contactId = :contactId ORDER BY id LIMIT 3") suspend fun numbersFor(contactId: Long): List<PhoneNumberEntity>
}

@Dao
interface InteractionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(items: List<InteractionEntity>)
    @Query("SELECT * FROM interactions WHERE contactId = :contactId ORDER BY timestamp DESC") fun observeFor(contactId: Long): Flow<List<InteractionEntity>>
    @Query("SELECT * FROM interactions WHERE timestamp >= :since") fun observeSince(since: Long): Flow<List<InteractionEntity>>
    @Query("SELECT * FROM interactions WHERE timestamp >= :since") suspend fun sinceList(since: Long): List<InteractionEntity>
    @Query("SELECT COUNT(*) FROM interactions WHERE contactId = :contactId AND timestamp >= :since AND direction != 'MISSED'")
    suspend fun countSince(contactId: Long, since: Long): Int
}

@Dao
interface ReminderStateDao {
    @Query("SELECT * FROM reminder_state") suspend fun all(): List<ReminderStateEntity>
    @Query("SELECT * FROM reminder_state WHERE contactId = :id") suspend fun get(id: Long): ReminderStateEntity?
    @Query("SELECT * FROM reminder_state WHERE contactId = :id") fun observe(id: Long): Flow<ReminderStateEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(state: ReminderStateEntity)
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM contacts") suspend fun contacts(): List<ContactEntity>
    @Query("SELECT * FROM phone_numbers") suspend fun numbers(): List<PhoneNumberEntity>
    @Query("SELECT * FROM interactions") suspend fun interactions(): List<InteractionEntity>
    @Query("SELECT * FROM reminder_state") suspend fun states(): List<ReminderStateEntity>

    @Query("DELETE FROM interactions") suspend fun deleteInteractions()
    @Query("DELETE FROM phone_numbers") suspend fun deleteNumbers()
    @Query("DELETE FROM reminder_state") suspend fun deleteStates()
    @Query("DELETE FROM contacts") suspend fun deleteContacts()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertContacts(items: List<ContactEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNumbers(items: List<PhoneNumberEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertInteractions(items: List<InteractionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStates(items: List<ReminderStateEntity>)
}
