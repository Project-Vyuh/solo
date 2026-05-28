package dev.projectvyuh.solo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.projectvyuh.solo.data.llm.LlmRepositoryImpl
import dev.projectvyuh.solo.domain.repository.LlmRepository
import javax.inject.Singleton

/**
 * Binds the domain repository interfaces to their data-layer implementations.
 * Use cases inject the interface, never the impl — keeps the domain layer
 * pure and lets us swap engines later (MLX on iOS, NPU backend, etc.) without
 * touching anything that depends on [LlmRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindLlmRepository(impl: LlmRepositoryImpl): LlmRepository
}
