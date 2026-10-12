package com.example.chipvision.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [InspectionRecord::class], version = 1, exportSchema = false)
abstract class InspectionDatabase : RoomDatabase() {
    abstract fun inspectionDao(): InspectionDao

    companion object {
        @Volatile
        private var instance: InspectionDatabase? = null

        fun getInstance(context: Context): InspectionDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    InspectionDatabase::class.java,
                    "chipvision_inspection.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
