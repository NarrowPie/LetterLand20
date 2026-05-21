package com.example.letterland;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import androidx.room.Update;
import androidx.room.Delete;

@Dao
public interface WordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WordEntry word);

    // ONLY grab the picture if it belongs to this specific kid
    @Query("SELECT * FROM word_table WHERE word = :searchWord AND profileName = :playerName LIMIT 1")
    WordEntry findWordForProfile(String searchWord, String playerName);

    // ONLY grab the gallery pictures for the kid who is logged in (NEWEST AT THE TOP)
    @Query("SELECT * FROM word_table WHERE profileName = :playerName ORDER BY rowid DESC")
    List<WordEntry> getAllWordsForProfile(String playerName);

    // 🚀 NEW: ONLY grab the starred pictures for the Quiz!
    @Query("SELECT * FROM word_table WHERE profileName = :playerName AND isStarred = 1 ORDER BY rowid DESC")
    List<WordEntry> getStarredWordsForProfile(String playerName);

    // Grab absolutely everything in the database! (NEWEST AT THE TOP)
    @Query("SELECT * FROM word_table ORDER BY rowid DESC")
    List<WordEntry> getAllWords();

    // Transfer ownership of all pictures to the new name!
    @Query("UPDATE word_table SET profileName = :newName WHERE profileName = :oldName")
    void updateProfileName(String oldName, String newName);

    @Update
    void update(WordEntry word);

    @Delete
    void delete(WordEntry word);
}