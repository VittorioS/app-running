import it.example.passolibero.AdaptiveProfile;
import it.example.passolibero.BatteryPolicy;
import it.example.passolibero.GpsQualityPolicy;
import it.example.passolibero.DistanceAccumulator;
import it.example.passolibero.MovementClassifier;
import it.example.passolibero.MotionEvidencePolicy;
import it.example.passolibero.MotionTimeline;
import it.example.passolibero.InertialGaitDetector;
import it.example.passolibero.RunMath;
import it.example.passolibero.RunStats;
import it.example.passolibero.RouteSegmenter;

public final class CoreAlgorithmTest {
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(Math.abs(RunMath.distanceMeters(0,0,0,0.001) - 111.19) < 0.5, "Haversine");
        check(RunMath.pace(300_000, 1000).equals("5:00"), "Pace 5:00 /km");
        check(RunMath.duration(3_670_000).equals("1:01:10"), "Duration");
        AdaptiveProfile profile = new AdaptiveProfile();
        check(profile.threshold(6, true) == 6, "Cold start");
        for (int i=0; i<40; i++) { profile.observe(false, 5.0); profile.observe(true, 7.5); }
        check(Math.abs(profile.threshold(6, true)-6.25) < 0.05, "Personal threshold");
        check(profile.threshold(6, false) == 6, "Manual override");

        MovementClassifier model = new MovementClassifier();
        for (int t = 0; t < 30; t++) model.update(t*1000L, 8.0, true, 12, 0, 6);
        check(model.current() == MovementClassifier.Kind.RUN, "RUN after confirmation");
        for (int t = 30; t < 60; t++) model.update(t*1000L, 5.0, true, 12, 0, 6);
        check(model.current() == MovementClassifier.Kind.WALK, "RUN->WALK without session stop");
        for (int t = 60; t < 65; t++) model.update(t*1000L, Double.NaN, false, 0, 100_000, 6);
        check(model.current() == MovementClassifier.Kind.WALK, "GPS gap does not invent motion");
        // Un cambio reale di velocita' deve essere visibile gia' al prossimo fix.
        MovementClassifier responsive = new MovementClassifier();
        for (int t = 0; t <= 4000; t += 1000)
            responsive.update(t, 10.0, true, 12, 0, 6.0);
        check(responsive.current() == MovementClassifier.Kind.RUN, "Riconoscimento corsa senza 10 s di attesa");
        responsive.update(5000, 4.0, true, 12, 0, 6.0);
        check(responsive.smoothedSpeedKmh() < 5.5,
                "10 -> 4 km/h visibile al primo fix (ottenuto: " + responsive.smoothedSpeedKmh() + ")");
        check(responsive.current() == MovementClassifier.Kind.RUN,
                "Cambio categoria non immediato al singolo campione");
        responsive.update(6000, 4.0, true, 12, 0, 6.0);
        responsive.update(7000, 4.0, true, 12, 0, 6.0);
        check(responsive.current() == MovementClassifier.Kind.WALK,
                "Corsa -> camminata confermata in circa 2 secondi");
        responsive.update(8000, 10.0, true, 12, 0, 6.0);
        check(responsive.smoothedSpeedKmh() > 8.0, "4 -> 10 km/h visibile al primo fix");
        responsive.update(9000, 10.0, true, 12, 0, 6.0);
        responsive.update(10000, 10.0, true, 12, 0, 6.0);
        check(responsive.current() == MovementClassifier.Kind.RUN,
                "Camminata -> corsa confermata in circa 2 secondi");
        responsive.update(11000, 4.0, true, 12, 0, 6.0);
        responsive.update(12000, 10.0, true, 12, 0, 6.0);
        check(responsive.current() == MovementClassifier.Kind.RUN,
                "Un fix anomalo non cambia la categoria");
        MovementClassifier delayedGps = new MovementClassifier();
        delayedGps.update(0, 10.0, true, 12, 0, 6.0);
        delayedGps.update(5000, 4.0, true, 12, 0, 6.0);
        check(delayedGps.smoothedSpeedKmh() < 4.1,
                "Filtro adattivo quando Android ritarda il fix GPS");

        // Simula 100 m di cammino con letture GPS ogni ~1 metro (1 Hz).
        // Il precedente filtro scartava quasi tutti questi incrementi.
        DistanceAccumulator distance = new DistanceAccumulator();
        double total = 0;
        for (int meters = 0; meters <= 100; meters++) {
            total += distance.accept(0, meters / 111_195.0, 5, true);
        }
        check(total >= 95 && total <= 101,
                "100 m a piccoli passi devono essere contati (ottenuti: " + total + ")");
        // Fermi, le oscillazioni attorno al punto non devono gonfiare i metri.
        distance.reset();
        double stopped = 0;
        for (int i = 0; i < 100; i++) {
            stopped += distance.accept(0, (i % 2) * 2 / 111_195.0, 5, false);
        }
        check(stopped == 0, "Nessun incremento GPS da fermo");
        // GNSS uncertainty is NOT proof of stillness.
        MotionEvidencePolicy motion = new MotionEvidencePolicy();
        for (int t=0; t<20; t++) {
            motion.observeGps(t*1000L, 0, (t%2==0?10:-10)/111_195.0,
                    15, 0.2, 1.5);
            check(motion.evaluate(t*1000L, 0, Long.MAX_VALUE, false,
                    false, false, false) == MotionEvidencePolicy.Evidence.UNCERTAIN,
                    "Rumoroso senza prova di moto o immobilita'");
        }
        motion.reset();
        for (int t=0; t<4; t++)
            motion.observeGps(t*1000L, 0, t*3.0/111_195.0, 8, 3.0, 0.3);
        check(motion.evaluate(3000,0,Long.MAX_VALUE,false,false,false,false)
                        == MotionEvidencePolicy.Evidence.MOVING,
                "GNSS affidabile rileva movimento");
        motion.reset();
        for (int t=0; t<4; t++)
            motion.observeGps(t*1000L, 0, 0, 5, 0.1, 0.1);
        check(motion.evaluate(3000,0,9000,false,false,false,false)
                        == MotionEvidencePolicy.Evidence.STILL_MODERATE,
                "GNSS vicino zero fornisce evidenza positiva di immobilita'");
        check(motion.evaluate(8000,0,Long.MAX_VALUE,false,false,false,false)
                        == MotionEvidencePolicy.Evidence.UNCERTAIN,
                "Fix vecchio: niente immobilita inventata");
        motion.reset();
        check(motion.evaluate(0,3,0,true,true,false,false)
                        == MotionEvidencePolicy.Evidence.MOVING,
                "Passi rilevati confermano movimento");
        InertialGaitDetector gait = new InertialGaitDetector();
        for (int i=0; i<100; i++) gait.observe(i*20L,0,0,9.81);
        check(!gait.gaitAt(2000),"Immobilita del telefono non crea falsa andatura");
        for (int i=100; i<260; i++) {
            double z=9.81+2.5*Math.sin((i-100)*20.0*2*Math.PI/650);
            gait.observe(i*20L,0,0,z);
        }
        check(gait.gaitAt(5200), "Cadenzamento inerziale riconoscibile in simulazione");

