package com.dna.app.data.di

import android.content.Context
import androidx.room.Room
import com.dna.app.data.local.db.AppDatabase
import com.dna.app.data.local.db.ColorCorrectionDao
import com.dna.app.data.local.db.DressDao
import com.dna.app.data.local.db.MIGRATION_2_3
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
            .addMigrations(MIGRATION_2_3)
            // Anything older than v2 (pre-M4) had no real user data — drop it.
            .fallbackToDestructiveMigrationFrom(1)
            .build()

    @Provides
    fun provideDressDao(db: AppDatabase): DressDao = db.dressDao()

    @Provides
    fun provideColorCorrectionDao(db: AppDatabase): ColorCorrectionDao = db.colorCorrectionDao()
}
