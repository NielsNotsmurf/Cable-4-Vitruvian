package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.data.local.UserProfileDao
import com.example.vitruvianredux.data.local.UserProfileEntity
import kotlinx.coroutines.flow.Flow
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
}
