package dev.projectvyuh.solo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.projectvyuh.solo.core.model.ModelManager
import dev.projectvyuh.solo.core.privacy.NetworkGuardInterceptor
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt providers for the LLM stack. [LiteRtLmEngine] and [ModelManager]
 * are themselves @Singleton-annotated with @Inject constructors, so they
 * are discovered automatically; this module only needs to provide the
 * external dependencies they take.
 *
 * The single [OkHttpClient] is wired through [NetworkGuardInterceptor],
 * so every outbound request anywhere in the app — model downloads, future
 * web-fetch tools, anything a transitive library might attempt — is forced
 * through Solo's network egress policy.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(guard: NetworkGuardInterceptor): OkHttpClient =
        ModelManager.buildHttpClient()
            .newBuilder()
            .addInterceptor(guard)
            .build()
}
