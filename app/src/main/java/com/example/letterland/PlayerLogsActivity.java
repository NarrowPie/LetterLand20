package com.example.letterland;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PlayerLogsActivity extends AppCompatActivity {

    private RecyclerView rvPlayerLogs;
    private TextView tvEmptyLogs;
    private PlayerLogAdapter adapter;
    private List<LogEntry> allLogs = new ArrayList<>();

    private MaterialButton btnFilterAll, btnFilterDeleted, btnFilterAdded, btnFilterEdited;
    private long lastFilterTouchTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_logs);

        // Bulletproof Back Button
        setSafeTouchListener(findViewById(R.id.btnBackPlayerLogs), this::finish);

        tvEmptyLogs = findViewById(R.id.tvEmptyLogs);
        rvPlayerLogs = findViewById(R.id.rvPlayerLogs);
        rvPlayerLogs.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PlayerLogAdapter(new ArrayList<>());
        rvPlayerLogs.setAdapter(adapter);

        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterDeleted = findViewById(R.id.btnFilterDeleted);
        btnFilterAdded = findViewById(R.id.btnFilterAdded);
        btnFilterEdited = findViewById(R.id.btnFilterEdited);

        // Bulletproof Filter Buttons
        setSafeTouchListener(btnFilterAll, () -> applyFilter("ALL"));
        setSafeTouchListener(btnFilterDeleted, () -> applyFilter("DELETED"));
        setSafeTouchListener(btnFilterAdded, () -> applyFilter("ADDED"));
        setSafeTouchListener(btnFilterEdited, () -> applyFilter("EDITED"));

        loadLogsFromDatabase();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setSafeTouchListener(View view, Runnable action) {
        view.setOnClickListener(null);
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFilterTouchTime < 500) {
                    return true;
                }
                lastFilterTouchTime = currentTime;
                SoundManager.getInstance(PlayerLogsActivity.this).playClick();
                action.run();
                return true;
            }
            return false;
        });
    }

    private void loadLogsFromDatabase() {
        new Thread(() -> {
            List<LogEntry> rawLogs = AppDatabase.getInstance(this).logDao().getAllLogs();
            allLogs.clear();

            for (LogEntry log : rawLogs) {
                if ("PLAYER_LOG".equals(log.action)) {
                    allLogs.add(log);
                }
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                applyFilter("ALL");
            });
        }).start();
    }

    private void applyFilter(String filterType) {
        List<LogEntry> filteredList = new ArrayList<>();

        for (LogEntry log : allLogs) {
            String[] parts = log.details.split("\\|");
            if (parts.length < 2) continue;
            String type = parts[0];

            if (filterType.equals("ALL")) {
                filteredList.add(log);
            } else if (filterType.equals("DELETED") && type.equals("DELETED")) {
                filteredList.add(log);
            } else if (filterType.equals("ADDED") && (type.equals("ADDED") || type.equals("RESTORED"))) {
                filteredList.add(log);
            } else if (filterType.equals("EDITED") && (type.equals("EDITED") || type.equals("RENAMED_FROM"))) {
                filteredList.add(log);
            }
        }

        if (filteredList.isEmpty()) {
            tvEmptyLogs.setVisibility(View.VISIBLE);
            rvPlayerLogs.setVisibility(View.GONE);
        } else {
            tvEmptyLogs.setVisibility(View.GONE);
            rvPlayerLogs.setVisibility(View.VISIBLE);
            adapter.updateData(filteredList);
        }
    }

    // ==========================================
    // THE ADAPTER
    // ==========================================
    private class PlayerLogAdapter extends RecyclerView.Adapter<PlayerLogAdapter.LogViewHolder> {
        private List<LogEntry> logs;
        private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.US);

        private long lastRestoreTouchTime = 0;

        public PlayerLogAdapter(List<LogEntry> logs) {
            this.logs = logs;
        }

        public void updateData(List<LogEntry> newLogs) {
            this.logs = newLogs;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_player_log, parent, false);
            return new LogViewHolder(view);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            LogEntry log = logs.get(position);

            String[] parts = log.details.split("\\|");
            String type = parts.length > 0 ? parts[0] : "UNKNOWN";
            String playerName = parts.length > 1 ? parts[1] : "Unknown Player";

            holder.tvLogPlayerName.setText(playerName);
            holder.tvLogDate.setText(sdf.format(new Date(log.timestamp)));

            if (type.equals("DELETED")) {
                holder.tvLogAction.setText("Profile was deleted");
                holder.tvLogAction.setTextColor(Color.parseColor("#F44336"));
                holder.ivLogIcon.setColorFilter(Color.parseColor("#F44336"));
                holder.ivLogIcon.setImageResource(android.R.drawable.ic_menu_delete);

                holder.btnRestorePlayer.setVisibility(View.VISIBLE);

                SharedPreferences prefs = getSharedPreferences("LetterLandMemory", MODE_PRIVATE);
                Set<String> currentProfiles = prefs.getStringSet("ALL_PROFILES", new HashSet<>());

                if (currentProfiles.contains(playerName)) {
                    // 🌟 RESTORED VISUAL STATE
                    holder.btnRestorePlayer.setEnabled(false);
                    holder.btnRestorePlayer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0E0E0"))); // Light Gray
                    holder.btnRestorePlayer.setTextColor(Color.parseColor("#4CAF50")); // Green Text
                    holder.btnRestorePlayer.setIconResource(0); // Removes the icon
                    holder.btnRestorePlayer.setText("RESTORED");
                } else {
                    // 🌟 ACTIVE VISUAL STATE
                    holder.btnRestorePlayer.setEnabled(true);
                    holder.btnRestorePlayer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // Solid Green
                    holder.btnRestorePlayer.setTextColor(Color.WHITE); // White Text
                    holder.btnRestorePlayer.setIconResource(android.R.drawable.ic_menu_revert); // Show Icon
                    holder.btnRestorePlayer.setIconTint(ColorStateList.valueOf(Color.WHITE)); // White Icon
                    holder.btnRestorePlayer.setText("RESTORE");
                }

            } else if (type.equals("ADDED")) {
                holder.tvLogAction.setText("Added as a new profile");
                holder.tvLogAction.setTextColor(Color.parseColor("#4CAF50"));
                holder.ivLogIcon.setColorFilter(Color.parseColor("#4CAF50"));
                holder.ivLogIcon.setImageResource(android.R.drawable.ic_menu_add);
                holder.btnRestorePlayer.setVisibility(View.GONE);

            } else if (type.equals("RESTORED")) {
                holder.tvLogAction.setText("Profile was restored");
                holder.tvLogAction.setTextColor(Color.parseColor("#4CAF50"));
                holder.ivLogIcon.setColorFilter(Color.parseColor("#4CAF50"));
                holder.ivLogIcon.setImageResource(android.R.drawable.ic_menu_revert);
                holder.btnRestorePlayer.setVisibility(View.GONE);

            } else if (type.equals("EDITED")) {
                holder.tvLogAction.setText("Changed avatar image");
                holder.tvLogAction.setTextColor(Color.parseColor("#FF9800"));
                holder.ivLogIcon.setColorFilter(Color.parseColor("#FF9800"));
                holder.ivLogIcon.setImageResource(android.R.drawable.ic_menu_edit);
                holder.btnRestorePlayer.setVisibility(View.GONE);

            } else if (type.equals("RENAMED_FROM")) {
                // parts[1] (playerName) is the NEW name. parts[2] is the OLD name.
                String oldName = parts.length > 2 ? parts[2] : "Unknown";

                holder.tvLogPlayerName.setText(playerName); // 🌟 Sets Bold Title Header to NEW name (NATHANIELS)
                holder.tvLogAction.setText("Renamed: " + playerName + " ➔ " + oldName); // 🌟 Sets description to Chronological Order (NATHANIEL ➔ NATHANIELS)
                holder.tvLogAction.setTextColor(Color.parseColor("#FF9800")); // Warn/Info Orange Tint
                holder.ivLogIcon.setColorFilter(Color.parseColor("#FF9800"));
                holder.ivLogIcon.setImageResource(android.R.drawable.ic_menu_edit);
                holder.btnRestorePlayer.setVisibility(View.GONE);
            }

            holder.btnRestorePlayer.setOnClickListener(null);
            holder.btnRestorePlayer.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP && holder.btnRestorePlayer.isEnabled()) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastRestoreTouchTime < 1000) {
                        return true;
                    }
                    lastRestoreTouchTime = currentTime;

                    SoundManager.getInstance(PlayerLogsActivity.this).playClick();

                    // 🌟 INSTANTLY APPLY RESTORED VISUAL STATE ON CLICK
                    holder.btnRestorePlayer.setEnabled(false);
                    holder.btnRestorePlayer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
                    holder.btnRestorePlayer.setTextColor(Color.parseColor("#4CAF50"));
                    holder.btnRestorePlayer.setIconResource(0);
                    holder.btnRestorePlayer.setText("RESTORED");

                    SharedPreferences prefs = getSharedPreferences("LetterLandMemory", MODE_PRIVATE);
                    Set<String> oldProfiles = prefs.getStringSet("ALL_PROFILES", new HashSet<>());
                    Set<String> newProfiles = new HashSet<>(oldProfiles);

                    if (newProfiles.contains(playerName)) {
                        Toast.makeText(PlayerLogsActivity.this, "This profile already exists!", Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    newProfiles.add(playerName);
                    prefs.edit().putStringSet("ALL_PROFILES", newProfiles).apply();

                    Toast.makeText(PlayerLogsActivity.this, playerName + " has been RESTORED!", Toast.LENGTH_LONG).show();

                    new Thread(() -> {
                        LogEntry restoredLog = new LogEntry("PLAYER_LOG", "RESTORED|" + playerName, System.currentTimeMillis());
                        AppDatabase.getInstance(PlayerLogsActivity.this).logDao().insertLog(restoredLog);

                        runOnUiThread(PlayerLogsActivity.this::loadLogsFromDatabase);
                    }).start();

                    return true;
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        class LogViewHolder extends RecyclerView.ViewHolder {
            ImageView ivLogIcon;
            TextView tvLogPlayerName, tvLogAction, tvLogDate;
            MaterialButton btnRestorePlayer;

            public LogViewHolder(@NonNull View itemView) {
                super(itemView);
                ivLogIcon = itemView.findViewById(R.id.ivLogIcon);
                tvLogPlayerName = itemView.findViewById(R.id.tvLogPlayerName);
                tvLogAction = itemView.findViewById(R.id.tvLogAction);
                tvLogDate = itemView.findViewById(R.id.tvLogDate);
                btnRestorePlayer = itemView.findViewById(R.id.btnRestorePlayer);
            }
        }
    }
}