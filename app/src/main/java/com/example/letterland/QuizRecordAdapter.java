package com.example.letterland;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuizRecordAdapter extends RecyclerView.Adapter<QuizRecordAdapter.ViewHolder> {

    private final List<QuizRecord> records;

    public QuizRecordAdapter(List<QuizRecord> records) {
        this.records = records;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quiz_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuizRecord record = records.get(position);

        // Uses playerName from the QuizRecord database entity
        holder.tvStudentName.setText("Student: " + record.playerName);
        holder.tvScore.setText("Score: " + record.score + "/" + record.totalItems);

        // Color code the score: Green if passed, Orange if okay, Red if failed
        float percentage = (float) record.score / record.totalItems;
        if (percentage >= 0.7f) {
            holder.tvScore.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        } else if (percentage >= 0.4f) {
            holder.tvScore.setTextColor(android.graphics.Color.parseColor("#FF9800"));
        } else {
            holder.tvScore.setTextColor(android.graphics.Color.parseColor("#F44336"));
        }

        // Convert the raw millisecond time into a readable format
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
        holder.tvTimestamp.setText(sdf.format(new Date(record.timestamp)));

        // 🚀 THE FIX: Make the item clickable to view the past answers!
        holder.itemView.setOnClickListener(v -> {
            SoundManager.getInstance(v.getContext()).playClick();
            Intent intent = new Intent(v.getContext(), QuizResultActivity.class);

            if (record.correctAnswers != null && record.userAnswers != null) {
                intent.putStringArrayListExtra("CORRECT_ANSWERS", new ArrayList<>(record.correctAnswers));
                intent.putStringArrayListExtra("USER_ANSWERS", new ArrayList<>(record.userAnswers));
            }
            intent.putExtra("IS_HISTORY", true);

            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvScore, tvTimestamp;

        public ViewHolder(View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvScore = itemView.findViewById(R.id.tvScore);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
    }
}