package com.example.vitruvianredux.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorInt: Int = -10354962, // 0xFF6200EE — Material purple
    val createdAt: Long = System.currentTimeMillis(),
    // Paired Bluetooth heart rate sensor, e.g. a Garmin watch broadcasting HR (added in v29).
    // A sensor belongs to one profile; heart rate is only recorded for that profile's sets.
    val hrSensorAddress: String? = null,
    val hrSensorName: String? = null
)

@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity)

    @Delete
    suspend fun delete(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profiles ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: String): UserProfileEntity?

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun getCount(): Int

    @Query("SELECT * FROM user_profiles ORDER BY createdAt ASC")
    suspend fun getAllProfilesSync(): List<UserProfileEntity>

    @Query("UPDATE user_profiles SET hrSensorAddress = :address, hrSensorName = :name WHERE id = :profileId")
    suspend fun setHeartRateSensor(profileId: String, address: String?, name: String?)

    /** A sensor belongs to one person: unpair it from everyone except [keepProfileId]. */
    @Query("UPDATE user_profiles SET hrSensorAddress = NULL, hrSensorName = NULL WHERE hrSensorAddress = :address AND id != :keepProfileId")
    suspend fun clearHeartRateSensorFromOthers(address: String, keepProfileId: String)
}
