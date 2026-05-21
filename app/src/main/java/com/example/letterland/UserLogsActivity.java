package com.example.letterland;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class UserLogsActivity extends AppCompatActivity {

    private RecyclerView rvLogsList;
    private LogAdapter logAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_logs);

        MaterialButton btnBack = findViewById(R.id.btnLogsBack);
        rvLogsList = findViewById(R.id.rvUserLogsList);

        btnBack.setOnClickListener(v -> {
            SoundManager.getInstance(this).playClick();
            finish();
        });

        rvLogsList.setLayoutManager(new LinearLayoutManager(this));

        // Initialize Adapter
        logAdapter = new LogAdapter(this, new ArrayList<>());
        rvLogsList.setAdapter(logAdapter);

        loadDataFromDatabase();
    }

    private void loadDataFromDatabase() {
        AppDatabase db = AppDatabase.getInstance(this);
        new Thread(() -> {
            List<WordEntry> collectedWords = db.wordDao().getAllWords();
            runOnUiThread(() -> {
                logAdapter.updateData(collectedWords);
            });
        }).start();
    }
}