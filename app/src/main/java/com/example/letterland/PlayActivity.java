package com.example.letterland;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private ImageView ivResultPicture;
    private FrameLayout scanContainer;
    private View highlightBox;
    private static final int CAMERA_PERMISSION_CODE = 10;
    private TextRecognizer textRecognizer;

    private Camera camera;
    private PointF manualFocusPoint = null;

    private String pendingWord = "";
    private String currentlyHighlightedWord = "";
    private Handler realTimeHandler = new Handler(Looper.getMainLooper());
    private boolean isScanningPaused = false;

    // 🚀 FIX 1: Changed to HashSet for instant O(1) lookups instead of slow O(N) linear scans
    private final Set<String> DICTIONARY = new HashSet<>();

    private ExecutorService cameraExecutor;

    private AlertDialog newWordDialog;

    private final ActivityResultLauncher<Void> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null && !pendingWord.isEmpty()) {
                    // 🚀 FIX 2: Used existing executor instead of spawning rogue threads
                    cameraExecutor.execute(() -> saveToAlmanac(pendingWord, bitmap));
                } else {
                    Toast.makeText(this, "No picture taken", Toast.LENGTH_SHORT).show();
                    resumeRealTimeScanning();
                }
            }
    );

    private void loadDictionaryFromAssets() {
        cameraExecutor.execute(() -> {
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
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Failed to load dictionary file!", Toast.LENGTH_SHORT).show();
                });
            }

            try {
                String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
                List<WordEntry> savedWords = AppDatabase.getInstance(PlayActivity.this).wordDao().getAllWordsForProfile(player);

                for (WordEntry entry : savedWords) {
                    String dbWord = entry.word.toUpperCase().trim();
                    DICTIONARY.add(dbWord); // HashSet naturally handles duplicates
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        cameraExecutor = Executors.newSingleThreadExecutor();
        loadDictionaryFromAssets();

        viewFinder = findViewById(R.id.viewFinder);
        ivResultPicture = findViewById(R.id.ivResultPicture);
        scanContainer = findViewById(R.id.scanContainer);
        highlightBox = findViewById(R.id.highlightBox);
        View btnBack = findViewById(R.id.btnBack);
        View btnScan = findViewById(R.id.btnScan);

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        scanContainer.setClickable(true);
        scanContainer.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float tapX = event.getX();
                float tapY = event.getY();

                manualFocusPoint = new PointF(tapX, tapY);

                if (action == MotionEvent.ACTION_DOWN && camera != null) {
                    int[] boxLoc = new int[2]; scanContainer.getLocationOnScreen(boxLoc);
                    int[] findLoc = new int[2]; viewFinder.getLocationOnScreen(findLoc);
                    float screenTapX = tapX + (boxLoc[0] - findLoc[0]);
                    float screenTapY = tapY + (boxLoc[1] - findLoc[1]);

                    MeteringPointFactory factory = viewFinder.getMeteringPointFactory();
                    MeteringPoint point = factory.createPoint(screenTapX, screenTapY);
                    FocusMeteringAction focusAction = new FocusMeteringAction.Builder(point).build();
                    camera.getCameraControl().startFocusAndMetering(focusAction);
                    v.performClick();
                }
                return true;
            }
            return false;
        });

        btnBack.setOnClickListener(v -> finish());

        btnScan.setOnClickListener(v -> {
            if (currentlyHighlightedWord.isEmpty()) {
                Toast.makeText(this, "Line up a word in the box first!", Toast.LENGTH_SHORT).show();
                return;
            }

            isScanningPaused = true;
            String wordToSearch = currentlyHighlightedWord;

            cameraExecutor.execute(() -> {
                String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
                WordEntry savedWord = AppDatabase.getInstance(this).wordDao().findWordForProfile(wordToSearch, player);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (savedWord != null) {
                        highlightBox.setVisibility(View.GONE);
                        android.content.Intent intent = new android.content.Intent(PlayActivity.this, WordDetailActivity.class);
                        intent.putExtra("WORD_TEXT", savedWord.word);
                        intent.putExtra("IMAGE_PATH", savedWord.imagePath);
                        intent.putExtra("SOURCE_PAGE", "SCANNER");
                        intent.putExtra("IS_NEW_WORD", false); // 🌟 PREVENTS DELETION EXPLOIT
                        startActivity(intent);
                    } else {
                        View dialogView = LayoutInflater.from(PlayActivity.this).inflate(R.layout.dialog_new_word, null);

                        newWordDialog = new AlertDialog.Builder(PlayActivity.this, R.style.CustomDialogTheme)
                                .setView(dialogView)
                                .create();

                        if (newWordDialog.getWindow() != null) {
                            newWordDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                        }

                        TextView tvDetected = dialogView.findViewById(R.id.tvDetectedWord);
                        tvDetected.setText(wordToSearch);

                        dialogView.findViewById(R.id.btnDialogCamera).setOnClickListener(v1 -> {
                            SoundManager.getInstance(PlayActivity.this).playShutter();
                            pendingWord = wordToSearch;
                            takePictureLauncher.launch(null);
                            newWordDialog.dismiss();
                        });

                        dialogView.findViewById(R.id.btnDialogLater).setOnClickListener(v1 -> {
                            newWordDialog.dismiss();
                            resumeRealTimeScanning();
                        });

                        newWordDialog.setCancelable(false);
                        newWordDialog.show();
                    }
                });
            });
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);

                startRealTimeScanning();

            } catch (Exception e) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private Bitmap enhanceImageForOCR(Bitmap original) {
        Bitmap enhanced = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(enhanced);
        Paint paint = new Paint();

        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);

        float contrast = 2.0f;
        float translate = (-0.5f * contrast + 0.5f) * 255f;
        ColorMatrix contrastMatrix = new ColorMatrix(new float[] {
                contrast, 0, 0, 0, translate,
                0, contrast, 0, 0, translate,
                0, 0, contrast, 0, translate,
                0, 0, 0, 1, 0
        });

        colorMatrix.postConcat(contrastMatrix);
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

        canvas.drawBitmap(original, 0, 0, paint);
        return enhanced;
    }

    private final Runnable realTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScanningPaused || isFinishing() || isDestroyed()) return;

            final Bitmap fullBitmap = viewFinder.getBitmap();
            if (fullBitmap == null) {
                realTimeHandler.postDelayed(this, 300);
                return;
            }

            int[] boxLoc = new int[2]; scanContainer.getLocationOnScreen(boxLoc);
            int[] findLoc = new int[2]; viewFinder.getLocationOnScreen(findLoc);
            int startX = Math.max(0, boxLoc[0] - findLoc[0]);
            int startY = Math.max(0, boxLoc[1] - findLoc[1]);
            int width = Math.min(scanContainer.getWidth(), fullBitmap.getWidth() - startX);
            int height = Math.min(scanContainer.getHeight(), fullBitmap.getHeight() - startY);

            if(width <= 0 || height <= 0 || startX < 0 || startY < 0) {
                fullBitmap.recycle();
                realTimeHandler.postDelayed(this, 300);
                return;
            }

            Bitmap croppedBitmap = Bitmap.createBitmap(fullBitmap, startX, startY, width, height);

            if (fullBitmap != croppedBitmap) {
                fullBitmap.recycle();
            }

            cameraExecutor.execute(() -> {
                if (isFinishing() || isDestroyed()) return;

                Bitmap cleanedBitmap = enhanceImageForOCR(croppedBitmap);
                InputImage image = InputImage.fromBitmap(cleanedBitmap, 0);

                textRecognizer.process(image)
                        .addOnSuccessListener(cameraExecutor, visionText -> {
                            processVisionTextInBackground(visionText, width, height);
                        })
                        .addOnCompleteListener(task -> {
                            croppedBitmap.recycle();
                            cleanedBitmap.recycle();

                            if (!isScanningPaused) {
                                realTimeHandler.postDelayed(realTimeRunnable, 300);
                            }
                        });
            });
        }
    };

    private void processVisionTextInBackground(Text visionText, int cropWidth, int cropHeight) {
        if (isScanningPaused || isFinishing() || isDestroyed()) return;

        String bestWord = "";
        Rect bestBox = null;
        float closestDistance = Float.MAX_VALUE;

        float targetX = cropWidth / 2f;
        float targetY = cropHeight / 2f;

        if (manualFocusPoint != null) {
            targetX = manualFocusPoint.x;
            targetY = manualFocusPoint.y;
        }

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    String rawWord = element.getText().toUpperCase().replaceAll("[^A-Z]", "");
                    String smartWord = findClosestWord(rawWord);

                    if (!smartWord.isEmpty() && smartWord.length() <= 10) {
                        Rect wordBox = element.getBoundingBox();
                        if (wordBox != null) {
                            float wordCenterX = wordBox.exactCenterX();
                            float wordCenterY = wordBox.exactCenterY();
                            float dx = wordCenterX - targetX;
                            float dy = wordCenterY - targetY;
                            float distance = (dx * dx) + (dy * dy);
                            if (distance < closestDistance) {
                                closestDistance = distance;
                                bestWord = smartWord;
                                bestBox = wordBox;
                            }
                        }
                    }
                }
            }
        }

        final String finalWord = bestWord;
        final Rect finalBox = bestBox;

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;

            if (!finalWord.isEmpty() && finalBox != null) {
                currentlyHighlightedWord = finalWord;
                highlightBox.setTranslationX(finalBox.left);
                highlightBox.setTranslationY(finalBox.top);

                ViewGroup.LayoutParams params = highlightBox.getLayoutParams();
                params.width = finalBox.width();
                params.height = finalBox.height();
                highlightBox.setLayoutParams(params);
                highlightBox.setVisibility(View.VISIBLE);
            } else {
                currentlyHighlightedWord = "";
                highlightBox.setVisibility(View.GONE);
            }
        });
    }

    private void startRealTimeScanning() {
        isScanningPaused = false;
        realTimeHandler.removeCallbacks(realTimeRunnable);
        realTimeHandler.post(realTimeRunnable);
    }

    private void resumeRealTimeScanning() {
        ivResultPicture.setVisibility(View.GONE);
        highlightBox.setVisibility(View.GONE);
        currentlyHighlightedWord = "";
        isScanningPaused = false;
        manualFocusPoint = null;
        realTimeHandler.removeCallbacks(realTimeRunnable);
        realTimeHandler.post(realTimeRunnable);
    }

    private void saveToAlmanac(String word, Bitmap bitmap) {
        String fileName = "word_" + word + "_" + System.currentTimeMillis() + ".jpg";
        java.io.File file = new java.io.File(getExternalFilesDir(null), fileName);

        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);

            String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");
            WordEntry newEntry = new WordEntry(word, player, file.getAbsolutePath());
            AppDatabase.getInstance(this).wordDao().insert(newEntry);

            DICTIONARY.add(word);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                Toast.makeText(this, word + " saved to Almanac!", Toast.LENGTH_SHORT).show();
                pendingWord = "";

                android.content.Intent intent = new android.content.Intent(PlayActivity.this, WordDetailActivity.class);
                intent.putExtra("WORD_TEXT", word);
                intent.putExtra("IMAGE_PATH", file.getAbsolutePath());
                intent.putExtra("SOURCE_PAGE", "SCANNER");
                intent.putExtra("IS_NEW_WORD", true); // 🌟 ALLOWS DELETING JUST THIS ONCE
                startActivity(intent);
            });
        } catch (java.io.IOException e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) resumeRealTimeScanning();
            });
        }
    }

    private String findClosestWord(String scannedWord) {
        if (scannedWord == null || scannedWord.isEmpty()) return "";
        if (DICTIONARY.contains(scannedWord)) return scannedWord;
        String bestMatch = scannedWord;
        int lowestDistance = 999;
        int maxAllowedDifferences = (scannedWord.length() <= 7) ? 1 : 2;

        for (String dictionaryWord : DICTIONARY) {
            int distance = calculateEditDistance(scannedWord, dictionaryWord);
            if (distance < lowestDistance && distance <= maxAllowedDifferences) {
                lowestDistance = distance;
                bestMatch = dictionaryWord;
            }
        }
        return bestMatch;
    }

    // 🚀 FIX 3: Replaced the heavy memory-hogging 2D array with a space-optimized 1D array
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

    @Override
    protected void onResume() {
        super.onResume();
        if (camera != null) {
            resumeRealTimeScanning();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isScanningPaused = true;
        realTimeHandler.removeCallbacks(realTimeRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (newWordDialog != null && newWordDialog.isShowing()) {
            newWordDialog.dismiss();
        }

        realTimeHandler.removeCallbacks(realTimeRunnable);
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}