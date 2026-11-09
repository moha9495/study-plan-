package com.muhannad.studyplan;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

/** Home-screen widget: overdue items, the next 14 days, and this week's undated items. */
public class PlanWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
        manager.notifyAppWidgetViewDataChanged(ids, R.id.list);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PlanWidget.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) update(context, manager, id);
        manager.notifyAppWidgetViewDataChanged(ids, R.id.list);
    }

    @SuppressWarnings("deprecation")
    static void update(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);

        Intent service = new Intent(context, PlanWidgetService.class);
        service.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        service.setData(Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.list, service);
        views.setEmptyView(R.id.list, R.id.empty);

        views.setTextViewText(R.id.week, Store.headerText(context));
        views.setOnClickPendingIntent(R.id.header, open(context, "weeks", 10));
        views.setOnClickPendingIntent(R.id.empty, open(context, "dates", 11));

        Intent template = new Intent(context, MainActivity.class);
        template.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        views.setPendingIntentTemplate(R.id.list, PendingIntent.getActivity(context, 12, template, flags));

        manager.updateAppWidget(id, views);
    }

    private static PendingIntent open(Context context, String view, int requestCode) {
        Intent i = new Intent(context, MainActivity.class);
        i.putExtra("view", view);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
