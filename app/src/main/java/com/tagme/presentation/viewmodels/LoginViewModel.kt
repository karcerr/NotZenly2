package com.tagme.presentation.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagme.domain.usecases.AuthUseCase
import com.tagme.domain.usecases.ConnectToWebsocketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
    private val connectToWebsocketUseCase: ConnectToWebsocketUseCase
) : ViewModel() {
    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> get() = _connectionStatus

    private val _loginResult = MutableLiveData<JSONObject?>()
    val loginResult: LiveData<JSONObject?> = _loginResult

    private val _loginTokenResult = MutableLiveData<JSONObject?>()
    val loginTokenResult: LiveData<JSONObject?> = _loginTokenResult

    private val _registerResult = MutableLiveData<JSONObject?>()
    val registerResult: LiveData<JSONObject?> = _registerResult

    private val _vkAuthResult = MutableLiveData<JSONObject?>()
    val vkAuthResult: LiveData<JSONObject?> = _vkAuthResult

    fun login(username: String, password: String) {
        viewModelScope.launch {
            val result = authUseCase.login(username, password)
            _loginResult.postValue(result)
        }
    }
    fun loginToken() {
        viewModelScope.launch {
            val result = authUseCase.loginToken()
            _loginTokenResult.postValue(result)
        }
    }
    fun register(username: String, password: String) {
        viewModelScope.launch {
            val result = authUseCase.register(username, password)
            _registerResult.postValue(result)
        }
    }

    fun authVK(token: String) {
        viewModelScope.launch {
            val result = authUseCase.authVK(token)
            _vkAuthResult.postValue(result)
        }
    }
    fun connectToServer() {
        viewModelScope.launch {
            val result = connectToWebsocketUseCase.connect()
            _connectionStatus.postValue(result)
        }
    }
    fun getMyDataWS() {
        viewModelScope.launch {
            authUseCase.getMyDataWS()
        }
    }
}
