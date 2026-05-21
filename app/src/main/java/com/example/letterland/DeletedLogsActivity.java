package com.example.letterland;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DeletedLogsActivity extends AppCompatActivity {

    private RecyclerView rvDeletedLogs;
    private DeletedLogAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deleted_logs);

        MaterialButton btnBack = findViewById(R.id.btnDeletedLogsBack);
        rvDeletedLogs = findViewById(R.id.rvDeletedLogs);
        rvDeletedLogs.setLayoutManager(new LinearLayoutManager(this));

        btnBack.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            finish();
        });

        loadLogs();
    }

    private void loadLogs() {
        new Thread(() -> {
            List<LogEntry> allLogs = AppDatabase.getInstance(this).logDao().getAllLogs();
            List<LogEntry> deletedLogs = new ArrayList<>();

            for (LogEntry log : allLogs) {
                if ("DELETED WORD".equals(log.action)) {
                    deletedLogs.add(log);
                }
            }

            runOnUiThread(() -> {
                adapter = new DeletedLogAdapter(deletedLogs);
                rvDeletedLogs.setAdapter(adapter);
            });
        }).start();
    }

    private void showRestoreDialog(LogEntry log, String wordName, String imagePath, String profileName, String deleterName) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_restore_item, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView ivRestoreImage = view.findViewById(R.id.ivRestoreImage);
        TextView tvRestoreDetails = view.findViewById(R.id.tvRestoreDetails);

        // Loads original high-quality image file into dialog container view
        Glide.with(this).load(imagePath).into(ivRestoreImage);

        // Displays clear collection data and explicit action tracing
        tvRestoreDetails.setText("Object: " + wordName + "\nCollected by: " + profileName + "\nDeleted by: " + deleterName);

        view.findViewById(R.id.btnCancelRestore).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            dialog.dismiss();
        });

        view.findViewById(R.id.btnConfirmRestore).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            dialog.dismiss();

            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(this);

                // 1. Verify user hasn't created another word under an identical text name
                WordEntry existingWord = db.wordDao().findWordForProfile(wordName, profileName);
                if (existingWord != null) {
                    runOnUiThread(() -> Toast.makeText(this, profileName + " already has a word named " + wordName + "!", Toast.LENGTH_LONG).show());
                    return;
                }

                // 2. Re-insert data back into user's specific Almanac table
                WordEntry restoredWord = new WordEntry(wordName, profileName, imagePath);
                db.wordDao().insert(restoredWord);

                // 3. Purge target log from history and append a normal action restoration statement
                db.logDao().deleteLog(log);
                db.logDao().insertLog(new LogEntry("PLAYER_LOG", "RESTORED_WORD|" + wordName + " for " + profileName, System.currentTimeMillis()));

                runOnUiThread(() -> {
                    Toast.makeText(this, wordName + " restored successfully for " + profileName + "!", Toast.LENGTH_SHORT).show();
                    loadLogs();
                });
            }).start();
        });

        dialog.show();
    }

    private class DeletedLogAdapter extends RecyclerView.Adapter<DeletedLogAdapter.LogViewHolder> {
        private final List<LogEntry> logs;
        private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());

        public DeletedLogAdapter(List<LogEntry> logs) {
            this.logs = logs;
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deleted_log, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            LogEntry log = logs.get(position);
            holder.tvDeletedTime.setText("Time: " + sdf.format(new Date(log.timestamp)));

            // Extract 4-token array parts safely
            String[] parts = log.details.split("\\|");

            if (parts.length >= 2) {
                String wordName = parts[0];
                String imagePath = parts[1];
                String profileName = (parts.length >= 3) ? parts[2] : "Unknown User";
                String deleterName = (parts.length >= 4) ? parts[3] : profileName;

                holder.tvDeletedWord.setText("Deleted '" + wordName + "'\nBy: " + deleterName);

                Glide.with(DeletedLogsActivity.this)
                        .load(imagePath)
                        .override(150, 150) // Downscaled rendering optimization strictly limits main thread memory footprints
                        .placeholder(R.drawable.admin_pic)
                        .into(holder.ivDeletedImage);

                // Bind click event mapping seamlessly to layout popup handlers
                holder.itemView.setOnClickListener(v -> {
                    SoundManager.getInstance(DeletedLogsActivity.this).playClick();
                    showRestoreDialog(log, wordName, imagePath, profileName, deleterName);
                });

            } else {
                holder.tvDeletedWord.setText(log.details);
                holder.ivDeletedImage.setImageResource(R.drawable.admin_pic);
                holder.itemView.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        class LogViewHolder extends RecyclerView.ViewHolder {
            ImageView ivDeletedImage;
            TextView tvDeletedWord, tvDeletedTime;

            public LogViewHolder(@NonNull View itemView) {
                super(itemView);
                ivDeletedImage = itemView.findViewById(R.id.ivDeletedImage);
                tvDeletedWord = itemView.findViewById(R.id.tvDeletedWord);
                tvDeletedTime = itemView.findViewById(R.id.tvDeletedTime);
            }
        }
    }
}