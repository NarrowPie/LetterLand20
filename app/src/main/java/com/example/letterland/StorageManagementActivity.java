package com.example.letterland;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import java.io.File;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

public class StorageManagementActivity extends AppCompatActivity {

    private long lastClickTime = 0;

    // 🌟 AUTOMATIC CONVEYOR BELT PRUNING THRESHOLDS
    // You can adjust these numbers to control how many rows stay in your database tables.
    private static final int HISTORY_LOGS_LIMIT = 100;       // Keeps the newest 100 word add/edit logs
    private static final int PLAYER_ACTIVITY_LIMIT = 200;   // Keeps the newest 200 clicks/screens active logs
    private static final int DELETED_ITEMS_LIMIT = 50;      // Keeps the newest 50 deleted word references
    private static final int QUIZ_RECORDS_LIMIT = 50;       // Keeps the newest 50 quiz result history rows

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage_management);

        // 🌟 TRIGGER: Fire off the conveyor belt queries automatically on screen load
        runAutomaticLogPruning();

        MaterialButton btnStorageBack = findViewById(R.id.btnStorageBack);
        MaterialButton btnClearCache = findViewById(R.id.btnClearCache);
        MaterialButton btnDeleteLogs = findViewById(R.id.btnDeleteLogs);
        MaterialButton btnResetData = findViewById(R.id.btnResetData);

        btnStorageBack.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            finish();
        });

        btnClearCache.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            clearAppTemporaryCache();
        });

        btnDeleteLogs.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            showPurgeLogsDialog();
        });

        btnResetData.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            showFactoryResetWarning();
        });
    }

    // 🌟 BACKGROUND AUTOMATION WORKER
    // Runs cleanly on a background thread to prevent UI stutter or lag.
    private void runAutomaticLogPruning() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(StorageManagementActivity.this);

                // Fire the Conveyor Belt Queries defined in your LogDao interface
                db.logDao().autoPruneHistoryLogs(HISTORY_LOGS_LIMIT);
                db.logDao().autoPrunePlayerActivityLogs(PLAYER_ACTIVITY_LIMIT);
                db.logDao().autoPruneDeletedItemLogs(DELETED_ITEMS_LIMIT);

                // Fire the Conveyor Belt Query defined in your QuizRecordDao interface
                db.quizRecordDao().autoPruneOldestRecords(QUIZ_RECORDS_LIMIT);

                android.util.Log.d("StorageManagement", "Conveyor belt self-cleaning executed perfectly.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private boolean isSpamClick() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 500) {
            return true;
        }
        lastClickTime = currentTime;
        return false;
    }

    // --- CACHE CLEARING LOGIC ---
    private void clearAppTemporaryCache() {
        Toast.makeText(this, "Clearing cache, please wait...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Glide.get(StorageManagementActivity.this).clearDiskCache();
                deleteDirectoryTree(getCacheDir());
                runOnUiThread(() -> {
                    Glide.get(StorageManagementActivity.this).clearMemory();
                    Toast.makeText(StorageManagementActivity.this, "App cache cleared successfully!", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(StorageManagementActivity.this, "Failed to completely clear cache.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private boolean deleteDirectoryTree(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDirectoryTree(new File(dir, child));
                    if (!success) { return false; }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }

    // --- PURGE LOGS MENU ---
    private void showPurgeLogsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_purge_logs, null);
        AlertDialog purgeDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (purgeDialog.getWindow() != null) {
            purgeDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        MaterialButton btnPurgeHistoryLogs = dialogView.findViewById(R.id.btnPurgeHistoryLogs);
        MaterialButton btnPurgeUserLogs = dialogView.findViewById(R.id.btnPurgeUserLogs);
        MaterialButton btnPurgeQuizRecords = dialogView.findViewById(R.id.btnPurgeQuizRecords);
        MaterialButton btnPurgeDeletedItems = dialogView.findViewById(R.id.btnPurgeDeletedItems);
        MaterialButton btnCancelPurge = dialogView.findViewById(R.id.btnCancelPurge);

        btnCancelPurge.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            purgeDialog.dismiss();
        });

        btnPurgeHistoryLogs.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            confirmFinalDeletion(1, "History Logs", purgeDialog);
        });

        btnPurgeUserLogs.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            confirmFinalDeletion(2, "User Logs", purgeDialog);
        });

        btnPurgeQuizRecords.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            confirmFinalDeletion(3, "Quiz Records", purgeDialog);
        });

        btnPurgeDeletedItems.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            confirmFinalDeletion(4, "Deleted Items History", purgeDialog);
        });

        purgeDialog.show();
    }

    private void confirmFinalDeletion(int logTypeId, String title, AlertDialog parentDialog) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Purge")
                .setMessage("Are you sure you want to permanently delete all " + title + "? This action cannot be undone.")
                .setPositiveButton("DELETE", (dialog, which) -> {
                    SoundManager.getInstance(this).playClick();
                    parentDialog.dismiss();
                    executePurge(logTypeId, title);
                })
                .setNegativeButton("CANCEL", (dialog, which) -> {
                    SoundManager.getInstance(this).playClick();
                    dialog.dismiss();
                })
                .show();
    }

    private void executePurge(int logTypeId, String title) {
        Toast.makeText(this, "Purging " + title + "...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(StorageManagementActivity.this);
            try {
                if (logTypeId == 1) {
                    db.logDao().deleteHistoryLogs();
                } else if (logTypeId == 2) {
                    db.logDao().deletePlayerActivityLogs();
                } else if (logTypeId == 3) {
                    db.quizRecordDao().deleteAllRecords();
                } else if (logTypeId == 4) {
                    db.logDao().deleteDeletedItemLogs();
                }

                runOnUiThread(() -> {
                    Toast.makeText(StorageManagementActivity.this, title + " wiped successfully!", Toast.LENGTH_LONG).show();

                    // 🌟 TRIGGER RE-RUN: Re-run auto-pruning parameters to re-verify state limits after data gets wiped
                    runAutomaticLogPruning();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(StorageManagementActivity.this, "Failed to purge " + title, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // --- FACTORY RESET SEQUENCE ---
    private void showFactoryResetWarning() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ FACTORY RESET ⚠️")
                .setMessage("Are you ABSOLUTELY sure? This will permanently delete ALL player profiles, ALL quiz scores, ALL almanac objects, and restore the app to its original factory state. \n\nThis CANNOT be undone.")
                .setPositiveButton("YES, WIPE EVERYTHING", (dialog, which) -> {
                    SoundManager.getInstance(this).playClick();
                    executeFactoryReset();
                })
                .setNegativeButton("CANCEL", (dialog, which) -> {
                    SoundManager.getInstance(this).playClick();
                    dialog.dismiss();
                })
                .show();
    }

    private void executeFactoryReset() {
        Toast.makeText(this, "Initiating Factory Reset...", Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                AppDatabase.getInstance(StorageManagementActivity.this).clearAllTables();

                SharedPreferences prefs = getSharedPreferences("LetterLandMemory", MODE_PRIVATE);
                prefs.edit().clear().apply();

                Glide.get(StorageManagementActivity.this).clearDiskCache();
                deleteDirectoryTree(getCacheDir());

                runOnUiThread(() -> {
                    Glide.get(StorageManagementActivity.this).clearMemory();
                    Toast.makeText(StorageManagementActivity.this, "SYSTEM RESET COMPLETE.", Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(StorageManagementActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(StorageManagementActivity.this, "Factory Reset Failed.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}