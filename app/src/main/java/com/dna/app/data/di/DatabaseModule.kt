package com.dna.app.data.di

import android.content.Context
import androidx.room.Room
import com.dna.app.data.local.db.AppDatabase
import com.dna.app.data.local.db.DressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "dna.db")
            // Migrations land in M4 alongside the sync worker. For M2 we
            // destructively migrate since there's no real user data yet.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDressDao(db: AppDatabase): DressDao = db.dressDao()
}
