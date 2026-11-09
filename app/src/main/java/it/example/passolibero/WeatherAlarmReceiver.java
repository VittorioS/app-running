package it.example.passolibero;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dopo un allarme, controlla tramite JobScheduler e pianifica il prossimo orario. */
public final class WeatherAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if ("it.example.passolibero.CHECK_WEATHER".equals(intent.getAction())) {
            WeatherMonitor.updateSchedule(context);
            WeatherMonitor.scheduleNetworkCheck(context);
        }
    }
}
