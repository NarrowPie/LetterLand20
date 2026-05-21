package com.example.letterland;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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

    private DigitalInkRecognizer recognizer;
    private String pendingWord = "";
    private String currentlyDetectedWord = "";
    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable;

    private final Set<String> DICTIONARY = new HashSet<>();
    private String lastSpokenLetter = "";
    private int lastSpokenWordLength = 0;

    // 🎙️ Speech Recognizer Fields
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private AlertDialog voiceDialog;
    private TextView tvVoiceStatus;
    private boolean isVoiceInputActive = false;
    private String rawVoiceOutputBuffer = "";

    // 🗣️ TextToSpeech Fields
    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;

    // ⏳ Tracking fields to safely handle button spam clicks exclusively
    private boolean isButtonSpamLocked = false;
    private final Handler spamHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    showCustomVoiceDialog();
                } else {
                    Toast.makeText(this, "Microphone permission denied!", Toast.LENGTH_LONG).show();
                }
            }
    );

    private final ActivityResultLauncher<Void> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null && !pendingWord.isEmpty()) {
                    try {
                        String tempName = "temp_word_" + System.currentTimeMillis() + ".jpg";
                        File tempFile = new File(getExternalFilesDir(null), tempName);
                        try (FileOutputStream out = new FileOutputStream(tempFile)) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                        }

                        Intent intent = new Intent(WriteActivity.this, WordDetailActivity.class);
                        intent.putExtra("WORD_TEXT", pendingWord);
                        intent.putExtra("IMAGE_PATH", tempFile.getAbsolutePath());
                        intent.putExtra("SOURCE_PAGE", "WRITE");
                        intent.putExtra("IS_NEW_WORD", true);
                        startActivity(intent);

                        resetCanvasAndText();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
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
        MaterialButton btnClear = findViewById(R.id.btnClear);
        MaterialButton btnProceed = findViewById(R.id.btnProceed);
        ImageButton btnBack = findViewById(R.id.btnBackWrite);
        MaterialButton btnVoiceAssist = findViewById(R.id.btnVoiceAssist);
        MaterialButton btnSpeakLiveText = findViewById(R.id.btnSpeakLiveText);

        btnBack.setOnClickListener(v -> finish());

        // Initialize TextToSpeech Engine
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

        // Configure Audio Focus Ducking Listeners for TTS
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

        // Click Listener to speak currently detected live text strings with Isolated Spam Guard
        if (btnSpeakLiveText != null) {
            btnSpeakLiveText.setOnClickListener(v -> {
                if (!isTtsReady) {
                    Toast.makeText(this, "Voice is loading...", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 🛑 BUTTON SPAM GUARD: Block click execution instantly if locked
                if (isButtonSpamLocked) {
                    return;
                }

                if (currentlyDetectedWord != null && !currentlyDetectedWord.isEmpty() && !currentlyDetectedWord.equals("...")) {
                    // Activate button lock threshold
                    isButtonSpamLocked = true;

                    speakTextDirectly(currentlyDetectedWord);

                    // Enforce a strict 1-second delay after trigger before unlocking the button interaction again
                    spamHandler.postDelayed(() -> isButtonSpamLocked = false, 1000);
                } else {
                    Toast.makeText(this, "Write something first!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Initialize Lifecycle-Bound Single Instance Engine
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            initializeSpeechIntent();
            setupSpeechListener();
        }

        // Asynchronously populate dictionary indices
        new Thread(this::loadDictionaryData).start();

        tvLiveText.setText("Loading Model...");

        try {
            DigitalInkRecognitionModelIdentifier modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US");
            DigitalInkRecognitionModel model = DigitalInkRecognitionModel.builder(modelIdentifier).build();

            RemoteModelManager.getInstance().download(model, new DownloadConditions.Builder().build())
                    .addOnSuccessListener(aVoid -> {
                        recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build());
                        tvLiveText.setText("...");
                    })
                    .addOnFailureListener(e -> tvLiveText.setText("Error"));
        } catch (MlKitException e) {
            e.printStackTrace();
        }

        drawingView.setOnDrawListener(new DrawingView.OnDrawListener() {
            @Override
            public void onDrawStarted() {
                SoundManager.getInstance(WriteActivity.this).startScratchSound();
                if (scanRunnable != null) scanHandler.removeCallbacks(scanRunnable);
            }

            @Override
            public void onDrawFinished() {
                SoundManager.getInstance(WriteActivity.this).stopScratchSound();
                scanRunnable = () -> performScan();
                scanHandler.postDelayed(scanRunnable, 600);
            }
        });

        btnVoiceAssist.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                showCustomVoiceDialog();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        });

        btnClear.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            resetCanvasAndText();
        });

        btnProceed.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            if (currentlyDetectedWord.isEmpty() || currentlyDetectedWord.equals("...")) {
                Toast.makeText(this, "Write clearly first!", Toast.LENGTH_SHORT).show();
            } else {
                checkWordDatabase(currentlyDetectedWord);
            }
        });
    }

    private void initializeSpeechIntent() {
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-PH");
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    }

    private void loadDictionaryData() {
        try {
            java.io.InputStream is = getAssets().open("dictionary.txt");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.toUpperCase().trim();
                if (!word.isEmpty()) DICTIONARY.add(word);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
            List<WordEntry> savedWords = AppDatabase.getInstance(this).wordDao().getAllWordsForProfile(player);
            for (WordEntry entry : savedWords) {
                DICTIONARY.add(entry.word.toUpperCase().trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String mapIsolatedLetter(String text) {
        if (text == null || text.trim().length() != 1) {
            return text;
        }
        String letter = text.trim().toUpperCase();
        switch (letter) {
            case "A": return "Ay";
            case "B": return "Bee";
            case "C": return "Cee";
            case "D": return "Dee";
            case "E": return "Ee";
            case "F": return "Eff";
            case "G": return "Gee";
            case "H": return "Aitch";
            case "I": return "Aye";
            case "J": return "Jay";
            case "K": return "Kay";
            case "L": return "Ell";
            case "M": return "Emm";
            case "N": return "Enn";
            case "O": return "Oh";
            case "P": return "Pee";
            case "Q": return "Cue";
            case "R": return "Ar";
            case "S": return "Ess";
            case "T": return "Tee";
            case "U": return "You";
            case "V": return "Vee";
            case "W": return "Double You";
            case "X": return "Ex";
            case "Y": return "Wye";
            case "Z": return "Zed";
            default: return text;
        }
    }

    private void showCustomVoiceDialog() {
        if (speechRecognizer == null) return;
        rawVoiceOutputBuffer = "";
        isVoiceInputActive = false;

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
            speechRecognizer.cancel();
            voiceDialog.dismiss();
        });

        btnProceedVoice.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            speechRecognizer.cancel();

            String verifiedWord = rawVoiceOutputBuffer.isEmpty() ? "MIC" : rawVoiceOutputBuffer;
            voiceDialog.dismiss();

            isVoiceInputActive = true;
            currentlyDetectedWord = verifiedWord;
            tvLiveText.setText(verifiedWord);
            drawingView.setTracingWord(verifiedWord);

            if (isTtsReady) {
                String spokenTarget = mapIsolatedLetter(verifiedWord);
                textToSpeech.speak("Let's trace " + spokenTarget, TextToSpeech.QUEUE_FLUSH, null, "VOICE_TRACE_ID");
            }
        });

        btnRetryVoice.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            speechRecognizer.cancel();
            rawVoiceOutputBuffer = "";
            tvVoiceStatus.setText("Listening again...");
            speechRecognizer.startListening(speechIntent);
        });

        voiceDialog.show();
        tvVoiceStatus.setText("Listening...");

        // ⚡ OPTIMIZED: Launches speech recording loop instantly without artificial thread blocks
        speechRecognizer.startListening(speechIntent);
    }

    private void setupSpeechListener() {
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
                if (tvVoiceStatus != null && !rawVoiceOutputBuffer.isEmpty()) {
                    tvVoiceStatus.setText("Heard: " + rawVoiceOutputBuffer + "\nTap PROCEED to trace!");
                }
            }

            @Override
            public void onError(int error) {
                if (tvVoiceStatus == null) return;
                switch (error) {
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: tvVoiceStatus.setText("Please say your word!"); break;
                    case SpeechRecognizer.ERROR_NO_MATCH: tvVoiceStatus.setText("Didn't catch that. Try again!"); break;
                    default: tvVoiceStatus.setText("Tap Retry to speak."); break;
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                processVoiceResults(partialResults);
            }

            @Override
            public void onResults(Bundle results) {
                processVoiceResults(results);
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void processVoiceResults(Bundle bundle) {
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty() && tvVoiceStatus != null) {
            String rawInput = matches.get(0);
            rawVoiceOutputBuffer = cleanAndVerifySpeechInput(rawInput, matches);
            tvVoiceStatus.setText("Heard: " + rawVoiceOutputBuffer);
        }
    }

    private String cleanAndVerifySpeechInput(String primeText, ArrayList<String> alternatives) {
        String input = primeText.toUpperCase().trim();

        for (String candidate : alternatives) {
            String cleanCand = candidate.toUpperCase().trim();
            if (DICTIONARY.contains(cleanCand)) {
                return cleanCand;
            }
        }

        if (input.equals("RIGHT") || input.equals("RIDE") || input.equals("RITES")) {
            return "WRITE";
        }

        if (input.contains(" ") && !DICTIONARY.contains(input)) {
            input = input.substring(0, input.indexOf(" "));
        }

        return input;
    }

    private void performScan() {
        if (isFinishing() || isDestroyed() || recognizer == null) return;

        Ink ink = drawingView.getInk();
        if (ink.getStrokes().isEmpty()) return;

        recognizer.recognize(ink)
                .addOnSuccessListener(result -> {
                    if (isFinishing() || isDestroyed() || result.getCandidates().isEmpty()) return;

                    String cleanWord = result.getCandidates().get(0).getText().toUpperCase().trim();
                    if (cleanWord.length() > 15) cleanWord = cleanWord.substring(0, 15);

                    isVoiceInputActive = false;
                    currentlyDetectedWord = cleanWord;
                    tvLiveText.setText(cleanWord);

                    if (!cleanWord.isEmpty()) {
                        String newlyWrittenLetter = cleanWord.substring(cleanWord.length() - 1);
                        int currentLength = cleanWord.length();

                        if (!newlyWrittenLetter.equals(lastSpokenLetter) || currentLength != lastSpokenWordLength) {
                            lastSpokenLetter = newlyWrittenLetter;
                            lastSpokenWordLength = currentLength;
                            playLocalLetterSound(newlyWrittenLetter);
                        }
                    }
                });
    }

    private void playLocalLetterSound(String letter) {
        String resName = letter.toLowerCase().trim();
        int resId = getResources().getIdentifier(resName, "raw", getPackageName());
        if (resId != 0) {
            SoundManager.getInstance(this).playPhonicAsset(this, resId);
        }
    }

    // Direct Audio Utility Pipeline Method with Flushed Queue Execution
    private void speakTextDirectly(String textToSpeak) {
        String optimizedString = mapIsolatedLetter(textToSpeak);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            textToSpeech.setAudioAttributes(audioAttributes);
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);

            // Using QUEUE_FLUSH interrupts any ongoing direct speaks instantly
            textToSpeech.speak(optimizedString, TextToSpeech.QUEUE_FLUSH, params, "TTS_DIRECT_ID");
        } else {
            textToSpeech.speak(optimizedString, TextToSpeech.QUEUE_FLUSH, null, "TTS_DIRECT_ID");
        }
    }

    private void checkWordDatabase(String word) {
        new Thread(() -> {
            String targetWord = word.toUpperCase().trim();

            if (isVoiceInputActive && !DICTIONARY.contains(targetWord)) {
                targetWord = findPhoneticMatch(targetWord);
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
        if (scannedWord == null || scannedWord.isEmpty() || DICTIONARY.contains(scannedWord)) return scannedWord;

        String scannedSoundex = getSoundexCode(scannedWord);
        for (String dictionaryWord : DICTIONARY) {
            if (getSoundexCode(dictionaryWord).equals(scannedSoundex)) {
                return dictionaryWord;
            }
        }
        return scannedWord;
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

    private void showNewWordDialog(String wordToSave) {
        if (isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_word, null);
        AlertDialog newWordDialog = new AlertDialog.Builder(this).setView(dialogView).create();

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

    private void resetCanvasAndText() {
        drawingView.resetFullCanvas();
        tvLiveText.setText("...");
        currentlyDetectedWord = "";
        lastSpokenLetter = "";
        lastSpokenWordLength = 0;
        isVoiceInputActive = false;
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

        // Remove any active delay triggers to safeguard resource leaks
        spamHandler.removeCallbacksAndMessages(null);

        if (speechRecognizer != null) {
            speechRecognizer.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}