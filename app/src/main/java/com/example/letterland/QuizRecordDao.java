package com.example.letterland;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface QuizRecordDao {

    @Insert
    void insertRecord(QuizRecord record);

    @Query("SELECT * FROM quiz_record_table ORDER BY timestamp DESC")
    List<QuizRecord> getAllRecords();

    @Query("UPDATE quiz_record_table SET playerName = :newName WHERE playerName = :oldName")
    void updateProfileName(String oldName, String newName);

    // Manual wipe (from the Storage Management menu)
    @Query("DELETE FROM quiz_record_table")
    void deleteAllRecords();

    // 🌟 AUTOMATIC CONVEYOR BELT: Keeps the newest 'limit' amount, deletes the rest
    @Query("DELETE FROM quiz_record_table WHERE id NOT IN (SELECT id FROM quiz_record_table ORDER BY timestamp DESC LIMIT :limit)")
    void autoPruneOldestRecords(int limit);
}