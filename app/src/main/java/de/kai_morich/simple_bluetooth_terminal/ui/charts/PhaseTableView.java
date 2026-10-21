package de.kai_morich.simple_bluetooth_terminal.ui.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * Tabla por fase de A3: cabecera L1 / L2 / L3 (negro, rojo, azul) y filas Voltaje, Corriente (con una barra de color
 * proporcional bajo cada valor) y FP. Se dibuja a mano con alto fijo para que no dependa de pesos ni de texto que se parta.
 */
public class PhaseTableView extends View {

    private static final int[] COLORS = {0xFF161616, 0xFFE00000, 0xFF1E7BFF};
    private static final float HEAD = 30, ROW = 36, ROW_BAR = 46;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float dp;

    private double[] volt = new double[3], amp = new double[3], pf = new double[3];

    public PhaseTableView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        head.setColor(0xFFFFFFFF);
        head.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        head.setTextSize(13 * dp);
        head.setTextAlign(Paint.Align.RIGHT);
        cell.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        cell.setTextSize(14 * dp);
        cell.setColor(0xFF161616);
        cell.setTextAlign(Paint.Align.RIGHT);
        label.setTextSize(12.5f * dp);
        label.setColor(0xFF555555);
        line.setColor(0xFFEEEEEE);
        line.setStrokeWidth(1f * dp);
    }

    public void set(double[] volt, double[] amp, double[] pf) {
        this.volt = volt;
        this.amp = amp;
        this.pf = pf;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(View.resolveSize(Math.round(300 * dp), wSpec), Math.round((HEAD + ROW + ROW_BAR + ROW) * dp));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float labelW = 88 * dp, colW = (w - labelW) / 3f, r = 14 * dp;
        // Esquinas redondeadas de toda la tabla
        clip.reset();
        rect.set(0, 0, w, h);
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);

        for (int k = 0; k < 3; k++) {
            fill.setColor(COLORS[k]);
            c.drawRect(labelW + colW * k, 0, labelW + colW * (k + 1), HEAD * dp, fill);
            c.drawText("L" + (k + 1), labelW + colW * (k + 1) - 12 * dp, 20 * dp, head);
        }
        float y = HEAD * dp;
        row(c, "Voltaje V", volt, "%.1f", y, ROW * dp, labelW, colW, w, false);
        y += ROW * dp;
        row(c, "Corriente\nA", amp, "%.1f", y, ROW_BAR * dp, labelW, colW, w, true);
        y += ROW_BAR * dp;
        row(c, "FP", pf, "%.2f", y, ROW * dp, labelW, colW, w, false);
        c.restore();
    }

    private void row(Canvas c, String name, double[] v, String fmt, float top, float height, float labelW, float colW, float w, boolean bars) {
        c.drawLine(0, top, w, top, line);
        float base = top + (bars ? 24 : 23) * dp;
        String[] parts = name.split("\n");
        for (int i = 0; i < parts.length; i++) c.drawText(parts[i], 12 * dp, parts.length > 1 ? base - 6 * dp + i * 14 * dp : base, label);
        double max = 0;
        for (double d : v) max = Math.max(max, Math.abs(d));
        for (int k = 0; k < 3; k++) {
            float right = labelW + colW * (k + 1) - 12 * dp;
            boolean ok = v != null && k < v.length && v[k] != 0;
            c.drawText(ok ? String.format(Locale.US, fmt, Math.abs(v[k])) : "—", right, base, cell);
            if (bars && ok && max > 0) {
                float full = colW - 24 * dp, bw = (float) (full * Math.abs(v[k]) / max);
                fill.setColor(COLORS[k]);
                rect.set(right - bw, top + height - 10 * dp, right, top + height - 6 * dp);
                c.drawRoundRect(rect, 2 * dp, 2 * dp, fill);
            }
        }
    }
}
