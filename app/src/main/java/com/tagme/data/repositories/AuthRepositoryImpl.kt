package com.tagme.data.repositories

import com.tagme.data.API
import com.tagme.domain.repositories.AuthRepository
import org.json.JSONObject
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: API
) : AuthRepository {

    override suspend fun login(username: String, password: String): JSONObject? {
        return api.loginUser(username, password)
    }

    override suspend fun loginToken(): JSONObject? {
        return api.loginToken()
    }

    override suspend fun register(username: String, password: String): JSONObject? {
        return api.registerUser(username, password)
    }

    override suspend fun authVK(token: String): JSONObject? {
        return api.authVK(token)
    }

    override suspend fun getMyDataWS() {
        api.getMyDataFromWS()
    }
}