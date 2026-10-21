package de.kai_morich.simple_bluetooth_terminal.ui.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Locale;

/**
 * Gráfica de 288 puntos (uno cada 5 min) sobre tarjeta oscura (K5). Varias series (L1 / L2 / L3), huecos donde el
 * valor es NaN y un marcador naranja que se mueve al tocar o arrastrar; el fragmento lo comparte entre las gráficas.
 */
public class TechChartView extends View {

    public static final int POINTS = 288;

    public interface OnMarker { void on(int index); }

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float dp;
    private final int slop;

    private String title = "", unit = "";
    private boolean barsMode, showHours;
    private double[][] series = new double[0][];
    private int[] colors = new int[0];
    private boolean[] visible = new boolean[0];
    private int markerIdx = -1;
    private OnMarker listener;
    private float downX, downY;
    private boolean dragging;

    public TechChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.2f * dp);
        line.setStrokeJoin(Paint.Join.ROUND);
        grid.setStrokeWidth(1f * dp);
        grid.setColor(0xFF2A2A2A);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(8.5f * dp);
        text.setColor(0xFF777777);
        head.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        head.setTextSize(12 * dp);
        head.setColor(0xFFFFFFFF);
        marker.setColor(0xFFFF8A00);
        marker.setStrokeWidth(1.5f * dp);
        bar.setStrokeWidth(1.2f * dp);
    }

    public void setHeader(String title, String unit) {
        this.title = title;
        this.unit = unit;
        invalidate();
    }

    /** Dibuja barras desde el cero (energía cada 5 min) en lugar de líneas. */
    public void setBars(boolean on) { barsMode = on; }

    /** Muestra las horas 00 · 06 · 12 · 18 · 24 abajo (sólo la última gráfica). */
    public void setShowHours(boolean on) { showHours = on; }

    public void setOnMarker(OnMarker l) { listener = l; }

    /** @param series una por fase; cada una con 288 valores, NaN = sin dato */
    public void setSeries(double[][] series, int[] colors, boolean[] visible) {
        this.series = series != null ? series : new double[0][];
        this.colors = colors;
        this.visible = visible;
        invalidate();
    }

    public void setMarker(int idx) {
        markerIdx = idx;
        invalidate();
    }

    private float x(int i, float left, float w) { return left + (w - left) * i / (POINTS - 1f); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float left = 34 * dp, top = 26 * dp, bottom = h - (showHours ? 16 : 6) * dp;

        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(unit, w - 2 * dp, 14 * dp, text);
        head.setTextAlign(Paint.Align.LEFT);
        c.drawText(title, 2 * dp, 14 * dp, head);

        // Rango de las series visibles
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int s = 0; s < series.length; s++) {
            if (!visible[s] || series[s] == null) continue;
            for (double v : series[s]) {
                if (bad(v)) continue;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        if (barsMode) { min = Math.min(min, 0); max = Math.max(max, 0); }
        if (min == Double.MAX_VALUE) { // sin datos
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText("Sin datos", left + (w - left) / 2f, (top + bottom) / 2f, text);
            drawHours(c, left, w, h);
            return;
        }
        if (max - min < 1e-9) { max += 1; min -= 1; }
        double step = niceStep((max - min) / 3.0);
        double lo = Math.floor(min / step) * step, hi = Math.ceil(max / step) * step;

        // Cuadrícula y escala
        text.setTextAlign(Paint.Align.RIGHT);
        for (double t = lo; t <= hi + step * 0.01; t += step) {
            float y = (float) (bottom - (t - lo) / (hi - lo) * (bottom - top));
            grid.setColor(Math.abs(t) < step * 0.01 ? 0xFF555555 : 0xFF2A2A2A);
            c.drawLine(left, y, w, y, grid);
            c.drawText(fmt(t, step), left - 4 * dp, y + 3 * dp, text);
        }
        float zeroY = (float) (bottom - (0 - lo) / (hi - lo) * (bottom - top));
        zeroY = Math.max(top, Math.min(bottom, zeroY));

        for (int s = 0; s < series.length; s++) {
            if (!visible[s] || series[s] == null) continue;
            double[] v = series[s];
            if (barsMode) {
                for (int i = 0; i < v.length; i++) {
                    if (bad(v[i])) continue;
                    float y = (float) (bottom - (v[i] - lo) / (hi - lo) * (bottom - top));
                    bar.setColor(v[i] >= 0 ? 0xFFD9A21B : 0xFF8FA8CC);
                    float px = x(i, left, w);
                    c.drawLine(px, zeroY, px, y, bar);
                }
            } else {
                line.setColor(colors[s]);
                path.reset();
                boolean pen = false;
                for (int i = 0; i < v.length; i++) {
                    if (bad(v[i])) { pen = false; continue; } // hueco, no una caída a 0
                    float px = x(i, left, w), py = (float) (bottom - (v[i] - lo) / (hi - lo) * (bottom - top));
                    if (!pen) { path.moveTo(px, py); pen = true; } else path.lineTo(px, py);
                }
                c.drawPath(path, line);
            }
        }
        drawHours(c, left, w, h);

        if (markerIdx >= 0) {
            float mx = x(markerIdx, left, w);
            c.drawLine(mx, top - 4 * dp, mx, bottom, marker);
        }
    }

    private void drawHours(Canvas c, float left, float w, float h) {
        if (!showHours) return;
        String[] hs = {"00", "06", "12", "18", "24"};
        for (int k = 0; k < 5; k++) {
            text.setTextAlign(k == 0 ? Paint.Align.LEFT : k == 4 ? Paint.Align.RIGHT : Paint.Align.CENTER);
            c.drawText(hs[k], left + (w - left) * k / 4f, h - 3 * dp, text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                move(e.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(e.getX() - downX) > slop && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY)) dragging = true;
                if (dragging) getParent().requestDisallowInterceptTouchEvent(true); // arrastre horizontal: no scrollear
                move(e.getX());
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private void move(float px) {
        float left = 34 * dp, w = getWidth();
        int i = Math.round((px - left) / (w - left) * (POINTS - 1));
        i = Math.max(0, Math.min(POINTS - 1, i));
        if (listener != null) listener.on(i);
    }

    /** NaN = hueco; los infinitos también se descartan (algunas GPU no dibujan nada si una coordenada lo es). */
    private static boolean bad(double v) { return Double.isNaN(v) || Double.isInfinite(v); }

    private static double niceStep(double raw) {
        if (raw <= 0) return 1;
        double pow = Math.pow(10, Math.floor(Math.log10(raw)));
        double n = raw / pow;
        return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * pow;
    }

    private static String fmt(double v, double step) {
        return step < 1 ? String.format(Locale.US, "%.1f", v) : String.format(Locale.US, "%.0f", v);
    }
}
