package com.example.letterland;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class QuizRecordActivity extends AppCompatActivity {

    private RecyclerView rvQuizRecords;
    private TextView tvNoRecords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_record);

        rvQuizRecords = findViewById(R.id.rvQuizRecords);
        tvNoRecords = findViewById(R.id.tvNoRecords);

        // Back Button
        findViewById(R.id.btnRecordBack).setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            finish();
        });

        rvQuizRecords.setLayoutManager(new LinearLayoutManager(this));

        // Load the scores!
        loadRecords();
    }

    private void loadRecords() {
        // Grab the records from the Room database we set up earlier
        List<QuizRecord> records = AppDatabase.getInstance(this).quizRecordDao().getAllRecords();

        if (records.isEmpty()) {
            tvNoRecords.setVisibility(View.VISIBLE);
            rvQuizRecords.setVisibility(View.GONE);
        } else {
            tvNoRecords.setVisibility(View.GONE);
            rvQuizRecords.setVisibility(View.VISIBLE);
            rvQuizRecords.setAdapter(new QuizRecordAdapter(records));
        }
    }
}