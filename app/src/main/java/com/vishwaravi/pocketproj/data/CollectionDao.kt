package com.vishwaravi.pocketproj.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: CollectionEntity): Long

    @Query("SELECT * FROM collections WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CollectionEntity?

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CollectionEntity?

    @Query("UPDATE collections SET name = :newName WHERE id = :id")
    suspend fun renameById(id: Long, newName: String): Int

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM collections")
    suspend fun deleteAll(): Int
}
