package com.muhannad.studyplan;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class PlanWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    /** Same colours, in the same order, as the web UI so subjects match. */
    private static final int[] PALETTE = {
            0xFF2F6FED, 0xFFE0563A, 0xFF2E9E5B, 0xFF9B51E0,
            0xFFD98A1A, 0xFF12A3B4, 0xFFD6457E, 0xFF6B7D2A
    };
    private static final Set<String> HOT =
            new HashSet<>(Arrays.asList("assignment", "quiz", "test", "project"));

    private static final class Row {
        boolean header;
        String title;
        String sub;
        int color;
        boolean urgent;
        String view;
    }

    private static final class Item {
        String title, subject, type;
        int week;
        LocalDate date;
        long days;
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private final List<Row> rows = new ArrayList<>();

        Factory(Context context) {
            this.context = context;
        }

        @Override public void onCreate() {}
        @Override public void onDestroy() { rows.clear(); }

        @Override
        public void onDataSetChanged() {
            rows.clear();
            try {
                build();
            } catch (Exception ignored) {
                rows.clear();
            }
        }

        private void build() throws Exception {
            String raw = Store.load(context);
            if (raw.isEmpty()) return;
            JSONObject root = new JSONObject(raw);
            JSONArray arr = root.optJSONArray("items");
            if (arr == null) return;

            LocalDate today = LocalDate.now();
            int cw = Store.currentWeek(root.optString("start", ""), today);

            TreeSet<String> subjectSet = new TreeSet<>();
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.optJSONObject(k);
                if (o != null) subjectSet.add(o.optString("subject", ""));
            }
            List<String> subjects = new ArrayList<>(subjectSet);

            List<Item> overdue = new ArrayList<>();
            List<Item> upcoming = new ArrayList<>();
            List<Item> thisWeek = new ArrayList<>();

            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.optJSONObject(k);
                if (o == null || o.optBoolean("done", false)) continue;
                Item it = new Item();
                it.title = o.optString("title", "");
                it.subject = o.optString("subject", "");
                it.type = o.optString("type", "other");
                it.week = o.optInt("week", 0);
                it.date = Store.parseDate(o.optString("date", ""));
                if (it.date != null) {
                    it.days = ChronoUnit.DAYS.between(today, it.date);
                    if (it.days < 0) overdue.add(it);
                    else if (it.days <= 14) upcoming.add(it);
                } else if (cw > 0 && it.week == cw) {
                    thisWeek.add(it);
                }
            }

            overdue.sort((a, b) -> a.date.compareTo(b.date) != 0 ? a.date.compareTo(b.date) : a.title.compareTo(b.title));
            upcoming.sort((a, b) -> a.date.compareTo(b.date) != 0 ? a.date.compareTo(b.date) : a.title.compareTo(b.title));
            thisWeek.sort((a, b) -> {
                int h = Boolean.compare(!HOT.contains(a.type), !HOT.contains(b.type));
                return h != 0 ? h : a.title.compareTo(b.title);
            });

            addSection("Overdue", overdue, subjects, "dates");
            addSection("Next 14 days", upcoming, subjects, "dates");
            addSection("This week · Week " + cw, thisWeek, subjects, "weeks");
        }

        private void addSection(String title, List<Item> items, List<String> subjects, String view) {
            if (items.isEmpty()) return;
            Row h = new Row();
            h.header = true;
            h.title = title;
            h.urgent = title.equals("Overdue");
            h.view = view;
            rows.add(h);
            for (Item it : items) {
                Row r = new Row();
                r.title = it.title;
                r.view = view;
                int idx = Math.max(0, subjects.indexOf(it.subject));
                r.color = PALETTE[idx % PALETTE.length];
                String when;
                if (it.date == null) {
                    when = HOT.contains(it.type) ? it.type + " · no date yet" : it.type;
                } else if (it.days < 0) {
                    when = it.date.format(Store.LONG) + " · " + (-it.days) + "d overdue";
                    r.urgent = true;
                } else if (it.days == 0) {
                    when = "Today";
                    r.urgent = true;
                } else if (it.days == 1) {
                    when = "Tomorrow";
                    r.urgent = true;
                } else {
                    when = it.date.format(Store.LONG) + " · in " + it.days + "d";
                }
                r.sub = it.subject + " · " + when;
                rows.add(r);
            }
        }

        @Override public int getCount() { return rows.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= rows.size()) {
                return new RemoteViews(context.getPackageName(), R.layout.widget_header);
            }
            Row r = rows.get(position);
            Intent fill = new Intent();
            fill.putExtra("view", r.view);
            if (r.header) {
                RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_header);
                v.setTextViewText(R.id.header_text, r.title.toUpperCase());
                v.setTextColor(R.id.header_text, r.urgent ? 0xFFFF8A80 : 0xFFAFC3E0);
                v.setOnClickFillInIntent(R.id.header_text, fill);
                return v;
            }
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_item);
            v.setTextViewText(R.id.item_title, r.title);
            v.setTextViewText(R.id.item_sub, r.sub);
            v.setTextColor(R.id.item_sub, r.urgent ? 0xFFFFB74D : 0xFFAFC3E0);
            v.setInt(R.id.item_bar, "setBackgroundColor", r.color);
            v.setOnClickFillInIntent(R.id.row, fill);
            return v;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }
    }
}
