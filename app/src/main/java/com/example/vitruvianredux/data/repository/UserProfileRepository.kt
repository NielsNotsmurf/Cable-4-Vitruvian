package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.data.local.UserProfileDao
import com.example.vitruvianredux.data.local.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val dao: UserProfileDao
) {
    fun getAllProfiles(): Flow<List<UserProfileEntity>> = dao.getAllProfiles()

    suspend fun createProfile(name: String, colorInt: Int): UserProfileEntity {
        val profile = UserProfileEntity(name = name, colorInt = colorInt)
        dao.insert(profile)
        return profile
    }

    suspend fun delete(profile: UserProfileEntity) = dao.delete(profile)

    suspend fun getById(id: String): UserProfileEntity? = dao.getById(id)

    suspend fun getCount(): Int = dao.getCount()

    suspend fun seedDefaultProfiles(): Boolean {
        if (dao.getCount() > 0) return false
        Timber.d("No profiles found — seeding defaults: Niels, Anna")
        dao.insert(UserProfileEntity(
            id = "niels",
            name = "Niels",
            colorInt = -0x75df99,
            createdAt = System.currentTimeMillis()
        ))
        dao.insert(UserProfileEntity(
            id = "anna",
            name = "Anna",
            colorInt = -0x4e2eb4,
            createdAt = System.currentTimeMillis() + 1L
        ))
        return true
    }
}
