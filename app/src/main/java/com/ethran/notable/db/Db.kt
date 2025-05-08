package com.ethran.notable.db

import android.content.Context
import android.os.Environment
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Date


class Converters {
    @TypeConverter
    fun fromListString(value: List<String>) = Json.encodeToString(value)

    @TypeConverter
    fun toListString(value: String) = Json.decodeFromString<List<String>>(value)

    @TypeConverter
    fun fromListPoint(value: List<StrokePoint>) = Json.encodeToString(value)

    @TypeConverter
    fun toListPoint(value: String) = Json.decodeFromString<List<StrokePoint>>(value)

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

@RenameColumn.Entries(
    RenameColumn(
        tableName = "Page",
        fromColumnName = "nativeTemplate",
        toColumnName = "background"
    )
)
class AutoMigration30to31 : AutoMigrationSpec

@Database(
    entities = [Folder::class, Notebook::class, Page::class, Stroke::class, Image::class, Kv::class],
    version = 31,
    autoMigrations = [
        AutoMigration(19, 20),
        AutoMigration(20, 21),
        AutoMigration(21, 22),
        AutoMigration(23, 24),
        AutoMigration(24, 25),
        AutoMigration(25, 26),
        AutoMigration(26, 27),
        AutoMigration(27, 28),
        AutoMigration(28, 29),
        AutoMigration(29, 30),
        AutoMigration(30,  31, spec = AutoMigration30to31::class)

    ], exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun kvDao(): KvDao
    abstract fun notebookDao(): NotebookDao
    abstract fun pageDao(): PageDao
    abstract fun strokeDao(): StrokeDao
    abstract fun ImageDao(): ImageDao

    companion object {
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            if (INSTANCE == null) {
                synchronized(this) {
                    val documentsDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    val dbDir = File(documentsDir, "notabledb")
                    if (!dbDir.exists()) {
                        dbDir.mkdirs()
                    }
                    val dbFile = File(dbDir, "app_database")

                    // Use Room to build the database
                    INSTANCE =
                        Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                            .allowMainThreadQueries() // Avoid in production
                            .addMigrations(MIGRATION_16_17, MIGRATION_17_18, MIGRATION_22_23)
                            .build()

                }
            }
            return INSTANCE!!
        }
    }
}