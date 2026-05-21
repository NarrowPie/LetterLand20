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

    // 🚀 NEW SECURITY TRACKER: Locks down true words and shields them from auto-spell mutations
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

    private final ActivityResultLauncher<Void> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null && !pendingWord.isEmpty()) {
                    new Thread(() -> saveToAlmanac(pendingWord, bitmap)).start();
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
        } else {
            Toast.makeText(this, "Microphone permission required! Leaving activity...", Toast.LENGTH_LONG).show();
            finish();
            return;
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
        if (SpeechRecognizer.isRecognitionAvailable(this) && speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            setupSpeechListener();

            speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-PH");
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-PH");
            speechIntent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "en-PH");
            speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

            speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600);
            speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600);
            speechIntent.putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 500);
            speechIntent.putExtra("android.speech.extras.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 600);
            speechIntent.putExtra("android.speech.extras.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 600);
        }
    }

    // 🚀 UPDATED: Inflates and hooks up the new internal popup "Proceed" option button
    private void showCustomVoiceDialog() {
        if (speechRecognizer == null) {
            initializeSpeechEngine();
        } else {
            try {
                speechRecognizer.cancel();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        rawVoiceOutputBuffer = ""; // Reset sample tracking lines securely

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_voice_assist, null);
        tvVoiceStatus = dialogView.findViewById(R.id.tvVoiceStatus);
        MaterialButton btnCancelVoice = dialogView.findViewById(R.id.btnCancelVoice);
        MaterialButton btnProceedVoice = dialogView.findViewById(R.id.btnProceedVoice); // 🚀 NEW LAYOUT FIELD

        voiceDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (voiceDialog.getWindow() != null) {
            voiceDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        btnCancelVoice.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
            }
            voiceDialog.dismiss();
        });

        // 🚀 NEW WORKER LOOP: Manual choice intercept bypasses speech timeline timeouts instantly
        if (btnProceedVoice != null) {
            btnProceedVoice.setOnClickListener(v -> {
                SoundManager.getInstance(this).playClick();
                if (speechRecognizer != null) {
                    speechRecognizer.stopListening(); // Halts hardware locks
                }

                String fallbackWord = rawVoiceOutputBuffer.isEmpty() ? "MIC" : rawVoiceOutputBuffer;
                voiceDialog.dismiss();

                // Direct application loop execution on the main thread
                isVoiceInputActive = true;
                currentlyDetectedWord = fallbackWord;
                tvLiveText.setText(fallbackWord);
                drawingView.setTracingWord(fallbackWord);

                if (isTtsReady) {
                    textToSpeech.speak("Let's trace " + fallbackWord, TextToSpeech.QUEUE_FLUSH, null, "VOICE_TRACE_ID");
                }
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
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (tvVoiceStatus != null) tvVoiceStatus.setText("Speak now!");
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {
                if (tvVoiceStatus != null && tvVoiceStatus.getText().toString().startsWith("Speaking:")) {
                    return;
                }
                if (rmsdB > 2.0f && tvVoiceStatus != null) {
                    tvVoiceStatus.setText("Hearing sounds...");
                }
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                if (tvVoiceStatus != null) tvVoiceStatus.setText("Analyzing word...");
            }

            @Override
            public void onError(int error) {
                String message;
                boolean fatalError = false;

                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO: message = "Audio recording error."; break;
                    case SpeechRecognizer.ERROR_CLIENT: message = "Synchronizing audio... Say your word."; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Mic permission missing!"; fatalError = true; break;
                    case SpeechRecognizer.ERROR_NETWORK: message = "Network connection issue."; break;
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Network timed out."; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No speech heard. Try again!"; break;
                    case SpeechRecognizer.ERROR_NO_MATCH: message = "Didn't catch that. Tap stop and retry!"; break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "Mic busy. Speak clearly now."; break;
                    default: message = "Ready. Please say your word."; break;
                }

                if (tvVoiceStatus != null) {
                    tvVoiceStatus.setText(message);
                }

                if (fatalError && voiceDialog != null && voiceDialog.isShowing()) {
                    voiceDialog.dismiss();
                    Toast.makeText(WriteActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partialMatches != null && !partialMatches.isEmpty() && tvVoiceStatus != null) {
                    String textSample = partialMatches.get(0).toUpperCase().trim();

                    if (textSample.contains(" ")) {
                        textSample = textSample.substring(0, textSample.indexOf(" "));
                    }

                    if (textSample.equals("RIGHT") || textSample.equals("RIDE") || textSample.equals("RITES")) {
                        textSample = "WRITE";
                    }

                    rawVoiceOutputBuffer = textSample; // Store sample securely inside buffer line array references
                    tvVoiceStatus.setText("Speaking: " + textSample);
                }
            }

            @Override
            public void onResults(Bundle results) {
                if (voiceDialog != null && voiceDialog.isShowing()) voiceDialog.dismiss();

                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {

                    String rawSpoken = matches.get(0).toUpperCase().trim();

                    if (rawSpoken.contains(" ")) {
                        rawSpoken = rawSpoken.substring(0, rawSpoken.indexOf(" "));
                    }

                    String finalSpokenWord = rawSpoken;

                    for (String candidate : matches) {
                        String cleanCandidate = candidate.toUpperCase().trim();
                        if (cleanCandidate.contains(" ")) {
                            cleanCandidate = cleanCandidate.substring(0, cleanCandidate.indexOf(" "));
                        }
                        if (DICTIONARY.contains(cleanCandidate)) {
                            finalSpokenWord = cleanCandidate;
                            break;
                        }
                    }

                    boolean containsRight = false;
                    boolean containsWrite = false;
                    for (String candidate : matches) {
                        String upperCand = candidate.toUpperCase().trim();
                        if (upperCand.equals("RIGHT")) containsRight = true;
                        if (upperCand.equals("WRITE")) containsWrite = true;
                    }

                    if (containsRight && containsWrite) {
                        finalSpokenWord = "WRITE";
                    }

                    if (finalSpokenWord.length() > 10) {
                        finalSpokenWord = finalSpokenWord.substring(0, 10);
                    }

                    final String closureWord = finalSpokenWord;
                    runOnUiThread(() -> {
                        isVoiceInputActive = true;
                        currentlyDetectedWord = closureWord;
                        tvLiveText.setText(closureWord);

                        drawingView.setTracingWord(closureWord);

                        if (isTtsReady) {
                            textToSpeech.speak("Let's trace " + closureWord, TextToSpeech.QUEUE_FLUSH, null, "VOICE_TRACE_ID");
                        }
                    });
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

                        isVoiceInputActive = false; // User touched the surface, reverting to freehand verification
                        currentlyDetectedWord = cleanWord;
                        tvLiveText.setText(cleanWord);

                        // PHASE 3: REAL-TIME CHARACTER SOUND-OUT
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
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvLiveText.setText("...");
                });
    }

    private String getPhonicSound(String letter) {
        switch (letter) {
            case "A": return "ah";
            case "B": return "buh";
            case "C": return "cuh";
            case "D": return "duh";
            case "E": return "eh";
            case "F": return "fuh";
            case "G": return "guh";
            case "H": return "huh";
            case "I": return "ih";
            case "J": return "juh";
            case "K": return "kuh";
            case "L": return "uhl";
            case "M": return "muh";
            case "N": return "nuh";
            case "O": return "ah";
            case "P": return "puh";
            case "Q": return "kwuh";
            case "R": return "ruh";
            case "S": return "suh";
            case "T": return "tuh";
            case "U": return "uh";
            case "V": return "vuh";
            case "W": return "wuh";
            case "X": return "ksuh";
            case "Y": return "yuh";
            case "Z": return "zuh";
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
        if (scanRunnable != null) {
            scanHandler.removeCallbacks(scanRunnable);
        }
    }

    // 🚀 FIXED: Dynamic bypass gate completely disables fuzzy auto-spelling for mic input
    private void checkWordDatabase(String word) {
        new Thread(() -> {
            String targetWord = word.toUpperCase().trim();

            // 🚀 SHIELD GATE: If it came from voice assist, skip Levenshtein and accept "MIC", "LAG", "CUT" exactly as spoken!
            if (!isVoiceInputActive && !DICTIONARY.contains(targetWord)) {
                targetWord = findClosestWord(targetWord);
            }

            final String processedWord = targetWord;
            String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
            WordEntry savedWord = AppDatabase.getInstance(this).wordDao().findWordForProfile(processedWord, player);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (savedWord != null) {
                    android.content.Intent intent = new android.content.Intent(WriteActivity.this, WordDetailActivity.class);
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

    private String findClosestWord(String scannedWord) {
        if (scannedWord == null || scannedWord.isEmpty()) return scannedWord;
        if (DICTIONARY.contains(scannedWord)) return scannedWord;

        String bestMatch = scannedWord;
        int lowestDistance = 999;
        int maxAllowedDifferences = 3;

        for (String dictionaryWord : DICTIONARY) {
            int distance = calculateEditDistance(scannedWord, dictionaryWord);
            if (distance < lowestDistance && distance <= maxAllowedDifferences) {
                lowestDistance = distance;
                bestMatch = dictionaryWord;
            }
        }
        return bestMatch;
    }

    private int calculateEditDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    private void showNewWordDialog(String wordToSave) {
        if (isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_word, null);
        newWordDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (newWordDialog.getWindow() != null) {
            newWordDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvDetected = dialogView.findViewById(R.id.tvDetectedWord);
        tvDetected.setText(wordToSave);

        dialogView.findViewById(R.id.btnDialogCamera).setOnClickListener(v1 -> {
            SoundManager.getInstance(this).playShutter();
            pendingWord = wordToSave;
            takePictureLauncher.launch(null);
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

    private void saveToAlmanac(String word, Bitmap bitmap) {
        String fileName = "word_" + word + "_" + System.currentTimeMillis() + ".jpg";
        java.io.File file = new java.io.File(getExternalFilesDir(null), fileName);

        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            Bitmap fixedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(fixedBitmap);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, 0, 0, null);

            fixedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);

            String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
            WordEntry newEntry = new WordEntry(word, player, file.getAbsolutePath());
            AppDatabase.getInstance(this).wordDao().insert(newEntry);

            DICTIONARY.add(word.toUpperCase().trim());

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, word + " saved!", Toast.LENGTH_SHORT).show();
                pendingWord = "";

                android.content.Intent intent = new android.content.Intent(WriteActivity.this, WordDetailActivity.class);
                intent.putExtra("WORD_TEXT", word);
                intent.putExtra("IMAGE_PATH", file.getAbsolutePath());
                intent.putExtra("SOURCE_PAGE", "WRITE");
                intent.putExtra("IS_NEW_WORD", true);
                startActivity(intent);

                resetCanvasAndText();
            });
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (newWordDialog != null && newWordDialog.isShowing()) {
            newWordDialog.dismiss();
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