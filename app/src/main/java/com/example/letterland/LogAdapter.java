package com.example.letterland;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private Context context;
    private List<WordEntry> wordList;

    public LogAdapter(Context context, List<WordEntry> wordList) {
        this.context = context;
        this.wordList = wordList;
    }

    public void updateData(List<WordEntry> newList) {
        this.wordList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        WordEntry wordEntry = wordList.get(position);
        holder.tvLogWord.setText(wordEntry.word);

        if (wordEntry.imagePath != null && !wordEntry.imagePath.isEmpty()) {
            holder.ivLogImage.setImageURI(Uri.parse(wordEntry.imagePath));
        } else {
            holder.ivLogImage.setImageResource(R.drawable.admin_pic);
        }

        String dateTimeString = "Unknown Date";
        if (wordEntry.imagePath != null) {
            try {
                File file = new File(Uri.parse(wordEntry.imagePath).getPath());
                if (file.exists()) {
                    long lastMod = file.lastModified();
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.US);
                    dateTimeString = sdf.format(new Date(lastMod));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Output configuration matching your requested format rules perfectly:
        String details;
        String rawProfile = wordEntry.profileName != null ? wordEntry.profileName : "Default";

        if (rawProfile.startsWith("ADMIN|")) {
            //Extract the actual player name by stripping out the prefix tag string
            String actualPlayerName = rawProfile.replace("ADMIN|", "");

            // UPDATED FORMAT: Displays "Added by: Admin to (PlayerName)" cleanly to eliminate any confusion
            details = "Added by: Admin to " + actualPlayerName + "\nDate: " + dateTimeString;
        } else {
            // Ordinary player collection (Scan / Write gameplay modes)
            details = "Profile: " + rawProfile + "\nDate: " + dateTimeString;
        }

        holder.tvLogDetails.setText(details);
    }

    @Override
    public int getItemCount() {
        return wordList != null ? wordList.size() : 0;
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        ImageView ivLogImage;
        TextView tvLogWord, tvLogDetails;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            ivLogImage = itemView.findViewById(R.id.ivLogImage);
            tvLogWord = itemView.findViewById(R.id.tvLogWord);
            tvLogDetails = itemView.findViewById(R.id.tvLogDetails);
        }
    }
}