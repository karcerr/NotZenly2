package com.tagme.domain.usecases

import com.tagme.domain.repositories.AuthRepository
import org.json.JSONObject
import javax.inject.Inject

class AuthUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend fun login(username: String, password: String): JSONObject? {
        return authRepository.login(username, password)
    }
    suspend fun loginToken(): JSONObject? {
        return authRepository.loginToken()
    }

    suspend fun register(username: String, password: String): JSONObject? {
        return authRepository.register(username, password)
    }

    suspend fun authVK(token: String): JSONObject? {
        return authRepository.authVK(token)
    }
    suspend fun getMyDataWS() {
        return authRepository.getMyDataWS()
    }
}

