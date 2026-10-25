package it.example.passolibero;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Widget standard Android: UI senza AppCompat/Compose/librerie esterne. */
public final class Ui {
    public static final int INK = Color.rgb(22, 43, 49);
    public static final int MUTED = Color.rgb(90, 112, 118);
    public static final int GREEN = Color.rgb(0, 125, 115);
    public static final int BG = Color.rgb(246, 250, 249);
    private Ui() { }

    public static int dp(Context c, float dp) { return Math.round(dp * c.getResources().getDisplayMetrics().density); }
    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL); return l;
    }
    public static TextView text(Context c, String value, int sp, boolean bold, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    public static GradientDrawable shape(int background, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable(); d.setColor(background); d.setCornerRadius(dp(c, radiusDp)); return d;
    }
    public static LinearLayout card(Context c) {
        LinearLayout card = vertical(c); card.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        card.setBackground(shape(Color.WHITE, 18, c)); card.setElevation(dp(c, 2)); return card;
    }
    public static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : GREEN);
        b.setBackground(shape(primary ? GREEN : Color.rgb(230, 246, 242), 12, c));
        b.setMinHeight(dp(c, 48));
        return b;
    }
    public static void marginBottom(Context c, LinearLayout parent, View child, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(c, bottom); parent.addView(child, p);
    }
    public static void weighted(LinearLayout parent, View child) {
        parent.addView(child, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    }
}
