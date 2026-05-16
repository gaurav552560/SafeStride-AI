package com.pmgaurav.safestrideai.map

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "map_tiles")
data class MapTile(
    @PrimaryKey val id: String,
    val z: Int,
    val x: Int,
    val y: Int,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MapTile

        if (id != other.id) return false
        if (z != other.z) return false
        if (x != other.x) return false
        if (y != other.y) return false
        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + z
        result = 31 * result + x
        result = 31 * result + y
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

@Dao
interface TileDao {
    @Query("SELECT * FROM map_tiles WHERE id = :id")
    suspend fun getTile(id: String): MapTile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTile(tile: MapTile)

    @Query("DELETE FROM map_tiles WHERE timestamp < :expiryTime")
    suspend fun clearOldTiles(expiryTime: Long)

    @Query("SELECT COUNT(*) FROM map_tiles")
    suspend fun getTileCount(): Int

    @Query("DELETE FROM map_tiles")
    suspend fun clearAll()
}

@Database(entities = [MapTile::class], version = 1, exportSchema = false)
abstract class TileDatabase : RoomDatabase() {
    abstract fun tileDao(): TileDao
}

