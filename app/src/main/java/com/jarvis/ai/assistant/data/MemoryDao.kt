package com.jarvis.ai.assistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {
    @Query("SELECT * FROM jarvis_memory ORDER BY timestamp DESC")
    suspend fun getAllMemory(): List<MemoryEntity>

    @Query("SELECT * FROM jarvis_memory WHERE `key` LIKE :searchKey LIMIT 1")
    suspend fun getMemoryByKey(searchKey: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("DELETE FROM jarvis_memory")
    suspend fun clearMemory()
}
