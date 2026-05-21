package com.example.letterland;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class EditAlmanacActivity extends AppCompatActivity {

    private RecyclerView rvEditAlmanac;
    private EditWordAdapter adapter;

    // TRACK FILTER STATE
    private boolean isShowingStarredOnly = false;

    // NEW CONTROLS
    private CheckBox cbSelectAll;
    private View btnDeleteSelected;

    @Override
    protected void onResume() {
        super.onResume();
        loadWordsFromDatabase();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Points to the dedicated Admin layout
        setContentView(R.layout.activity_edit_almanac);

        rvEditAlmanac = findViewById(R.id.rvAlmanac);
        View btnBack = findViewById(R.id.btnBackAlmanac);
        ImageButton btnFilterStarred = findViewById(R.id.btnFilterStarred);

        cbSelectAll = findViewById(R.id.cbSelectAll);
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected);

        btnBack.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            finish();
        });

        btnFilterStarred.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            isShowingStarredOnly = !isShowingStarredOnly;
            if (isShowingStarredOnly) {
                btnFilterStarred.setImageResource(android.R.drawable.btn_star_big_on);
                Toast.makeText(this, "Showing Quiz Words Only", Toast.LENGTH_SHORT).show();
            } else {
                btnFilterStarred.setImageResource(android.R.drawable.btn_star_big_off);
                Toast.makeText(this, "Showing All Words", Toast.LENGTH_SHORT).show();
            }
            loadWordsFromDatabase();
        });

        rvEditAlmanac.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new EditWordAdapter(new ArrayList<>());
        rvEditAlmanac.setAdapter(adapter);

        // SELECT ALL CHECKBOX LISTENER
        cbSelectAll.setOnClickListener(v -> {
            if (adapter != null) {
                adapter.selectAll(cbSelectAll.isChecked());
            }
        });

        // DELETE BUTTON LISTENER WITH CUSTOM DIALOG
        btnDeleteSelected.setOnClickListener(v -> {
            if (adapter != null) {
                List<WordEntry> itemsToDelete = adapter.getSelectedWords();
                if (itemsToDelete.isEmpty()) {
                    Toast.makeText(this, "No items selected!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // SHOW THE CUSTOM CONFIRMATION DIALOG BEFORE DELETING!
                showDeleteConfirmationDialog(() -> {

                    // --- This code ONLY runs if they click "Yes, Delete!" ---
                    new Thread(() -> {
                        AppDatabase db = AppDatabase.getInstance(this);
                        for (WordEntry word : itemsToDelete) {

                            // 1. Delete from Database
                            db.wordDao().delete(word);

                            // 🚀 2. THE FIX: Log with exact format: Word | Image | Collector | Deleter
                            // 🚀 THE FIX: Log with exact 3-part format: Word | Image | Collector
                            String logDetails = word.word + "|" + word.imagePath + "|" + word.profileName;
                            db.logDao().insertLog(new LogEntry("DELETED WORD", logDetails, System.currentTimeMillis()));

                            // 🚀 3. THE FIX: DO NOT delete the physical file!
                            // Keep it on the phone so the Deleted Logs screen can show it and restore it!
                            /*
                            if (word.imagePath != null) {
                                try {
                                    java.io.File file = new java.io.File(android.net.Uri.parse(word.imagePath).getPath());
                                    if (file.exists()) {
                                        file.delete();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            */
                        }

                        runOnUiThread(() -> {
                            Toast.makeText(this, itemsToDelete.size() + " item(s) deleted!", Toast.LENGTH_SHORT).show();
                            cbSelectAll.setChecked(false);
                            loadWordsFromDatabase(); // Refresh the list
                        });
                    }).start();
                    // --------------------------------------------------------

                });
            }
        });
    }

    // --- CUSTOM DIALOG METHOD ---
    private void showDeleteConfirmationDialog(Runnable onConfirm) {
        // 1. Create the custom dialog
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_delete);

        // 2. Make the background transparent so your rounded MaterialCardView corners show perfectly!
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // 3. Link the buttons from your XML
        MaterialButton btnConfirmDelete = dialog.findViewById(R.id.btnConfirmDelete);
        MaterialButton btnCancelDelete = dialog.findViewById(R.id.btnCancelDelete);

        // 4. Handle "Cancel"
        btnCancelDelete.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            dialog.dismiss(); // Just close the popup
        });

        // 5. Handle "Yes, Delete!"
        btnConfirmDelete.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            dialog.dismiss(); // Close the popup

            // Run the actual deletion code that was passed into this method
            if (onConfirm != null) {
                onConfirm.run();
            }
        });

        // 6. Show it to the user
        dialog.show();
    }

    private void loadWordsFromDatabase() {
        // GRAB THE ACTIVE PROFILE FIRST
        SharedPreferences prefs = getSharedPreferences("LetterLandMemory", MODE_PRIVATE);
        String activeProfile = prefs.getString("ACTIVE_PROFILE", "");

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<WordEntry> userWords;

            // FILTER BY ACTIVE PROFILE
            if (activeProfile.isEmpty()) {
                userWords = db.wordDao().getAllWords(); // Safety fallback
            } else {
                userWords = db.wordDao().getAllWordsForProfile(activeProfile); // ONLY the active kid's words
            }

            List<WordEntry> displayWords = new ArrayList<>();

            // Filter them in Java for the Starred/Quiz toggle
            if (isShowingStarredOnly) {
                for (WordEntry word : userWords) {
                    if (word.isStarred) {
                        displayWords.add(word);
                    }
                }
            } else {
                displayWords.addAll(userWords);
            }

            runOnUiThread(() -> {
                adapter.updateWords(displayWords);
            });
        }).start();
    }

    // --- INNER ADAPTER CLASS ---
    class EditWordAdapter extends RecyclerView.Adapter<EditWordAdapter.WordViewHolder> {
        private List<WordEntry> words;

        public EditWordAdapter(List<WordEntry> words) {
            this.words = words;
        }

        public void updateWords(List<WordEntry> newWords) {
            this.words = newWords;
            updateSelectAllCheckboxState();
            notifyDataSetChanged();
        }

        public void selectAll(boolean isSelected) {
            for (WordEntry word : words) {
                word.isSelected = isSelected;
            }
            notifyDataSetChanged();
        }

        public List<WordEntry> getSelectedWords() {
            List<WordEntry> selected = new ArrayList<>();
            for (WordEntry word : words) {
                if (word.isSelected) {
                    selected.add(word);
                }
            }
            return selected;
        }

        private void updateSelectAllCheckboxState() {
            if (cbSelectAll != null) {
                boolean allSelected = !words.isEmpty();
                for (WordEntry w : words) {
                    if (!w.isSelected) {
                        allSelected = false;
                        break;
                    }
                }
                cbSelectAll.setChecked(allSelected);
            }
        }

        @NonNull
        @Override
        public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_edit_word, parent, false);
            return new WordViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
            WordEntry currentWord = words.get(position);
            holder.tvWord.setText(currentWord.word);

            if (currentWord.imagePath != null && !currentWord.imagePath.isEmpty()) {
                holder.ivImage.setImageURI(Uri.parse(currentWord.imagePath));
            } else {
                holder.ivImage.setImageResource(R.drawable.admin_pic);
            }

            if (currentWord.isStarred) {
                holder.ivStar.setImageResource(android.R.drawable.btn_star_big_on);
            } else {
                holder.ivStar.setImageResource(android.R.drawable.btn_star_big_off);
            }

            holder.ivStar.setOnClickListener(v -> {
                SoundManager.getInstance(v.getContext()).playClick();
                boolean newState = !currentWord.isStarred;
                currentWord.isStarred = newState;

                new Thread(() -> {
                    AppDatabase.getInstance(v.getContext()).wordDao().update(currentWord);
                    runOnUiThread(() -> {
                        notifyItemChanged(position);
                        if (newState) {
                            Toast.makeText(v.getContext(), currentWord.word + " added to Quiz!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(v.getContext(), currentWord.word + " removed from Quiz.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            });

            holder.itemView.setOnClickListener(v -> {
                SoundManager.getInstance(v.getContext()).playClick();
                android.content.Intent intent = new android.content.Intent(v.getContext(), WordDetailActivity.class);
                intent.putExtra("WORD_TEXT", currentWord.word);
                intent.putExtra("IMAGE_PATH", currentWord.imagePath);
                intent.putExtra("SOURCE_PAGE", "EDIT_ALMANAC");
                v.getContext().startActivity(intent);
            });

            // Checkbox Setup
            holder.cbSelectEditWord.setOnCheckedChangeListener(null);
            holder.cbSelectEditWord.setChecked(currentWord.isSelected);

            holder.cbSelectEditWord.setOnCheckedChangeListener((buttonView, isChecked) -> {
                currentWord.isSelected = isChecked;
                updateSelectAllCheckboxState();
            });
        }

        @Override
        public int getItemCount() {
            return words.size();
        }

        class WordViewHolder extends RecyclerView.ViewHolder {
            ImageView ivImage;
            ImageView ivStar;
            TextView tvWord;
            CheckBox cbSelectEditWord;

            public WordViewHolder(@NonNull View itemView) {
                super(itemView);
                ivImage = itemView.findViewById(R.id.ivGalleryImage);
                tvWord = itemView.findViewById(R.id.tvGalleryWord);
                ivStar = itemView.findViewById(R.id.ivStar);
                cbSelectEditWord = itemView.findViewById(R.id.cbSelectEditWord);
            }
        }
    }
}