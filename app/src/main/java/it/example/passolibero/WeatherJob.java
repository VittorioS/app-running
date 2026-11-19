package it.example.passolibero;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import it.example.passolibero.application.weather.WeatherCheckCancellation;

/** Android JobScheduler: una verifica oraria, con rete, solo quando abilitata. */
public final class WeatherJob extends JobService {
    private final Handler main = new Handler(Looper.getMainLooper());
    // JobScheduler lifecycle callbacks and completion both use the main thread.
    private Run current;

    private record Run(JobParameters params, WeatherCheckCancellation cancellation) {}

    @Override public boolean onStartJob(JobParameters params) {
        if (current != null) current.cancellation().cancel();
        Run run = new Run(params, new WeatherCheckCancellation());
        current = run;
        new Thread(() -> {
            try {
                WeatherMonitor.checkAndNotify(getApplicationContext(), run.cancellation(),
                        WeatherApi::forecast);
            } catch (Exception ex) {
                Log.w("PassoLiberoWeather", "Controllo meteo rimandato", ex);
            } finally {
                main.post(() -> {
                    if (current != run) return;
                    current = null;
                    jobFinished(run.params(), false);
                });
            }
        }, "WeatherForecastCheck").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        if (current != null && current.params().getJobId() == params.getJobId()) {
            current.cancellation().cancel();
            current = null;
        }
        return true;
    }
}
