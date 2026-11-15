package com.feringsapp.echobox.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TrackEntity::class,
        TrackTagEntity::class,
        SeriesEntity::class,
        HttpCacheEntity::class,
        SyncStateEntity::class,
        DownloadEntity::class,
        QueueItemEntity::class,
        PlayerStateEntity::class,
        PlayHistoryEntity::class,
        MirrorEntity::class,
        SubmissionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun tagDao(): TagDao
    abstract fun metaDao(): MetaDao
    abstract fun mirrorDao(): MirrorDao
    abstract fun submissionDao(): SubmissionDao
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): HistoryDao
}

object EchoDatabaseFactory {
    fun create(context: Context): EchoDatabase =
        Room.databaseBuilder(context, EchoDatabase::class.java, "echobox.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    val MIGRATIONS: Array<Migration> = emptyArray()
}
