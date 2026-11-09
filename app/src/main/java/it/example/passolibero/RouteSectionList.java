package it.example.passolibero;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Elenco interattivo della traccia, condiviso da sessione corrente e storico. */
public final class RouteSectionList {
    private final Context context;
    private final TrackMap map;
    private final LinearLayout root;
    private final LinearLayout rows;
    private final TextView empty;
    private final Button showAll;
    private final List<SectionRow> rendered = new ArrayList<>();

    public RouteSectionList(Context context, TrackMap map) {
        this.context = context;
        this.map = map;
        root = Ui.vertical(context);

        LinearLayout header = Ui.horizontal(context);
        Ui.weighted(header, Ui.text(context, "SEZIONI DEL PERCORSO", 14, true, Ui.INK));
        showAll = Ui.button(context, "Mostra tutte", false);
        showAll.setTextSize(12);
        showAll.setMinHeight(Ui.dp(context, 40));
        showAll.setOnClickListener(v -> map.selectSection(0));
        header.addView(showAll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(context, 40)));
        Ui.marginBottom(context, root, header, 7);

        empty = Ui.text(context, "I tratti compariranno quando arriveranno i punti GPS.",
                12, false, Ui.MUTED);
        Ui.marginBottom(context, root, empty, 3);
        rows = Ui.vertical(context);
        Ui.marginBottom(context, root, rows, 0);
        map.setOnSectionSelectedListener(number -> refreshSelection());
        refreshSelection();
    }

    public LinearLayout view() { return root; }

    /**
     * I Section restano gli stessi oggetti mentre arrivano nuovi fix.
     * Vengono create soltanto nuove righe; l'ultima e la penultima
     * vengono aggiornate per mantenerne le statistiche correnti.
     */
    public void sync() {
        List<RouteSections.Section> sections = map.sections();
        if (rendered.size() > sections.size()
                || (!rendered.isEmpty() && rendered.get(0).section != sections.get(0))) {
            rows.removeAllViews();
            rendered.clear();
        }
        for (int i = rendered.size(); i < sections.size(); i++) {
            SectionRow row = new SectionRow(sections.get(i));
            rendered.add(row);
            Ui.marginBottom(context, rows, row.view, 6);
        }
        empty.setVisibility(sections.isEmpty() ? View.VISIBLE : View.GONE);
        showAll.setEnabled(map.selectedSectionNumber() != 0);
        // Refresh incrementale: la penultima può aver ricevuto gli ultimi fix
        // nello stesso batch in cui è stata aperta la nuova sezione.
        if (!rendered.isEmpty()) {
            rendered.get(rendered.size() - 1).render();
            if (rendered.size() > 1) rendered.get(rendered.size() - 2).render();
        }
    }

    private void refreshSelection() {
        showAll.setEnabled(map.selectedSectionNumber() != 0);
        for (SectionRow row : rendered) row.render();
    }

    private final class SectionRow {
        final RouteSections.Section section;
        final LinearLayout view;
        final View strip;
        final TextView name, summary, detail;

        SectionRow(RouteSections.Section section) {
            this.section = section;
            view = Ui.vertical(context);
            view.setPadding(Ui.dp(context, 11), Ui.dp(context, 10),
                    Ui.dp(context, 11), Ui.dp(context, 10));
            view.setMinimumHeight(Ui.dp(context, 56));
            view.setClickable(true);
            view.setFocusable(true);
            view.setOnClickListener(v ->
                    map.selectSection(map.selectedSectionNumber() == section.number
                            ? 0 : section.number));

            LinearLayout line = Ui.horizontal(context);
            strip = new View(context);
            LinearLayout.LayoutParams marker = new LinearLayout.LayoutParams(
                    Ui.dp(context, 5), Ui.dp(context, 40));
            marker.rightMargin = Ui.dp(context, 12);
            line.addView(strip, marker);
            LinearLayout labels = Ui.vertical(context);
            name = Ui.text(context, "", 14, true, Ui.INK);
            Ui.marginBottom(context, labels, name, 3);
            summary = Ui.text(context, "", 12, false, Ui.MUTED);
            Ui.marginBottom(context, labels, summary, 0);
            Ui.weighted(line, labels);
            Ui.marginBottom(context, view, line, 0);

            detail = Ui.text(context, "", 12, false, Ui.INK);
            detail.setPadding(Ui.dp(context, 17), Ui.dp(context, 8), 0, 0);
            Ui.marginBottom(context, view, detail, 0);
            render();
        }

        void render() {
            int selected = map.selectedSectionNumber();
            boolean current = selected == section.number;
            int color = RouteSectionSelection.color(section.kind, section.number, selected);
            strip.setBackground(Ui.shape(color, 4, context));
            GradientDrawable background = Ui.shape(
                    current ? Color.rgb(229, 246, 242) : Color.rgb(247, 250, 249),
                    12, context);
            background.setStroke(Ui.dp(context, current ? 2 : 1),
                    current ? Ui.GREEN : Color.rgb(220, 231, 228));
            view.setBackground(background);

            String label = "Sezione " + section.number + " · "
                    + RouteSections.label(section.kind);
            name.setText(label);
            summary.setText(RunMath.duration(section.durationMs()) + " · "
                    + String.format(Locale.ITALY, "%.1f m", section.distanceM)
                    + (section.approximateSprint ? " · stima" : ""));
            view.setContentDescription(label + ". Tocca per "
                    + (current ? "mostrare tutti i tratti" : "evidenziare questo tratto"));
            double average = section.averageKmh();
            detail.setText("Velocità media: "
                    + (Double.isFinite(average) ? RunMath.velocity(average) + " km/h" : "—")
                    + " · Max: "
                    + (Double.isFinite(section.maxKmh)
                            ? RunMath.velocity(section.maxKmh) + " km/h" : "—")
                    + " · Punti GPS: " + section.samples);
            detail.setVisibility(current ? View.VISIBLE : View.GONE);
        }
    }
}
