package com.vishwaravi.pocketproj.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE isHidden = 1 ORDER BY createdAt DESC")
    fun observeHidden(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): LinkEntity?

    @Query("SELECT * FROM links WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): LinkEntity?

    /**
     * A one-shot snapshot of every link, for the sweeps that have to read the whole table and then
     * write to it inside a single transaction ([LinkRepository.syncHiddenTags], `renameTag`,
     * `deleteTag`).
     *
     * These used to reach for `observeAll().first()`, which is a Flow: collecting one registers an
     * InvalidationTracker observer and then cancels it on the very next line, inside the one block
     * where a cancellation is least welcome, and it makes the sweep's correctness depend on "the
     * first emission equals the current table" holding for the whole write phase. One query says
     * what it means. The order matches [observeAll] so a snapshot and the screen agree on which
     * link is which.
     */
    @Query("SELECT * FROM links ORDER BY createdAt DESC")
    suspend fun getAll(): List<LinkEntity>

    @Insert
    suspend fun insert(link: LinkEntity): Long

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE links SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long): Int

    @Query("UPDATE links SET collectionId = :newCollectionId WHERE collectionId = :oldCollectionId")
    suspend fun reassignCollection(oldCollectionId: Long, newCollectionId: Long): Int

    @Update
    suspend fun update(link: LinkEntity): Int

    @Query("DELETE FROM links WHERE collectionId = :collectionId")
    suspend fun deleteByCollectionId(collectionId: Long): Int

    @Query("DELETE FROM links")
    suspend fun deleteAll(): Int
}