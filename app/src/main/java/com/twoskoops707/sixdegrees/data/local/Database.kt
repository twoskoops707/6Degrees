package com.twoskoops707.sixdegrees.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.twoskoops707.sixdegrees.data.local.dao.CompanyDao
import com.twoskoops707.sixdegrees.data.local.dao.PersonDao
import com.twoskoops707.sixdegrees.data.local.dao.ReportDao
import com.twoskoops707.sixdegrees.data.local.entity.CompanyEntity
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.data.local.entity.PersonEntity
import com.twoskoops707.sixdegrees.data.local.entity.PropertyEntity
import com.twoskoops707.sixdegrees.data.local.entity.VehicleRecordEntity

@Database(
    entities = [
        PersonEntity::class,
        CompanyEntity::class,
        PropertyEntity::class,
        VehicleRecordEntity::class,
        OsintReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OsintDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun companyDao(): CompanyDao
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: OsintDatabase? = null

        fun getDatabase(context: Context): OsintDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OsintDatabase::class.java,
                    "osint_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}