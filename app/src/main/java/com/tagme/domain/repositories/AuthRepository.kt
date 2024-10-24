package com.tagme.domain.repositories

import org.json.JSONObject

interface AuthRepository {
    suspend fun login(username: String, password: String): JSONObject?
    suspend fun loginToken(): JSONObject?
    suspend fun register(username: String, password: String): JSONObject?
    suspend fun authVK(token: String): JSONObject?
    suspend fun getMyDataWS()
}