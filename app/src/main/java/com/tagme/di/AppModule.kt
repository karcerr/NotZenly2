package com.tagme.di

import android.app.Application
import android.content.Context
import android.content.res.Resources
import com.tagme.data.API
import com.tagme.data.repositories.AuthRepositoryImpl
import com.tagme.domain.repositories.AuthRepository
import com.tagme.domain.usecases.AuthUseCase
import com.tagme.domain.usecases.ConnectToWebsocketUseCase
import com.tagme.presentation.services.NotificationManager
import com.tagme.presentation.viewmodels.LoginViewModel
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAPI(@ApplicationContext context: Context, notificationManager: NotificationManager): API {
        return API(context, notificationManager)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(api: API): AuthRepository {
        return AuthRepositoryImpl(api)
    }

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context, api: Lazy<API>): NotificationManager {
        return NotificationManager(context, api)
    }

    @Provides
    @Singleton
    fun provideAuthUseCase(authRepository: AuthRepository): AuthUseCase {
        return AuthUseCase(authRepository)
    }

    @Provides
    @Singleton
    fun provideConnectToWebsocketUseCase(api: API): ConnectToWebsocketUseCase {
        return ConnectToWebsocketUseCase(api)
    }

    @Provides
    @Singleton
    fun provideLoginViewModel(
        authUseCase: AuthUseCase,
        connectToWebsocketUseCase: ConnectToWebsocketUseCase
    ): LoginViewModel {
        return LoginViewModel(authUseCase, connectToWebsocketUseCase)
    }
    @Provides
    @Singleton
    fun provideResources(application: Application): Resources {
        return application.resources
    }
}
