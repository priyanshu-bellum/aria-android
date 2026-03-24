package com.aria.di

import android.content.Context
import com.aria.data.claude.ClaudeApiClient
import com.aria.data.claude.PromptBuilder
import com.aria.data.local.AriaDatabase
import com.aria.data.local.dao.ConversationDao
import com.aria.data.local.dao.DaySummaryDao
import com.aria.data.local.dao.NoteDao
import com.aria.data.local.dao.TodoDao
import com.aria.data.memory.Mem0ApiClient
import com.aria.data.memory.Mem0Repository
import com.aria.data.repository.SecureStorage
import com.aria.picoclaw.ConfigWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi =
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AriaDatabase =
        AriaDatabase.create(context)

    @Provides
    fun provideTodoDao(db: AriaDatabase): TodoDao = db.todoDao()

    @Provides
    fun provideNoteDao(db: AriaDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideDaySummaryDao(db: AriaDatabase): DaySummaryDao = db.daySummaryDao()

    @Provides
    fun provideConversationDao(db: AriaDatabase): ConversationDao = db.conversationDao()

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage =
        SecureStorage(context)

    @Provides
    @Singleton
    fun provideMem0ApiClient(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        secureStorage: SecureStorage
    ): Mem0ApiClient = Mem0ApiClient(okHttpClient, moshi, secureStorage)

    @Provides
    @Singleton
    fun provideMem0Repository(
        mem0ApiClient: Mem0ApiClient,
        moshi: Moshi
    ): Mem0Repository = Mem0Repository(mem0ApiClient, moshi)

    @Provides
    @Singleton
    fun provideClaudeApiClient(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        secureStorage: SecureStorage
    ): ClaudeApiClient = ClaudeApiClient(okHttpClient, moshi, secureStorage)

    @Provides
    @Singleton
    fun providePromptBuilder(
        mem0Repository: Mem0Repository,
        todoDao: TodoDao
    ): PromptBuilder = PromptBuilder(mem0Repository, todoDao)

    @Provides
    @Singleton
    fun provideConfigWriter(
        @ApplicationContext context: Context,
        secureStorage: SecureStorage
    ): ConfigWriter = ConfigWriter(context, secureStorage)
}
