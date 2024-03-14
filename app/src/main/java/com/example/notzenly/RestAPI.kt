package com.example.notzenly

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class UserRegistration(val nickname: String, val password: String)
data class UserResponse(val id: Int, val nickname: String, val lastLocation: String?)

interface ApiService {
    @POST("/api/registration")
    fun registerUser(@Body user: UserRegistration): Call<UserResponse>

    @POST("/api/login")
    fun loginUser(@Body user: UserRegistration): Call<ResponseBody>
}

object ApiClient {
    private const val BASE_URL = "http://127.0.0.1:5000/api/"

    val apiService: ApiService by lazy {
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}


class RestAPI {
    suspend fun loginUser(nickname: String, password: String): String? {
        return withContext(Dispatchers.IO) {
            val loginCall = ApiClient.apiService.loginUser(UserRegistration(nickname, password))
            val loginResponse = loginCall.execute()
            if (loginResponse.isSuccessful) {
                println("Login successful")
                null
            } else {
                println("Login failed: ${loginResponse.errorBody()?.string()}")
                loginResponse.errorBody()?.string()
            }
        }
    }

    suspend fun registerUser(nickname: String, password: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val registrationCall = ApiClient.apiService.registerUser(UserRegistration(nickname, password))
                val registrationResponse = registrationCall.execute()
                if (registrationResponse.isSuccessful) {
                    val user = registrationResponse.body()
                    println("User registered: $user")
                    null
                } else {
                    println("Failed to register user: ${registrationResponse.errorBody()?.string()}")
                    registrationResponse.errorBody()?.string()
                }
            } catch (e: HttpException) {
                println("HTTP Exception: ${e.message()}")
                e.message()
            } catch (e: Exception) {
                println("Error: ${e.message}")
                e.message
            }
        }
    }
}