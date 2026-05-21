package com.example.letterland;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class AdminActivity extends AppCompatActivity {

    private long lastClickTime = 0;
    private AlertDialog pinDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnChangePin = findViewById(R.id.btnChangePin);
        MaterialButton btnAdminUserLogs = findViewById(R.id.btnAdminUserLogs);
        MaterialButton btnAdminPlayerLogs = findViewById(R.id.btnAdminPlayerLogs);
        MaterialButton btnQuizScores = findViewById(R.id.btnQuizScores);
        MaterialButton btnAdminAddObject = findViewById(R.id.btnAdminAddObject);
        MaterialButton btnEditAlmanac = findViewById(R.id.btnEditAlmanac);
        MaterialButton btnDeletedLogs = findViewById(R.id.btnDeletedLogs);

        // 🌟 Initialize the new Manage Storage Button
        MaterialButton btnManageStorage = findViewById(R.id.btnManageStorage);

        btnBack.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            finish();
        });

        btnChangePin.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            showChangePinDialog();
        });

        btnAdminUserLogs.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            if (hasActiveProfile()) {
                startActivity(new Intent(AdminActivity.this, UserLogsActivity.class));
            }
        });

        btnAdminPlayerLogs.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            startActivity(new Intent(AdminActivity.this, PlayerLogsActivity.class));
        });

        btnQuizScores.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            if (hasActiveProfile()) {
                startActivity(new Intent(AdminActivity.this, QuizRecordActivity.class));
            }
        });

        btnAdminAddObject.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            if (hasActiveProfile()) {
                startActivity(new Intent(AdminActivity.this, AddObjectActivity.class));
            }
        });

        btnEditAlmanac.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            if (hasActiveProfile()) {
                startActivity(new Intent(AdminActivity.this, EditAlmanacActivity.class));
            }
        });

        btnDeletedLogs.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            startActivity(new Intent(AdminActivity.this, DeletedLogsActivity.class));
        });

        // 🌟 Click Listener for Manage Storage (Triggers PIN check)
        btnManageStorage.setOnClickListener(v -> {
            if (isSpamClick()) return;
            SoundManager.getInstance(this).playClick();
            showConfirmWipeDialog();
        });
    }

    // Prevents double-taps and double-sounds
    private boolean isSpamClick() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 500) {
            return true;
        }
        lastClickTime = currentTime;
        return false;
    }

    private boolean hasActiveProfile() {
        SharedPreferences prefs = getSharedPreferences("LetterLandMemory", MODE_PRIVATE);
        String activePlayer = prefs.getString("ACTIVE_PROFILE", "");

        if (activePlayer.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_admin_select_player), Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void showChangePinDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reset_pin, null);
        pinDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (pinDialog.getWindow() != null) {
            pinDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etCurrentPin = dialogView.findViewById(R.id.etCurrentPin);
        EditText etNewPin = dialogView.findViewById(R.id.etNewPin);

        dialogView.findViewById(R.id.btnCancelReset).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            pinDialog.dismiss();
        });

        dialogView.findViewById(R.id.btnConfirmReset).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();

            String currentEntered = etCurrentPin.getText().toString();
            String newPinEntered = etNewPin.getText().toString();

            SharedPreferences prefs = getSharedPreferences("LetterLandMemory", MODE_PRIVATE);
            String savedPin = prefs.getString("ADMIN_PIN", "1234");

            if (!currentEntered.equals(savedPin)) {
                Toast.makeText(this, getString(R.string.toast_current_pin_incorrect), Toast.LENGTH_SHORT).show();
            } else if (newPinEntered.length() < 4) {
                Toast.makeText(this, getString(R.string.toast_new_pin_length), Toast.LENGTH_SHORT).show();
            } else {
                prefs.edit().putString("ADMIN_PIN", newPinEntered).apply();
                Toast.makeText(this, getString(R.string.toast_pin_updated), Toast.LENGTH_SHORT).show();
                pinDialog.dismiss();
            }
        });

        pinDialog.show();
    }

    // 🌟 Custom PIN check opens the new Storage Management Screen!
    private void showConfirmWipeDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_pin, null);
        AlertDialog wipePinDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (wipePinDialog.getWindow() != null) {
            wipePinDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etAdminPin = dialogView.findViewById(R.id.etAdminPin);
        MaterialButton btnCancelPin = dialogView.findViewById(R.id.btnCancelPin);
        MaterialButton btnConfirmPin = dialogView.findViewById(R.id.btnConfirmPin);

        btnCancelPin.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            wipePinDialog.dismiss();
        });

        btnConfirmPin.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();

            String enteredPin = etAdminPin.getText().toString();
            SharedPreferences prefs = getSharedPreferences("LetterLandMemory", MODE_PRIVATE);
            String savedPin = prefs.getString("ADMIN_PIN", "1234");

            if (enteredPin.equals(savedPin)) {
                wipePinDialog.dismiss();
                // Launches the new screen
                startActivity(new Intent(AdminActivity.this, StorageManagementActivity.class));
            } else {
                Toast.makeText(this, "Incorrect PIN. Action blocked.", Toast.LENGTH_SHORT).show();
                etAdminPin.setText("");
            }
        });

        wipePinDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pinDialog != null && pinDialog.isShowing()) {
            pinDialog.dismiss();
        }
    }
}