package com.example.letterland;

import androidx.room.TypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Converters {
    @TypeConverter
    public static String fromList(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) sb.append("|||");
        }
        return sb.toString();
    }

    @TypeConverter
    public static List<String> toList(String data) {
        if (data == null || data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split("\\|\\|\\|")));
    }
}