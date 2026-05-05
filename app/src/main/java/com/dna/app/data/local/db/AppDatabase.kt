package com.dna.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DressEntity::class, ColorCorrectionEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dressDao(): DressDao
    abstract fun colorCorrectionDao(): ColorCorrectionDao
}
