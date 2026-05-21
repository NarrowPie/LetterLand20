package com.example.letterland;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class AddObjectActivity extends AppCompatActivity {

    private EditText etNewWord;
    private ImageView ivSelectedImage;
    private Bitmap selectedBitmap = null;

    // 📸 Handles Taking a Picture
    private final ActivityResultLauncher<Void> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) {
                    selectedBitmap = bitmap;
                    ivSelectedImage.setImageBitmap(bitmap);
                } else {
                    Toast.makeText(this, "No picture taken", Toast.LENGTH_SHORT).show();
                }
            }
    );

    // 🖼️ Handles Picking from Gallery
    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                        selectedBitmap = bitmap;
                        ivSelectedImage.setImageBitmap(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_object);

        etNewWord = findViewById(R.id.etNewWord);
        ivSelectedImage = findViewById(R.id.ivSelectedImage);

        MaterialButton btnCamera = findViewById(R.id.btnCamera);
        MaterialButton btnGallery = findViewById(R.id.btnGallery);
        MaterialButton btnSave = findViewById(R.id.btnSaveObject);

        // 🌟 FIXED LAYOUT ID: Matches your layout XML button element symbol 'btnAddObjectBack' perfectly
        MaterialButton btnBack = findViewById(R.id.btnAddObjectBack);

        btnBack.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            finish();
        });

        btnCamera.setOnClickListener(v -> {
            SoundManager.getInstance(this).playShutter();
            takePictureLauncher.launch(null);
        });

        btnGallery.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            pickImageLauncher.launch("image/*");
        });

        btnSave.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            saveObjectToDatabase();
        });
    }

    // 🚀 NEW & IMPROVED: Safely scale down images AND fix black background issues!
    private Bitmap getResizedAndFixedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(image, width, height, true);
        Bitmap finalBitmap = Bitmap.createBitmap(scaledBitmap.getWidth(), scaledBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalBitmap);
        canvas.drawColor(Color.WHITE); // Force white background
        canvas.drawBitmap(scaledBitmap, 0, 0, null);
        return finalBitmap;
    }

    private void saveObjectToDatabase() {
        String word = etNewWord.getText().toString().trim().toUpperCase();
        if (word.isEmpty()) {
            Toast.makeText(this, "Please type a word name!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedBitmap == null) {
            Toast.makeText(this, "Please add an image!", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            String player = getSharedPreferences("LetterLandMemory", MODE_PRIVATE).getString("ACTIVE_PROFILE", "Default");

            WordEntry existingWord = AppDatabase.getInstance(this).wordDao().findWordForProfile(word, player);
            WordEntry existingAdminWord = AppDatabase.getInstance(this).wordDao().findWordForProfile(word, "ADMIN|" + player);
            if (existingWord != null || existingAdminWord != null) {
                runOnUiThread(() -> Toast.makeText(this, "This word already exists in the Almanac!", Toast.LENGTH_SHORT).show());
                return;
            }

            String fileName = "word_" + word + "_" + System.currentTimeMillis() + ".jpg";
            File file = new File(getExternalFilesDir(null), fileName);

            try (FileOutputStream out = new FileOutputStream(file)) {
                Bitmap fixedBitmap = getResizedAndFixedBitmap(selectedBitmap, 800);
                fixedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);

                // 🌟 Tag the profile string with ADMIN| prefix so the log row handles attribution cleanly
                String adminProfileTag = "ADMIN|" + player;
                WordEntry newEntry = new WordEntry(word, adminProfileTag, file.getAbsolutePath());
                AppDatabase.getInstance(this).wordDao().insert(newEntry);

                runOnUiThread(() -> {
                    Toast.makeText(this, word + " successfully added to Almanac!", Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to save image.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}