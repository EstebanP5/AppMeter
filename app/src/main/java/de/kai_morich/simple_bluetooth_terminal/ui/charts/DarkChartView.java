package de.kai_morich.simple_bluetooth_terminal.ui.charts;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Arrays;
import java.util.Locale;

/**
 * Curva del día sobre tarjeta oscura (K1 y K4).
 *  · principal: línea azul con relleno (consumo / hoy)
 *  · solar: área dorada (K1)
 *  · comparación: línea punteada gris (ayer, K4)
 *  · marcas: punto blanco en el pico con su etiqueta y línea "ahora" con punto.
 * Se dibuja con una animación de barrido de izquierda a derecha.
 */
public class DarkChartView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solarFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), area = new Path();
    private final float dp;

    private double[] main = new double[0], solar = new double[0], compare = new double[0];
    private int nowHour = -1;
    private int slots = 24;          // puntos en que se divide el día: 24 por hora, 96 cada 15 min, 288 cada 5 min
    private boolean[] marks = new boolean[0]; // puntos rojos (caídas) sobre la línea principal
    private String peakLabel;
    private boolean yLabels;
    private int gridColor = 0xFF333333, labelColor = 0xFF777777, hiColor = 0xFFFFFFFF;
    private float progress = 1f;
    private final ChartTip tip;
    private int pick = -1; // hora tocada
    private String nameMain = "Consumo", nameSolar = "Solar", nameCompare = "Ayer", unit = "kW";
    private final Runnable clearPick = () -> { pick = -1; invalidate(); };
    private ValueAnimator anim;

    public DarkChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.2f * dp);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(0xFF4C8DFF);
        fill.setColor(0xFF1B3763);
        solarFill.setColor(0xFFD9A21B);
        dashed.setStyle(Paint.Style.STROKE);
        dashed.setStrokeWidth(1.6f * dp);
        dashed.setColor(0xFF8B8B8B);
        dashed.setPathEffect(new DashPathEffect(new float[]{4 * dp, 4 * dp}, 0));
        axis.setStrokeWidth(1f * dp);
        axis.setColor(gridColor);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(9 * dp);
        text.setColor(0xFF777777);
        dot.setColor(0xFFFFFFFF);
        tip = new ChartTip(dp);
    }

    /** Nombres que se muestran en el globito al tocar la gráfica. */
    public void setNames(String main, String solar, String compare) {
        nameMain = main;
        nameSolar = solar;
        nameCompare = compare;
    }

    public void setUnit(String unit) { this.unit = unit; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float w = getWidth(), left = yLabels ? 26 * dp : 0;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                removeCallbacks(clearPick);
                pick = Math.max(0, Math.min(slots - 1, Math.round((e.getX() - left) / (w - left) * slots)));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                postDelayed(clearPick, 3000);
                return true;
            case MotionEvent.ACTION_CANCEL:
                postDelayed(clearPick, 3000);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    /** Muestra la escala (valores a la izquierda) — la usa el detalle del medidor. */
    public void setYLabels(boolean on) { yLabels = on; }

    /** Resolución del día (24, 96 o 288); las series deben traer un valor por cada slot. */
    public void setSlots(int n) { slots = Math.max(2, n); }

    /** Marca con un punto rojo los índices verdaderos de la serie principal. */
    public void setMarks(boolean[] m) {
        marks = m != null ? m : new boolean[0];
        invalidate();
    }

    /** Versión para tarjeta blanca (admin): línea azul con relleno claro, rejilla y textos grises, marcas oscuras. */
    public void setLight(boolean light) {
        if (!light) return;
        gridColor = 0xFFE6E6E6;
        labelColor = 0xFF999999;
        hiColor = 0xFF161616;
        line.setColor(0xFF1E7BFF);
        fill.setColor(0xFFDCE9FF);
        dashed.setColor(0xFFB5B5B5);
        axis.setColor(gridColor);
        text.setColor(labelColor);
        dot.setColor(hiColor);
    }

    public void setLineColors(int main, int fillColor) {
        line.setColor(main);
        fill.setColor(fillColor);
    }

    /**
     * @param main    un valor por hora hasta ahora (0..nowHour)
     * @param solar   idem, opcional (null = sin área solar)
     * @param compare un valor por hora de todo el día, opcional (null = sin línea punteada)
     * @param label   etiqueta del pico, p. ej. "pico 684"; null = sin marca de pico
     */
    public void set(double[] main, double[] solar, double[] compare, int nowHour, String label) {
        this.main = main != null ? main : new double[0];
        this.solar = solar != null ? solar : new double[0];
        this.compare = compare != null ? compare : new double[0];
        this.nowHour = nowHour;
        this.peakLabel = label;
        animateIn();
    }

    /** Igual que {@link #set} pero sin repetir el barrido (actualizaciones en vivo). */
    public void update(double[] main, double[] solar, double[] compare, int nowHour, String label) {
        this.main = main != null ? main : new double[0];
        this.solar = solar != null ? solar : new double[0];
        this.compare = compare != null ? compare : new double[0];
        this.nowHour = nowHour;
        this.peakLabel = label;
        invalidate();
    }

    public void animateIn() {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(900);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(x -> { progress = (float) x.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }

    private float x(int i, float left, float w) { return left + (w - left) * i / (float) slots; }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float left = yLabels ? 26 * dp : 0, top = 16 * dp, bottom = h - 16 * dp;
        double max = 1;
        for (double v : main) max = Math.max(max, v);
        for (double v : solar) max = Math.max(max, v);
        for (double v : compare) max = Math.max(max, v);
        max = niceMax(max);

        // Ejes: línea base y etiquetas 00 · 06 · 12 · 18 · 24
        c.drawLine(left, bottom, w, bottom, axis);
        text.setTextAlign(Paint.Align.CENTER);
        String[] hours = {"00", "06", "12", "18", "24"};
        for (int k = 0; k < 5; k++) {
            float tx = x(k * slots / 4, left, w);
            text.setTextAlign(k == 0 ? Paint.Align.LEFT : k == 4 ? Paint.Align.RIGHT : Paint.Align.CENTER);
            c.drawText(hours[k], tx, h - 3 * dp, text);
        }
        if (yLabels) {
            text.setTextAlign(Paint.Align.LEFT);
            for (int k = 1; k <= 3; k++) {
                float ty = bottom - (bottom - top) * k / 3f;
                c.drawLine(left, ty, w, ty, axis);
                c.drawText(String.format(Locale.US, "%.0f", max * k / 3.0), 0, ty + 3 * dp, text);
            }
        }

        // Barrido de izquierda a derecha
        c.save();
        c.clipRect(0, 0, left + (w - left) * progress, h);

        if (solar.length > 1) { // área solar
            area.reset();
            area.moveTo(x(0, left, w), bottom);
            for (int i = 0; i < solar.length; i++) area.lineTo(x(i, left, w), y(solar[i], max, top, bottom));
            area.lineTo(x(solar.length - 1, left, w), bottom);
            area.close();
            c.drawPath(area, solarFill);
        }
        if (compare.length > 1) { // ayer, punteada
            smooth(path, compare, max, left, w, top, bottom);
            c.drawPath(path, dashed);
        }
        if (main.length > 1) { // principal con relleno
            smooth(path, main, max, left, w, top, bottom);
            area.reset();
            area.addPath(path);
            area.lineTo(x(main.length - 1, left, w), bottom);
            area.lineTo(x(0, left, w), bottom);
            area.close();
            c.drawPath(area, fill);
            c.drawPath(path, line);
        }
        c.restore();

        // Marcas (pico y ahora) una vez que el barrido las alcanzó
        if (main.length > 1 && progress > 0.95f) {
            int peak = 0;
            for (int i = 0; i < main.length; i++) if (main[i] > main[peak]) peak = i;
            dot.setColor(0xFFD32F2F);
            for (int i = 0; i < marks.length && i < main.length; i++) {
                if (marks[i]) c.drawCircle(x(i, left, w), y(main[i], max, top, bottom), 3.5f * dp, dot);
            }
            dot.setColor(hiColor);
            if (peakLabel != null && main[peak] > 0) {
                float px = x(peak, left, w), py = y(main[peak], max, top, bottom);
                c.drawCircle(px, py, 3.5f * dp, dot);
                text.setColor(hiColor);
                text.setTextAlign(px > w - 50 * dp ? Paint.Align.RIGHT : Paint.Align.CENTER);
                c.drawText(peakLabel, px, py - 7 * dp, text);
                text.setColor(labelColor);
            }
            int last = main.length - 1;
            if (nowHour >= 0 && last == Math.min(nowHour, slots - 1)) {
                float nx = x(last, left, w);
                axis.setColor(hiColor);
                c.drawLine(nx, top, nx, bottom, axis);
                axis.setColor(gridColor);
                c.drawCircle(nx, y(main[last], max, top, bottom), 3.5f * dp, dot);
            }
        }
        if (main.length < 2 && progress > 0.5f) {
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText("Sin datos para hoy", left + (w - left) / 2f, (top + bottom) / 2f, text);
        }

        // Globito del punto tocado
        if (pick >= 0 && progress > 0.95f) {
            String[] ls = new String[3];
            int[] cs = new int[3];
            int n = 0;
            double anchor = -1;
            if (pick < main.length) {
                ls[n] = nameMain + " " + ChartTip.num(main[pick]) + " " + unit;
                cs[n++] = line.getColor();
                anchor = main[pick];
            }
            if (pick < solar.length && solar[pick] > 0) {
                ls[n] = nameSolar + " " + ChartTip.num(solar[pick]) + " " + unit;
                cs[n++] = 0xFFF2B01E;
                if (anchor < 0) anchor = solar[pick];
            }
            if (pick < compare.length) {
                ls[n] = nameCompare + " " + ChartTip.num(compare[pick]) + " " + unit;
                cs[n++] = 0xFF9A9A9A;
                if (anchor < 0) anchor = compare[pick];
            }
            if (n > 0) {
                float px = x(pick, left, w), py = y(anchor, max, top, bottom);
                axis.setColor(hiColor == 0xFFFFFFFF ? 0x66FFFFFF : 0x66161616);
                c.drawLine(px, top, px, bottom, axis);
                axis.setColor(gridColor);
                c.drawCircle(px, py, 4 * dp, dot);
                tip.draw(c, px, py, w, h, String.format(Locale.US, "%02d:%02d", pick * 1440 / slots / 60, pick * 1440 / slots % 60), Arrays.copyOf(ls, n), Arrays.copyOf(cs, n));
            }
        }
    }

    private float y(double v, double max, float top, float bottom) {
        return (float) (bottom - Math.max(0, v) / max * (bottom - top));
    }

    /** Línea suave: cada tramo es una curva con los puntos medios como control. */
    private void smooth(Path p, double[] v, double max, float left, float w, float top, float bottom) {
        p.reset();
        for (int i = 0; i < v.length; i++) {
            float px = x(i, left, w), py = y(v[i], max, top, bottom);
            if (i == 0) { p.moveTo(px, py); continue; }
            float prevX = x(i - 1, left, w), prevY = y(v[i - 1], max, top, bottom);
            float mid = (prevX + px) / 2f;
            p.cubicTo(mid, prevY, mid, py, px, py);
        }
    }

    /** Tope "redondo" para la escala (con un poco de aire arriba). */
    private static double niceMax(double max) {
        double m = max * 1.12;
        double pow = Math.pow(10, Math.floor(Math.log10(m)));
        double n = m / pow;
        double step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
        return step * pow;
    }
}
