package com.example.letterland;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.digitalink.DigitalInkRecognition;
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel;
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier;
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer;
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions;
import com.google.mlkit.vision.digitalink.Ink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuizActivity extends AppCompatActivity {

    private DrawingView drawingView;
    private TextView tvLiveText, tvProgress;
    private ImageView ivQuizImage;
    private MaterialButton btnSpeakLiveText;

    private DigitalInkRecognizer recognizer;

    // TTS Variables
    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;

    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable;

    private List<WordEntry> quizWords = new ArrayList<>();
    private ArrayList<String> correctAnswers = new ArrayList<>();
    private ArrayList<String> userAnswers = new ArrayList<>();

    private int currentQuestionIndex = 0;
    private String currentlyDetectedWord = "";

    // Thread Manager & Dialog Trackers to prevent memory leaks
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private Dialog lockedDialog;
    private Dialog zoomDialog;
    private AlertDialog confirmDialog;

    // 🚀 Bumper Variables 🚀
    private View layoutBumper;
    private TextView tvBumperLoading;
    private ProgressBar pbBumperLoading;
    private MaterialButton btnBumperStart;
    private boolean isFirstLevelLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        drawingView = findViewById(R.id.quizDrawingView);
        tvLiveText = findViewById(R.id.tvLiveText);
        tvProgress = findViewById(R.id.tvQuizProgress);
        ivQuizImage = findViewById(R.id.ivQuizImage);
        btnSpeakLiveText = findViewById(R.id.btnSpeakLiveText);

        // Setup Bumper UI
        layoutBumper = findViewById(R.id.layoutBumper);
        tvBumperLoading = findViewById(R.id.tvBumperLoading);
        pbBumperLoading = findViewById(R.id.pbBumperLoading);
        btnBumperStart = findViewById(R.id.btnBumperStart);

        // This click listener does nothing but prevents touches from passing through the bumper
        layoutBumper.setOnClickListener(v -> {});

        btnBumperStart.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            // Fade out the bumper smoothly
            layoutBumper.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        layoutBumper.setVisibility(View.GONE);
                        // 🚀 THE TRICK: We start the real Level 1 only AFTER pressing start!
                        loadCurrentQuestion();
                    });
        });

        findViewById(R.id.btnBackQuiz).setOnClickListener(v -> finish());

        findViewById(R.id.btnQuizClear).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            resetCanvasAndText();
        });

        findViewById(R.id.cardQuizImage).setOnClickListener(v -> {
            if (!quizWords.isEmpty()) {
                showZoomedImageDialog();
            }
        });

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    runOnUiThread(() -> Toast.makeText(this, "Voice language not supported.", Toast.LENGTH_SHORT).show());
                } else {
                    isTtsReady = true;
                }
            }
        });

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                SoundManager.getInstance(getApplicationContext()).duckBackgroundMusic();
            }

            @Override
            public void onDone(String utteranceId) {
                SoundManager.getInstance(getApplicationContext()).restoreBackgroundMusic();
            }

            @Override
            public void onError(String utteranceId) {
                SoundManager.getInstance(getApplicationContext()).restoreBackgroundMusic();
            }
        });

        btnSpeakLiveText.setOnClickListener(v -> {
            if (!isTtsReady) {
                Toast.makeText(this, "Voice is loading...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentlyDetectedWord != null && !currentlyDetectedWord.isEmpty() && !currentlyDetectedWord.equals("...")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    textToSpeech.setAudioAttributes(audioAttributes);
                    Bundle params = new Bundle();
                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
                    textToSpeech.speak(currentlyDetectedWord, TextToSpeech.QUEUE_ADD, params, "TTS_ID");
                } else {
                    textToSpeech.speak(currentlyDetectedWord, TextToSpeech.QUEUE_ADD, null, "TTS_ID");
                }
            } else {
                Toast.makeText(this, "Write an answer first!", Toast.LENGTH_SHORT).show();
            }
        });

        tvLiveText.setText("Loading......");

        try {
            DigitalInkRecognitionModelIdentifier modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US");
            DigitalInkRecognitionModel model = DigitalInkRecognitionModel.builder(modelIdentifier).build();

            RemoteModelManager manager = RemoteModelManager.getInstance();
            manager.download(model, new DownloadConditions.Builder().build())
                    .addOnSuccessListener(aVoid -> {
                        recognizer = DigitalInkRecognition.getClient(
                                DigitalInkRecognizerOptions.builder(model).build());

                        tvLiveText.setText("...");

                        if (!drawingView.getInk().getStrokes().isEmpty()) {
                            performScan();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load Handwriting AI.", Toast.LENGTH_SHORT).show();
                        tvLiveText.setText("Error");
                    });

        } catch (MlKitException e) {
            e.printStackTrace();
        }

        drawingView.setOnDrawListener(new DrawingView.OnDrawListener() {
            @Override
            public void onDrawStarted() {
                SoundManager.getInstance(QuizActivity.this).startScratchSound();
                if (scanRunnable != null) scanHandler.removeCallbacks(scanRunnable);
            }
            @Override
            public void onDrawFinished() {
                SoundManager.getInstance(QuizActivity.this).stopScratchSound();
                scanRunnable = () -> performScan();
                scanHandler.postDelayed(scanRunnable, 400);
            }
        });

        findViewById(R.id.btnQuizProceed).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();

            if (currentlyDetectedWord.isEmpty() || currentlyDetectedWord.equals("...")) {
                Toast.makeText(this, "Please write an answer first!", Toast.LENGTH_SHORT).show();
                return;
            }

            showCustomConfirmDialog();
        });

        // FIXED: Safe background fetching
        databaseExecutor.execute(() -> {
            String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
            List<WordEntry> quizReadyWords = AppDatabase.getInstance(this).wordDao().getStarredWordsForProfile(player);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                if (quizReadyWords.size() < 10) {
                    showLockedDialog();
                } else {
                    Collections.shuffle(quizReadyWords);
                    int limit = Math.min(quizReadyWords.size(), 10);
                    quizWords = quizReadyWords.subList(0, limit);

                    // 🚀 THE TRICK: Load the decoy image to absorb the glitch first!
                    loadSacrificialDummyImage();
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.getInstance(this).startBackgroundMusic();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.getInstance(this).pauseBackgroundMusic();
        SoundManager.getInstance(this).stopScratchSound();
    }

    private void showLockedDialog() {
        if (isFinishing() || isDestroyed()) return;

        lockedDialog = new Dialog(this);
        lockedDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        lockedDialog.setContentView(R.layout.dialog_mode_locked);
        lockedDialog.setCancelable(false);

        if (lockedDialog.getWindow() != null) {
            lockedDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        lockedDialog.findViewById(R.id.btnDialogOk).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            lockedDialog.dismiss();

            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        lockedDialog.show();
    }

    // 🚀 The Decoy: Absorbs the first-load glitch behind the Bumper
    private void loadSacrificialDummyImage() {
        if (isFinishing() || isDestroyed()) return;

        RequestListener<Drawable> requestListener = new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                runOnUiThread(() -> enableBumperStart());
                return false;
            }
            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                runOnUiThread(() -> enableBumperStart());
                return false;
            }
        };

        Glide.with(this)
                .load(R.drawable.title_logo) // Using your logo as the sacrificial image
                .override(250, 250)
                .centerCrop()
                .listener(requestListener)
                .into(ivQuizImage);
    }

    private void enableBumperStart() {
        isFirstLevelLoaded = true;
        pbBumperLoading.setVisibility(View.GONE);
        tvBumperLoading.setText("Ready!");
        btnBumperStart.setVisibility(View.VISIBLE);
    }

    private void loadCurrentQuestion() {
        if (isFinishing() || isDestroyed()) return;

        try {
            resetCanvasAndText();
            tvProgress.setText((currentQuestionIndex + 1) + "/" + quizWords.size());

            WordEntry currentWord = quizWords.get(currentQuestionIndex);

            if (currentWord.imagePath != null && !currentWord.imagePath.isEmpty()) {
                // The decoy took the hit, so this will now load smoothly like Level 2!
                Glide.with(this)
                        .load(currentWord.imagePath)
                        .override(250, 250)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .error(R.drawable.title_logo)
                        .into(ivQuizImage);

            } else {
                ivQuizImage.setImageDrawable(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showZoomedImageDialog() {
        if (isFinishing() || isDestroyed()) return;

        SoundManager.getInstance(this).playClick();

        zoomDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        zoomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        zoomDialog.setContentView(R.layout.dialog_zoom_image);

        if (zoomDialog.getWindow() != null) {
            zoomDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView ivZoomed = zoomDialog.findViewById(R.id.ivZoomedImage);
        WordEntry currentWord = quizWords.get(currentQuestionIndex);

        if (currentWord.imagePath != null && !currentWord.imagePath.isEmpty()) {
            Glide.with(zoomDialog.getContext())
                    .load(currentWord.imagePath)
                    .override(800, 800)
                    .fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(R.drawable.title_logo)
                    .into(ivZoomed);
        }

        zoomDialog.findViewById(R.id.rootZoomLayout).setOnClickListener(v1 -> {
            SoundManager.getInstance(this).playClick();
            zoomDialog.dismiss();
        });

        zoomDialog.show();
    }

    private void showCustomConfirmDialog() {
        if (isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_quiz_confirm, null);
        confirmDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView tvMessage = dialogView.findViewById(R.id.tvConfirmMessage);
        tvMessage.setText("Is '" + currentlyDetectedWord + "' your final answer?");

        dialogView.findViewById(R.id.btnCancelConfirm).setOnClickListener(v1 -> {
            SoundManager.getInstance(this).playClick();
            confirmDialog.dismiss();
        });

        dialogView.findViewById(R.id.btnYesConfirm).setOnClickListener(v1 -> {
            SoundManager.getInstance(this).playClick();
            confirmDialog.dismiss();

            correctAnswers.add(quizWords.get(currentQuestionIndex).word);
            userAnswers.add(currentlyDetectedWord);

            currentQuestionIndex++;

            if (currentQuestionIndex < quizWords.size()) {
                loadCurrentQuestion();
            } else {
                goToResults();
            }
        });

        confirmDialog.show();
    }

    private void goToResults() {
        if (scanRunnable != null) scanHandler.removeCallbacks(scanRunnable);

        int finalScore = 0;
        for (int i = 0; i < correctAnswers.size(); i++) {
            if (correctAnswers.get(i).equalsIgnoreCase(userAnswers.get(i))) {
                finalScore++;
            }
        }
        final int score = finalScore;

        // FIXED: Save the quiz result off the main thread to prevent UI freezing
        databaseExecutor.execute(() -> {
            String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
            long currentTime = System.currentTimeMillis();

            QuizRecord newRecord = new QuizRecord(player, score, correctAnswers.size(), currentTime, correctAnswers, userAnswers);
            AppDatabase.getInstance(QuizActivity.this).quizRecordDao().insertRecord(newRecord);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Intent intent = new Intent(QuizActivity.this, com.example.letterland.QuizResultActivity.class);
                intent.putStringArrayListExtra("CORRECT_ANSWERS", correctAnswers);
                intent.putStringArrayListExtra("USER_ANSWERS", userAnswers);
                startActivity(intent);
                finish();
            });
        });
    }

    private void resetCanvasAndText() {
        drawingView.clearCanvas();

        if (recognizer == null) {
            tvLiveText.setText("Loading AI...");
        } else {
            tvLiveText.setText("...");
        }

        currentlyDetectedWord = "";
        if (scanRunnable != null) scanHandler.removeCallbacks(scanRunnable);
    }

    private void performScan() {
        if (isFinishing() || isDestroyed()) return;

        if (recognizer == null) {
            tvLiveText.setText("Loading AI...");
            return;
        }

        Ink ink = drawingView.getInk();
        if (ink.getStrokes().isEmpty()) return;

        recognizer.recognize(ink)
                .addOnSuccessListener(result -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!result.getCandidates().isEmpty()) {
                        String cleanWord = result.getCandidates().get(0).getText().toUpperCase().trim();

                        if (cleanWord.length() > 10) {
                            cleanWord = cleanWord.substring(0, 10);
                        }

                        currentlyDetectedWord = cleanWord;
                        tvLiveText.setText(cleanWord);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvLiveText.setText("...");
                });
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        // FIXED: Dismiss dialogs so app doesn't crash on rotation
        if (lockedDialog != null && lockedDialog.isShowing()) lockedDialog.dismiss();
        if (zoomDialog != null && zoomDialog.isShowing()) zoomDialog.dismiss();
        if (confirmDialog != null && confirmDialog.isShowing()) confirmDialog.dismiss();

        // FIXED: Properly kill the background thread
        if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
            databaseExecutor.shutdown();
        }

        super.onDestroy();
        SoundManager.getInstance(this).stopScratchSound();
        if (scanRunnable != null) {
            scanHandler.removeCallbacks(scanRunnable);
        }
        if (recognizer != null) {
            recognizer.close();
        }
    }
}