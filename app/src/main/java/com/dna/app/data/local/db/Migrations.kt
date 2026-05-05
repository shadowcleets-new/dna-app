package com.dna.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3:
 *  - dresses gains video / dimension columns + a mediaType discriminator.
 *  - color_corrections table created (per-dress slider state).
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dresses ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'IMAGE'")
        db.execSQL("ALTER TABLE dresses ADD COLUMN mimeType TEXT")
        db.execSQL("ALTER TABLE dresses ADD COLUMN videoOriginalUrl TEXT")
        db.execSQL("ALTER TABLE dresses ADD COLUMN durationMs INTEGER")
        db.execSQL("ALTER TABLE dresses ADD COLUMN width INTEGER")
        db.execSQL("ALTER TABLE dresses ADD COLUMN height INTEGER")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS color_corrections (
                dressId TEXT NOT NULL PRIMARY KEY,
                brightness REAL NOT NULL,
                contrast REAL NOT NULL,
                saturation REAL NOT NULL,
                exposureEv REAL NOT NULL,
                temperatureK REAL NOT NULL,
                tint REAL NOT NULL,
                whiteBalanceR REAL,
                whiteBalanceG REAL,
                whiteBalanceB REAL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