        MotionTimeline timeline = new MotionTimeline();
        timeline.advance(0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        for (int t=1;t<=5;t++) timeline.advance(t*1000L,
                MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        check(timeline.visible()==MovementClassifier.Kind.RUN,"Partenza corsa");
        long beforeShortStop=timeline.runMs();
        for (int t=6;t<=8;t++) timeline.advance(t*1000L,
                MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.RUN);
        check(timeline.visible()==MovementClassifier.Kind.RUN,"Microfermata invisibile");
        timeline.advance(9000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        check(timeline.stillMs()==0,"Microfermata NON conteggiata");
        check(timeline.runMs()>beforeShortStop,"Microfermata attribuita alla corsa");
        for (int t=10;t<=14;t++) timeline.advance(t*1000L,
                MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.RUN);
        check(timeline.visible()==MovementClassifier.Kind.STILL,"Fermata confermata");
        check(timeline.stillMs()==5000,"Fermata contabilizzata retroattivamente");
        for (int t=15;t<=18;t++) timeline.advance(t*1000L,
                MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        check(timeline.visible()==MovementClassifier.Kind.WALK,"Ripartenza camminata");
        timeline.finish();
        check(timeline.runMs()+timeline.walkMs()+timeline.stillMs()+timeline.unknownMs()
                ==18000,"Ogni secondo attribuito una sola volta");
        MotionTimeline blackout = new MotionTimeline();
        blackout.advance(0,MotionEvidencePolicy.Evidence.UNCERTAIN,MovementClassifier.Kind.UNKNOWN);
        for(int t=1;t<=9;t++) blackout.advance(t*1000L,
                MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        blackout.finish();
        check(blackout.stillMs()==0 && blackout.unknownMs()==9000,
                "Perdita sensori non viene conteggiata come sosta");
        // Dopo un buco GPS non collegare posizioni distanti.
        distance.reset();
        check(distance.accept(0, 1, 5, true) == 0, "Nuova ancora dopo buco GPS");
        check(RunStats.instantPace(12.0).equals("5:00"), "Ritmo attuale espresso in min/km");
        check(RunStats.instantPace(0).equals("--:--"), "Ritmo assente da fermo");
        check(RunStats.nextCheckpointM(875, 250) == 1000, "Prossimo checkpoint");
        check(RunStats.remainingCheckpointM(875, 250) == 125, "Metri mancanti");
        check(RunStats.nextCheckpointM(1000, 500) == 1500, "Checkpoint successivo esatto");
        check(RunStats.goalProgress(2500, 5000) == 50, "50 percento dell'obiettivo");
        check(RunStats.goalRemaining(5100, 5000) == 0, "Obiettivo raggiunto");
        check(RouteSegmenter.isGap(1_000, 0, 0, 40_000, 0, 0), "Buchi GPS non collegati");
        check(!RouteSegmenter.isGap(1_000, 0, 0, 2_000, 0, 0.00001), "Percorso continuo");
        check(BatteryPolicy.shouldPublishState(-1, 1_000, false), "Primo stato");
        check(!BatteryPolicy.shouldPublishState(1_000, 1_200, true), "Evita doppio salvataggio GPS");
        check(BatteryPolicy.shouldPublishState(1_000, 1_900, true), "Aggiornamento GPS rapido");
        check(!BatteryPolicy.shouldPublishState(1_000, 2_500, false), "Evita salvataggio timer duplicato");
        check(BatteryPolicy.shouldPublishState(1_000, 3_000, false), "Salvataggio timer se GPS assente");
        check(BatteryPolicy.shouldPublishState(50_000, 100, true), "Gestisce riavvio uptime");
        check(GpsQualityPolicy.isReliable(12f, 500), "GPS affidabile");
        check(!GpsQualityPolicy.isReliable(26f, 500), "GPS impreciso");
        check(!GpsQualityPolicy.isReliable(12f, 11000), "Fix GPS vecchio");
        check(!GpsQualityPolicy.confirmedPoor(1000, 2000), "Nessun allarme prematuro");
        check(GpsQualityPolicy.confirmedPoor(1000, 4000), "Allarme confermato");
        check(GpsQualityPolicy.confirmedRecovery(1000, 11000), "Recupero confermato");

        System.out.println("PASS: test algoritmici e policy risparmio energetico superati.");
    }
}
