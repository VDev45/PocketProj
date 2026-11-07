package com.vishwaravi.pocketproj.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LockerNoteDao {
    @Query("SELECT * FROM locker_notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LockerNoteEntity>>

    @Query("SELECT * FROM locker_notes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): LockerNoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: LockerNoteEntity)

    /** Returns the number of rows changed, so a caller can tell "updated" from "already gone". */
    @Update
    suspend fun update(note: LockerNoteEntity): Int

    @Query("DELETE FROM locker_notes WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM locker_notes")
    suspend fun deleteAll(): Int
}
