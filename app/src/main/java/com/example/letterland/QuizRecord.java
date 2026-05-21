package com.example.letterland;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.List;

@Entity(tableName = "quiz_record_table")
public class QuizRecord {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String playerName;
    public int score;
    public int totalItems;
    public long timestamp;

    // 🚀 NEW: Now the database remembers the exact answers!
    public List<String> correctAnswers;
    public List<String> userAnswers;

    public QuizRecord(String playerName, int score, int totalItems, long timestamp, List<String> correctAnswers, List<String> userAnswers) {
        this.playerName = playerName;
        this.score = score;
        this.totalItems = totalItems;
        this.timestamp = timestamp;
        this.correctAnswers = correctAnswers;
        this.userAnswers = userAnswers;
    }
}