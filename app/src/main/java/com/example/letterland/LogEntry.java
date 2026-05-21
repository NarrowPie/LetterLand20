package com.example.letterland;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "log_table")
public class LogEntry {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String action;
    public String details;
    public long timestamp;

    public LogEntry(String action, String details, long timestamp) {
        this.action = action;
        this.details = details;
        this.timestamp = timestamp;
    }
}