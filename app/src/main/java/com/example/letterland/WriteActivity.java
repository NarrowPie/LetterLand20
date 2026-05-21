package com.example.letterland;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WriteActivity extends AppCompatActivity {

    private DrawingView drawingView;
    private TextView tvLiveText;
    private MaterialButton btnSpeakLiveText;

    private DigitalInkRecognizer recognizer;

    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;

    private String pendingWord = "";
    private String currentlyDetectedWord = "";

    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable;

    private AlertDialog newWordDialog;

    // PHASE 2: LOCAL RUNTIME DICTIONARY INDEX FOR INVENTIVE SPELLING
    private final Set<String> DICTIONARY = new HashSet<>();

    // PHASE 3: CACHE VARIABLE TO PREVENT DUPLICATE AUDIO FEEDBACK STUTTERING
    private String lastSpokenLetter = "";

    // CUSTOM POPUP SPEECH RECOGNIZER PIPELINES
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private AlertDialog voiceDialog;
    private TextView tvVoiceStatus;

    // Tracker variables to shield words from auto-spell mutations
    private boolean isVoiceInputActive = false;
    private String rawVoiceOutputBuffer = "";

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    initializeSpeechEngine();
                } else {
                    Toast.makeText(this, "Microphone permission denied! Exiting game mode...", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
    );

    // 🚀 BULLETPROOF CAMERA CALLBACK: Instantly forwards text and temporary images directly to Detail Activity without background blockages!
    private final ActivityResultLauncher<Void> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null && !pendingWord.isEmpty()) {
                    try {
                        // Save to a clean temp file safely to avoid database context conflicts during transitions
                        String tempName = "temp_word_" + System.currentTimeMillis() + ".jpg";
                        File tempFile = new File(getExternalFilesDir(null), tempName);
                        try (FileOutputStream out = new FileOutputStream(tempFile)) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                        }

                        Toast.makeText(this, "Image captured! Loading detail...", Toast.LENGTH_SHORT).show();

                        // 🚀 FORWARD INTENT: Launches detail layout immediately!
                        Intent intent = new Intent(WriteActivity.this, WordDetailActivity.class);
                        intent.putExtra("WORD_TEXT", pendingWord);
                        intent.putExtra("IMAGE_PATH", tempFile.getAbsolutePath());
                        intent.putExtra("SOURCE_PAGE", "WRITE");
                        intent.putExtra("IS_NEW_WORD", true);
                        startActivity(intent);

                        // Clear input configurations safely
                        resetCanvasAndText();

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Transition failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "No picture taken", Toast.LENGTH_SHORT).show();
                    resetCanvasAndText();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write);

        drawingView = findViewById(R.id.drawingView);
        tvLiveText = findViewById(R.id.tvLiveText);
        btnSpeakLiveText = findViewById(R.id.btnSpeakLiveText);
        MaterialButton btnClear = findViewById(R.id.btnClear);
        MaterialButton btnProceed = findViewById(R.id.btnProceed);
        ImageButton btnBack = findViewById(R.id.btnBackWrite);
        MaterialButton btnVoiceAssist = findViewById(R.id.btnVoiceAssist);

        btnBack.setOnClickListener(v -> finish());

        // Populate Local Dictionary Index
        new Thread(() -> {
            try {
                java.io.InputStream is = getAssets().open("dictionary.txt");
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.toUpperCase().trim();
                    if (!word.isEmpty()) {
                        DICTIONARY.add(word);
                    }
                }
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
                List<WordEntry> savedWords = AppDatabase.getInstance(WriteActivity.this).wordDao().getAllWordsForProfile(player);
                for (WordEntry entry : savedWords) {
                    String dbWord = entry.word.toUpperCase().trim();
                    DICTIONARY.add(dbWord);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

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
                speakTextDirectly(currentlyDetectedWord);
            } else {
                Toast.makeText(this, "Write something first!", Toast.LENGTH_SHORT).show();
            }
        });

        tvLiveText.setText("Loading.....");

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
                        Toast.makeText(this, "Failed to download offline model.", Toast.LENGTH_SHORT).show();
                        tvLiveText.setText("Error");
                    });

        } catch (MlKitException e) {
            e.printStackTrace();
        }

        drawingView.setOnDrawListener(new DrawingView.OnDrawListener() {
            @Override
            public void onDrawStarted() {
                SoundManager.getInstance(WriteActivity.this).startScratchSound();
                if (scanRunnable != null) {
                    scanHandler.removeCallbacks(scanRunnable);
                }
            }

            @Override
            public void onDrawFinished() {
                SoundManager.getInstance(WriteActivity.this).stopScratchSound();
                scanRunnable = () -> performScan();
                scanHandler.postDelayed(scanRunnable, 600);
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initializeSpeechEngine();
        }

        if (btnVoiceAssist != null) {
            btnVoiceAssist.setOnClickListener(v -> {
                SoundManager.getInstance(this).playClick();
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    showCustomVoiceDialog();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                }
            });
        }

        btnClear.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            resetCanvasAndText();
        });

        btnProceed.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            if (currentlyDetectedWord.isEmpty() || currentlyDetectedWord.equals("...")) {
                Toast.makeText(this, "I couldn't read that! Try writing clearer.", Toast.LENGTH_SHORT).show();
            } else {
                checkWordDatabase(currentlyDetectedWord);
            }
        });
    }

    private void initializeSpeechEngine() {
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-PH");
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-PH");
        speechIntent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "en-PH");
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
    }

    private void showCustomVoiceDialog() {
        isVoiceInputActive = false;
        rawVoiceOutputBuffer = "";

        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
            speechRecognizer = null;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        setupSpeechListener();
        initializeSpeechEngine();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_voice_assist, null);
        tvVoiceStatus = dialogView.findViewById(R.id.tvVoiceStatus);
        MaterialButton btnCancelVoice = dialogView.findViewById(R.id.btnCancelVoice);
        MaterialButton btnProceedVoice = dialogView.findViewById(R.id.btnProceedVoice);
        MaterialButton btnRetryVoice = dialogView.findViewById(R.id.btnRetryVoice);

        voiceDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(dialogView)
                .create();

        if (voiceDialog.getWindow() != null) {
            voiceDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        btnCancelVoice.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            }
            isVoiceInputActive = false;
            voiceDialog.dismiss();
        });

        // 🚀 PROCEED BUTTON: Fixed to be the exclusive way to confirm and leave the dialog
        if (btnProceedVoice != null) {
            btnProceedVoice.setOnClickListener(v -> {
                SoundManager.getInstance(this).playClick();
                if (speechRecognizer != null) {
                    speechRecognizer.stopListening();
                    speechRecognizer.cancel();
                }

                String fallbackWord = rawVoiceOutputBuffer.isEmpty() ? "MIC" : rawVoiceOutputBuffer;
                voiceDialog.dismiss();

                // Force layout state changes into tracing template mechanics safely
                isVoiceInputActive = true;
                currentlyDetectedWord = fallbackWord;
                tvLiveText.setText(fallbackWord);
                drawingView.setTracingWord(fallbackWord);

                if (isTtsReady) {
                    textToSpeech.speak("Let's trace " + fallbackWord, TextToSpeech.QUEUE_FLUSH, null, "VOICE_TRACE_ID");
                }
            });
        }

        // 🚀 RETRY BUTTON: Clears buffers and requests a new session without closing the frame
        if (btnRetryVoice != null) {
            btnRetryVoice.setOnClickListener(v -> {
                SoundManager.getInstance(this).playClick();
                if (speechRecognizer != null) {
                    speechRecognizer.cancel();
                }
                rawVoiceOutputBuffer = "";
                tvVoiceStatus.setText("Listening again...");
                speechRecognizer.startListening(speechIntent);
            });
        }

        voiceDialog.show();
        tvVoiceStatus.setText("Listening...");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (speechRecognizer != null && speechIntent != null && voiceDialog != null && voiceDialog.isShowing()) {
                speechRecognizer.startListening(speechIntent);
            }
        }, 250);
    }

    private void setupSpeechListener() {
        if (speechRecognizer == null) return;

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (tvVoiceStatus != null && rawVoiceOutputBuffer.isEmpty()) {
                    tvVoiceStatus.setText("Speak now!");
                }
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                // 🚀 FIX: Stay inside the dialog completely, instructing the child to finish up
                if (tvVoiceStatus != null && !rawVoiceOutputBuffer.isEmpty()) {
                    tvVoiceStatus.setText("Heard: " + rawVoiceOutputBuffer + "\nTap PROCEED to trace!");
                }
            }

            @Override
            public void onError(int error) {
                String message;
                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO: message = "Audio error."; break;
                    case SpeechRecognizer.ERROR_CLIENT: message = "Tap Retry to try again."; break;
                    case SpeechRecognizer.ERROR_NETWORK: message = "Network issue."; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "Please say your word!"; break;
                    case SpeechRecognizer.ERROR_NO_MATCH: message = "Didn't catch that. Try again!"; break;
                    default: message = "Ready to record. Please speak."; break;
                }

                if (tvVoiceStatus != null && rawVoiceOutputBuffer.isEmpty()) {
                    tvVoiceStatus.setText(message);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partialMatches != null && !partialMatches.isEmpty() && tvVoiceStatus != null) {
                    String textSample = partialMatches.get(0).toUpperCase().trim();

                    if (textSample.contains(" ") && !DICTIONARY.contains(textSample)) {
                        textSample = textSample.substring(0, textSample.indexOf(" "));
                    }

                    if (textSample.equals("RIGHT") || textSample.equals("RIDE") || textSample.equals("RITES")) {
                        textSample = "WRITE";
                    }

                    rawVoiceOutputBuffer = textSample;
                    tvVoiceStatus.setText("Heard: " + textSample);
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String textSample = matches.get(0).toUpperCase().trim();

                    for (String candidate : matches) {
                        String cleanCand = candidate.toUpperCase().trim();
                        if (DICTIONARY.contains(cleanCand)) {
                            textSample = cleanCand;
                            break;
                        }
                    }

                    if (textSample.contains(" ") && !DICTIONARY.contains(textSample)) {
                        textSample = textSample.substring(0, textSample.indexOf(" "));
                    }

                    rawVoiceOutputBuffer = textSample;
                    if (tvVoiceStatus != null) {
                        tvVoiceStatus.setText("Heard: " + textSample + "\nTap PROCEED to trace!");
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
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

    private void performScan() {
        if (isFinishing() || isDestroyed()) return;
        if (recognizer == null) return;

        Ink ink = drawingView.getInk();
        if (ink.getStrokes().isEmpty()) return;

        recognizer.recognize(ink)
                .addOnSuccessListener(result -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!result.getCandidates().isEmpty()) {
                        String cleanWord = result.getCandidates().get(0).getText().toUpperCase().trim();
                        if (cleanWord.length() > 15) cleanWord = cleanWord.substring(0, 15);

                        isVoiceInputActive = false;
                        currentlyDetectedWord = cleanWord;
                        tvLiveText.setText(cleanWord);

                        if (!cleanWord.isEmpty()) {
                            String newlyWrittenLetter = cleanWord.substring(cleanWord.length() - 1);
                            if (!newlyWrittenLetter.equals(lastSpokenLetter)) {
                                lastSpokenLetter = newlyWrittenLetter;
                                String phonicSound = getPhonicSound(newlyWrittenLetter);
                                if (isTtsReady) {
                                    textToSpeech.speak(phonicSound, TextToSpeech.QUEUE_FLUSH, null, "PHONICS_FEEDBACK_ID");
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> tvLiveText.setText("..."));
    }

    private String getPhonicSound(String letter) {
        switch (letter) {
            case "A": return "ah"; case "B": return "buh"; case "C": return "cuh";
            case "D": return "duh"; case "E": return "eh"; case "F": return "fuh";
            case "G": return "guh"; case "H": return "huh"; case "I": return "ih";
            case "J": return "juh"; case "K": return "kuh"; case "L": return "uhl";
            case "M": return "muh"; case "N": return "nuh"; case "O": return "ah";
            case "P": return "puh"; case "Q": return "kwuh"; case "R": return "ruh";
            case "S": return "suh"; case "T": return "tuh"; case "U": return "uh";
            case "V": return "vuh"; case "W": return "wuh"; case "X": return "ksuh";
            case "Y": return "yuh"; case "Z": return "zuh";
            default: return letter.toLowerCase();
        }
    }

    private void speakTextDirectly(String textToSpeak) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            textToSpeech.setAudioAttributes(audioAttributes);
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
            textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_ADD, params, "TTS_DIRECT_ID");
        } else {
            textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, "TTS_DIRECT_ID");
        }
    }

    private void resetCanvasAndText() {
        drawingView.resetFullCanvas();
        tvLiveText.setText("...");
        currentlyDetectedWord = "";
        lastSpokenLetter = "";
        isVoiceInputActive = false;
    }

    private void checkWordDatabase(String word) {
        new Thread(() -> {
            String targetWord = word.toUpperCase().trim();

            if (!isVoiceInputActive) {
                // Freehand free input preserves text string characters without modifications
            } else {
                if (!DICTIONARY.contains(targetWord)) {
                    targetWord = findPhoneticMatch(targetWord);
                }
            }

            final String processedWord = targetWord;
            String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
            WordEntry savedWord = AppDatabase.getInstance(this).wordDao().findWordForProfile(processedWord, player);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (savedWord != null) {
                    Intent intent = new Intent(WriteActivity.this, WordDetailActivity.class);
                    intent.putExtra("WORD_TEXT", savedWord.word);
                    intent.putExtra("IMAGE_PATH", savedWord.imagePath);
                    intent.putExtra("SOURCE_PAGE", "WRITE");
                    intent.putExtra("IS_NEW_WORD", false);
                    startActivity(intent);
                    resetCanvasAndText();
                } else {
                    showNewWordDialog(processedWord);
                }
            });
        }).start();
    }

    private String findPhoneticMatch(String scannedWord) {
        if (scannedWord == null || scannedWord.isEmpty()) return scannedWord;
        if (DICTIONARY.contains(scannedWord)) return scannedWord;

        String scannedSoundex = getSoundexCode(scannedWord);
        for (String dictionaryWord : DICTIONARY) {
            if (getSoundexCode(dictionaryWord).equals(scannedSoundex)) {
                return dictionaryWord;
            }
        }

        String bestMatch = scannedWord;
        int lowestDistance = 999;
        for (String dictionaryWord : DICTIONARY) {
            int distance = calculateEditDistance(scannedWord, dictionaryWord);
            if (distance < lowestDistance && distance <= 2) {
                lowestDistance = distance;
                bestMatch = dictionaryWord;
            }
        }
        return bestMatch;
    }

    private String getSoundexCode(String s) {
        if (s == null || s.isEmpty()) return "0000";
        char[] x = s.toUpperCase().toCharArray();
        StringBuilder buffer = new StringBuilder();
        buffer.append(x[0]);

        for (int i = 1; i < x.length; i++) {
            switch (x[i]) {
                case 'B': case 'F': case 'P': case 'V': buffer.append('1'); break;
                case 'C': case 'G': case 'J': case 'K': case 'Q': case 'S': case 'X': case 'Z': buffer.append('2'); break;
                case 'D': case 'T': buffer.append('3'); break;
                case 'L': buffer.append('4'); break;
                case 'M': case 'N': buffer.append('5'); break;
                case 'R': buffer.append('6'); break;
            }
        }

        StringBuilder cleanBuffer = new StringBuilder();
        cleanBuffer.append(buffer.charAt(0));
        for (int i = 1; i < buffer.length(); i++) {
            if (buffer.charAt(i) != buffer.charAt(i - 1)) cleanBuffer.append(buffer.charAt(i));
        }

        while (cleanBuffer.length() < 4) cleanBuffer.append('0');
        return cleanBuffer.substring(0, 4);
    }

    private int calculateEditDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i; int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j]; costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    private void showNewWordDialog(String wordToSave) {
        if (isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_word, null);
        newWordDialog = new AlertDialog.Builder(this).setView(dialogView).create();

        if (newWordDialog.getWindow() != null) {
            newWordDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvDetected = dialogView.findViewById(R.id.tvDetectedWord);
        tvDetected.setText(wordToSave);

        dialogView.findViewById(R.id.btnDialogCamera).setOnClickListener(v1 -> {
            SoundManager.getInstance(this).playShutter();
            pendingWord = wordToSave;

            takePictureLauncher.launch(null); // Triggers camera capture frame cleanly
            newWordDialog.dismiss();
        });

        dialogView.findViewById(R.id.btnDialogLater).setOnClickListener(v1 -> {
            SoundManager.getInstance(this).playClick();
            newWordDialog.dismiss();
            resetCanvasAndText();
        });

        newWordDialog.setCancelable(false);
        newWordDialog.show();
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) { textToSpeech.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        super.onDestroy();
    }
}