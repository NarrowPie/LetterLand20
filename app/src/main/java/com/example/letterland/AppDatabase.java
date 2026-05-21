package com.example.letterland;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

// 🚀 Bumped to version 7 and added TypeConverters!
@Database(entities = {WordEntry.class, LogEntry.class, QuizRecord.class}, version = 7, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract WordDao wordDao();
    public abstract LogDao logDao();
    public abstract QuizRecordDao quizRecordDao();

    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "letterland_database")
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build();
        }
        return instance;
    }
}