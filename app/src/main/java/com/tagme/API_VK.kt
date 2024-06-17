package com.tagme

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

object RetrofitInstance {
    private const val BASE_URL = "https://api.vk.com/method/"

    val api: VkApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VkApiService::class.java)
    }
}

interface VkApiService {
    @GET("users.get")
    fun getUsers(
        @Query("access_token") accessToken: String,
        @Query("fields") fields: String,
        @Query("v") apiVersion: String = "5.199"
    ): Call<VkResponse>
}
data class VkResponse(
    val response: List<User>
)

data class User(
    val id: Int,
    val first_name: String,
    val last_name: String,
    val photo_200: String
)
