package it.example.passolibero;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

/** Android JobScheduler: una verifica oraria, con rete, solo quando abilitata. */
public final class WeatherJob extends JobService {
    private volatile boolean active;
    @Override public boolean onStartJob(JobParameters params) {
        active = true;
        new Thread(() -> {
            try {
                if (active) WeatherMonitor.checkAndNotify(getApplicationContext());
            } catch (Exception ex) {
                Log.w("PassoLiberoWeather", "Controllo meteo rimandato", ex);
            } finally {
                if (active) jobFinished(params, false);
            }
        }, "WeatherForecastCheck").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        active = false;
        return true;
    }
}
