package com.example.letterland;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private boolean isMusicOn = true;
    private SharedPreferences prefs;

    // Optimized background thread manager
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    // Leak trackers
    private android.app.AlertDialog playOptionsDialog;
    private android.app.AlertDialog exitDialog;
    private android.app.AlertDialog adminPinDialog;

    // 🚀 UPDATED: Request multiple core permissions simultaneously at application launch
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                    if (!entry.getValue()) {
                        allGranted = false;
                    }
                }
                if (!allGranted) {
                    Toast.makeText(this, "Camera and Microphone permissions are required to play LetterLand!", Toast.LENGTH_LONG).show();
                    finishAffinity(); // 🚀 Gracefully exits app if permissions are denied
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 🚀 UPDATED: Check and prompt permissions upfront together
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            requestPermissionsLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            });
        }

        prefs = getSharedPreferences("LetterLandMemory", MODE_PRIVATE);
        SoundManager soundManager = SoundManager.getInstance(this);

        View layoutOnboarding = findViewById(R.id.layoutOnboarding);
        EditText etFirstPlayerName = findViewById(R.id.etFirstPlayerName);
        MaterialButton btnStartPlaying = findViewById(R.id.btnStartPlaying);

        Set<String> allProfiles = prefs.getStringSet("ALL_PROFILES", new HashSet<>());
        if (allProfiles.isEmpty()) {
            layoutOnboarding.setVisibility(View.VISIBLE);
        } else {
            layoutOnboarding.setVisibility(View.GONE);
            // If the app is freshly launched, force them to pick a profile
            if (savedInstanceState == null) {
                // Clear the active profile so no one is logged in by default
                prefs.edit().putString("ACTIVE_PROFILE", "").apply();
                // Tell ProfilesActivity that this is a forced login!
                Intent startupIntent = new Intent(MainActivity.this, ProfilesActivity.class);
                startupIntent.putExtra("IS_MANDATORY_LOGIN", true);
                startActivity(startupIntent);
            }
        }

        btnStartPlaying.setOnClickListener(v -> {
            String newName = etFirstPlayerName.getText().toString().trim().toUpperCase();

            if (!newName.isEmpty()) {
                soundManager.playClick();
                Set<String> newProfiles = new HashSet<>();
                newProfiles.add(newName);

                prefs.edit().putStringSet("ALL_PROFILES", newProfiles).apply();
                prefs.edit().putString("ACTIVE_PROFILE", newName).apply();

                // FIX: Log the very first profile creation to the database!
                databaseExecutor.execute(() -> {
                    LogEntry log = new LogEntry("PLAYER_LOG", "ADDED|" + newName, System.currentTimeMillis());
                    AppDatabase.getInstance(MainActivity.this).logDao().insertLog(log);
                });

                updatePlayerBadge();

                layoutOnboarding.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                    layoutOnboarding.setVisibility(View.GONE);
                });

            } else {
                Toast.makeText(MainActivity.this, getString(R.string.toast_enter_name), Toast.LENGTH_SHORT).show();
            }
        });

        mediaPlayer = MediaPlayer.create(this, R.raw.menu_music);
        if (mediaPlayer != null) {
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }

        MaterialButton btnPlay = findViewById(R.id.btnPlay);
        MaterialButton btnDatabases = findViewById(R.id.btnDatabases);
        MaterialButton btnExit = findViewById(R.id.btnExit);
        com.google.android.material.imageview.ShapeableImageView btnAdmin = findViewById(R.id.btnAdmin);
        MaterialButton btnMusicToggle = findViewById(R.id.btnMusicToggle);
        MaterialCardView playerBadge = findViewById(R.id.playerBadgeCard);

        btnPlay.setOnClickListener(v -> {
            soundManager.playClick();

            String activePlayer = prefs.getString("ACTIVE_PROFILE", "");
            if (activePlayer.isEmpty()) {
                Toast.makeText(MainActivity.this, getString(R.string.toast_select_player_first), Toast.LENGTH_SHORT).show();

                hushMusic();
                startActivity(new Intent(MainActivity.this, ProfilesActivity.class));
            } else {
                showPlayOptionsDialog();
            }
        });

        btnDatabases.setOnClickListener(v -> {
            soundManager.playClick();
            hushMusic();
            startActivity(new Intent(MainActivity.this, AlmanacActivity.class));
        });

        playerBadge.setOnClickListener(v -> {
            soundManager.playClick();
            hushMusic();
            startActivity(new Intent(MainActivity.this, ProfilesActivity.class));
        });

        // Admin Button Styling
        android.graphics.drawable.GradientDrawable whiteCircle = new android.graphics.drawable.GradientDrawable();
        whiteCircle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        whiteCircle.setColor(Color.WHITE);
        btnAdmin.setBackground(whiteCircle);
        btnAdmin.setShapeAppearanceModel(new ShapeAppearanceModel()
                .toBuilder()
                .setAllCornerSizes(new com.google.android.material.shape.RelativeCornerSize(0.5f))
                .build());
        btnAdmin.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#29B6F6")));
        btnAdmin.setStrokeWidth(5f);
        btnAdmin.setImageResource(R.drawable.admin_pic);
        btnAdmin.setScaleType(ImageView.ScaleType.FIT_CENTER);
        btnAdmin.setPadding(15, 15, 15, 15);

        btnAdmin.setOnClickListener(v -> {
            soundManager.playClick();
            showAdminPinDialog();
        });

        btnExit.setOnClickListener(v -> {
            soundManager.playClick();
            showExitDialog();
        });

        btnMusicToggle.setOnClickListener(v -> {
            soundManager.playClick();
            if (mediaPlayer == null) return;

            if (isMusicOn) {
                mediaPlayer.pause();
                btnMusicToggle.setIconResource(android.R.drawable.ic_lock_silent_mode);
                isMusicOn = false;
                soundManager.toggleSound(false);
            } else {
                mediaPlayer.start();
                btnMusicToggle.setIconResource(android.R.drawable.ic_lock_silent_mode_off);
                isMusicOn = true;
                soundManager.toggleSound(true);
            }
        });
    }

    private void showPlayOptionsDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_play_options, null);
        builder.setView(view);

        playOptionsDialog = builder.create();

        if (playOptionsDialog.getWindow() != null) {
            playOptionsDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        playOptionsDialog.show();

        View btnWrite = view.findViewById(R.id.btnWriteLetters);
        if (btnWrite != null) {
            btnWrite.setOnClickListener(v -> {
                SoundManager.getInstance(this).playClick();
                playOptionsDialog.dismiss();
                hushMusic();
                startActivity(new Intent(MainActivity.this, WriteActivity.class));
            });
        }

        View btnScan = view.findViewById(R.id.btnScanWords);
        if (btnScan != null) {
            btnScan.setOnClickListener(v -> {
                SoundManager.getInstance(this).playClick();
                playOptionsDialog.dismiss();
                hushMusic();
                startActivity(new Intent(MainActivity.this, PlayActivity.class));
            });
        }

        MaterialButton btnQuiz = view.findViewById(R.id.btnQuizMode);
        TextView tvQuizHint = view.findViewById(R.id.tvQuizUnlockHint);
        if (btnQuiz != null && tvQuizHint != null) {
            btnQuiz.setEnabled(false);
            databaseExecutor.execute(() -> {
                String player = prefs.getString("ACTIVE_PROFILE", "Default");
                int wordCount = AppDatabase.getInstance(MainActivity.this).wordDao().getAllWordsForProfile(player).size();

                runOnUiThread(() -> {
                    if (wordCount >= 10) {
                        btnQuiz.setEnabled(true);
                        btnQuiz.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.quiz_unlocked_purple)));
                        tvQuizHint.setVisibility(View.GONE);
                    } else {
                        btnQuiz.setEnabled(false);
                        btnQuiz.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.quiz_locked_grey)));
                        int itemsNeeded = 10 - wordCount;
                        tvQuizHint.setText(getString(R.string.tv_quiz_unlock_hint, itemsNeeded));
                        tvQuizHint.setVisibility(View.VISIBLE);
                    }
                });
            });

            btnQuiz.setOnClickListener(v -> {
                SoundManager.getInstance(this).playClick();
                playOptionsDialog.dismiss();
                hushMusic();
                startActivity(new Intent(MainActivity.this, QuizActivity.class));
            });
        }

        View btnCancel = view.findViewById(R.id.btnCancelOptions);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                SoundManager.getInstance(this).playClick();
                playOptionsDialog.dismiss();
            });
        }
    }

    private void showExitDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_exit, null);
        builder.setView(view);

        exitDialog = builder.create();

        if (exitDialog.getWindow() != null) {
            exitDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        exitDialog.show();

        view.findViewById(R.id.btnCancelExit).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            exitDialog.dismiss();
        });
        view.findViewById(R.id.btnConfirmExit).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            exitDialog.dismiss();
            finishAffinity();
        });
    }

    private void showAdminPinDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_admin_pin, null);
        adminPinDialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (adminPinDialog.getWindow() != null) {
            adminPinDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etPin = dialogView.findViewById(R.id.etAdminPin);
        dialogView.findViewById(R.id.btnCancelPin).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            adminPinDialog.dismiss();
        });
        dialogView.findViewById(R.id.btnConfirmPin).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            String enteredPin = etPin.getText().toString();

            String savedPin = prefs.getString("ADMIN_PIN", "1234");

            if (enteredPin.equals(savedPin)) {
                adminPinDialog.dismiss();
                hushMusic();
                startActivity(new Intent(MainActivity.this, AdminActivity.class));
            } else {
                Toast.makeText(this, getString(R.string.toast_incorrect_pin), Toast.LENGTH_SHORT).show();
                etPin.setText("");
            }
        });
        adminPinDialog.show();
    }

    private void hushMusic() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    private void updatePlayerBadge() {
        String activePlayer = prefs.getString("ACTIVE_PROFILE", "");
        TextView tvName = findViewById(R.id.tvActivePlayerName);
        ImageView ivPic = findViewById(R.id.ivActivePlayerPic);

        if (activePlayer.isEmpty()) {
            tvName.setText(getString(R.string.tv_select_player));
            ivPic.setImageResource(R.drawable.admin_pic);
        } else {
            tvName.setText(activePlayer);
            String avatarPath = prefs.getString("AVATAR_" + activePlayer, null);

            if (avatarPath != null) {
                Glide.with(this)
                        .load(avatarPath)
                        .placeholder(R.drawable.admin_pic)
                        .error(R.drawable.admin_pic)
                        .into(ivPic);
            } else {
                ivPic.setImageResource(R.drawable.admin_pic);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePlayerBadge();

        if (isMusicOn && mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }

        Set<String> allProfiles = prefs.getStringSet("ALL_PROFILES", new HashSet<>());
        View layoutOnboarding = findViewById(R.id.layoutOnboarding);
        if (allProfiles.isEmpty()) {
            layoutOnboarding.setAlpha(1f);
            layoutOnboarding.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playOptionsDialog != null && playOptionsDialog.isShowing()) playOptionsDialog.dismiss();
        if (exitDialog != null && exitDialog.isShowing()) exitDialog.dismiss();
        if (adminPinDialog != null && adminPinDialog.isShowing()) adminPinDialog.dismiss();

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (!databaseExecutor.isShutdown()) {
            databaseExecutor.shutdown();
        }
    }
}