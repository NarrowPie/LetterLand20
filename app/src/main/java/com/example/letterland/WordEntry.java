package com.example.letterland;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

// 🚀 Removed the old primary keys constraint so words don't merge/overwrite!
@Entity(tableName = "word_table")
public class WordEntry {

    // 🚀 NEW: Every single item gets its own unique ID now!
    // This stops the database from confusing two items with the same name.
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String word;

    @NonNull
    public String profileName; // Tags the picture to the specific player!

    public String imagePath;

    // 🚀 NEW: Tracks if the item is approved/starred for the Quiz!
    public boolean isStarred = false;

    // This tracks if the checkbox is checked in the Admin panel!
    // @Ignore means the database won't try to save this, it's just for the UI.
    @Ignore
    public boolean isSelected = false;

    public WordEntry(@NonNull String word, @NonNull String profileName, String imagePath) {
        this.word = word;
        this.profileName = profileName;
        this.imagePath = imagePath;
    }
}