package com.example.letterland;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;

public class QuizResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_result);

        TextView tvEmoji = findViewById(R.id.tvEmoji);
        TextView tvFinalScore = findViewById(R.id.tvFinalScore);
        RecyclerView rvResults = findViewById(R.id.rvResults);
        View btnTryAgain = findViewById(R.id.btnTryAgain);
        View btnMainMenu = findViewById(R.id.btnMainMenu);

        btnMainMenu.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            finish();
        });

        if (btnTryAgain != null) {
            btnTryAgain.setOnClickListener(v -> {
                SoundManager.getInstance(this).playClick();
                startActivity(new Intent(this, QuizActivity.class));
                finish();
            });
        }

        ArrayList<String> correctAnswers = getIntent().getStringArrayListExtra("CORRECT_ANSWERS");
        ArrayList<String> userAnswers = getIntent().getStringArrayListExtra("USER_ANSWERS");
        boolean isHistory = getIntent().getBooleanExtra("IS_HISTORY", false);

        // Hide "Play Again" if viewing from Admin logs
        if (isHistory && btnTryAgain != null) {
            btnTryAgain.setVisibility(View.GONE);
        }

        if (correctAnswers == null || userAnswers == null) return;

        // Calculate Score
        int score = 0;
        for (int i = 0; i < correctAnswers.size(); i++) {
            if (correctAnswers.get(i).equalsIgnoreCase(userAnswers.get(i))) {
                score++;
            }
        }

        // Update UI based on performance
        tvFinalScore.setText(score + "/" + correctAnswers.size());
        float percentage = (float) score / correctAnswers.size();

        if (percentage >= 0.7f) {
            tvEmoji.setText("😊");
            tvFinalScore.setTextColor(Color.parseColor("#4CAF50"));
        } else if (percentage >= 0.4f) {
            tvEmoji.setText("😐");
            tvFinalScore.setTextColor(Color.parseColor("#FF9800"));
        } else {
            tvEmoji.setText("☹️");
            tvFinalScore.setTextColor(Color.parseColor("#F44336"));
        }

        // Setup RecyclerView
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        rvResults.setAdapter(new ResultAdapter(correctAnswers, userAnswers));
    }

    // --- Inner Adapter Class ---
    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ResultViewHolder> {
        private final ArrayList<String> correct;
        private final ArrayList<String> user;

        public ResultAdapter(ArrayList<String> correct, ArrayList<String> user) {
            this.correct = correct;
            this.user = user;
        }

        @NonNull
        @Override
        public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quiz_result, parent, false);
            return new ResultViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
            String correctWord = correct.get(position);
            String userWord = user.get(position);

            holder.tvCorrectWord.setText(correctWord);
            holder.tvUserAnswer.setText(userWord);

            if (correctWord.equalsIgnoreCase(userWord)) {
                holder.cardItem.setCardBackgroundColor(Color.parseColor("#4CAF50")); // Green for correct
            } else {
                holder.cardItem.setCardBackgroundColor(Color.parseColor("#EF5350")); // Red for wrong
            }
        }

        @Override
        public int getItemCount() {
            return correct.size();
        }

        // --- Inner ViewHolder Class (This fixes the red errors) ---
        class ResultViewHolder extends RecyclerView.ViewHolder {
            TextView tvCorrectWord, tvUserAnswer;
            MaterialCardView cardItem;

            public ResultViewHolder(@NonNull View itemView) {
                super(itemView);
                tvCorrectWord = itemView.findViewById(R.id.tvCorrectWord);
                tvUserAnswer = itemView.findViewById(R.id.tvUserAnswer);
                cardItem = itemView.findViewById(R.id.cardResultItem);
            }
        }
    }
}