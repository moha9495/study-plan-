package com.muhannad.studyplan;

import android.content.Context;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Saves the plan (same JSON the web UI uses) where both the app and the widget can read it. */
final class Store {
    private static final String PREFS = "plan";
    private static final String KEY = "data";

    static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private Store() {}

    static String load(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
    }

    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).commit();
    }

    static LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Same rule as the web UI: week 1 starts on the start date; before it, 0. */
    static int currentWeek(String start, LocalDate today) {
        LocalDate s = parseDate(start);
        if (s == null) return 0;
        long d = ChronoUnit.DAYS.between(s, today);
        return d >= 0 ? (int) (d / 7) + 1 : 0;
    }

    static String headerText(Context c) {
        LocalDate today = LocalDate.now();
        String start = "";
        try {
            String raw = load(c);
            if (!raw.isEmpty()) start = new JSONObject(raw).optString("start", "");
        } catch (Exception ignored) {
        }
        LocalDate s = parseDate(start);
        if (s == null) return today.format(LONG);
        int cw = currentWeek(start, today);
        if (cw <= 0) return "Starts " + s.format(SHORT);
        LocalDate a = s.plusDays((cw - 1) * 7L);
        return "Week " + cw + " · " + a.format(SHORT) + " – " + a.plusDays(6).format(SHORT);
    }
}
