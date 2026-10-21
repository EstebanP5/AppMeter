package de.kai_morich.simple_bluetooth_terminal.ui.charts;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Barras de energía de A5: una por hora / día. Las pasadas en azul, la actual en azul claro y las que faltan con el valor
 * de la comparación (ayer) en gris; la mayor va en negro con su valor. Al tocar una barra sale su valor.
 */
public class EnergyBarsView extends View {

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ChartTip tip;
    private final float dp;

    private String[] labels = new String[0], tips = new String[0];
    private double[] values = new double[0], ghost;
    private int nowIdx = -1, labelEvery = 1, picked = -1;
    private boolean highlightMax = true;
    private int mainColor = 0xFF1E7BFF, nowColor = 0xFF8FB8FF, ghostColor = 0xFFE0E0E0;
    private String unit = "kWh", mainName = "Hoy", ghostName = "Ayer";
    private float grow = 1f;
    private ValueAnimator anim;

    public EnergyBarsView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        axis.setColor(0xFFE0E0E0);
        axis.setStrokeWidth(1f * dp);
        text.setTextSize(9.5f * dp);
        text.setColor(0xFF999999);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.MONOSPACE);
        tip = new ChartTip(dp);
    }

    public void setColors(int main, int now, int ghost) {
        mainColor = main;
        nowColor = now;
        ghostColor = ghost;
    }

    public void setUnit(String unit) { this.unit = unit; }

    public void setNames(String main, String ghost) {
        mainName = main;
        ghostName = ghost;
    }

    public void setTips(String[] titles) { tips = titles != null ? titles : new String[0]; }

    /**
     * @param values    una barra por posición (NaN = sin dato)
     * @param ghost     comparación opcional, se muestra en las posiciones que aún no llegan (después de nowIdx)
     * @param nowIdx    posición en curso; -1 = periodo completo (todas en azul)
     * @param highlight la barra mayor va en negro con su valor encima
     */
    public void set(String[] labels, double[] values, double[] ghost, int nowIdx, boolean highlight, int labelEvery) {
        this.labels = labels;
        this.values = values;
        this.ghost = ghost;
        this.nowIdx = nowIdx;
        this.highlightMax = highlight;
        this.labelEvery = Math.max(1, labelEvery);
        this.picked = -1;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(650);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(x -> { grow = (float) x.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }

    private static boolean ok(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    @Override
    protected void onDraw(Canvas c) {
        int n = values.length;
        float w = getWidth(), h = getHeight(), base = h - 18 * dp, top = 18 * dp;
        c.drawLine(0, base, w, base, axis);
        if (n == 0) return;
        double max = 0;
        int maxAt = -1;
        for (int i = 0; i < n; i++) {
            if (ok(values[i]) && (nowIdx < 0 || i <= nowIdx) && values[i] > max) { max = values[i]; maxAt = i; }
            if (ok(values[i])) max = Math.max(max, values[i]);
            if (ghost != null && i < ghost.length && ok(ghost[i])) max = Math.max(max, ghost[i]);
        }
        if (max <= 0) {
            text.setTextSize(12 * dp);
            c.drawText("Sin datos", w / 2, (top + base) / 2, text);
            text.setTextSize(9.5f * dp);
            return;
        }
        float slot = w / n, bw = slot * 0.72f;
        for (int i = 0; i < n; i++) {
            float x = i * slot + (slot - bw) / 2;
            double v;
            int color;
            if (nowIdx >= 0 && i > nowIdx) {
                v = ghost != null && i < ghost.length ? ghost[i] : Double.NaN;
                color = ghostColor;
            } else {
                v = values[i];
                color = nowIdx >= 0 && i == nowIdx ? nowColor : mainColor;
                if (highlightMax && i == maxAt) color = 0xFF161616;
            }
            if (ok(v) && v > 0) {
                float g = Math.max(0f, Math.min(1f, grow * 1.4f - 0.4f * i / Math.max(1, n - 1)));
                float bh = (float) (v / max * (base - top)) * g;
                bar.setColor(color);
                c.drawRect(x, base - bh, x + bw, base, bar);
            }
            if (i % labelEvery == 0 && i < labels.length) c.drawText(labels[i], x + bw / 2, h - 5 * dp, text);
        }
        if (highlightMax && maxAt >= 0 && grow > 0.95f) {
            float x = maxAt * slot + slot / 2;
            text.setColor(0xFF161616);
            text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            c.drawText(ChartTip.num(values[maxAt]), Math.max(14 * dp, Math.min(w - 14 * dp, x)), (float) (base - values[maxAt] / max * (base - top)) - 4 * dp, text);
            text.setColor(0xFF999999);
            text.setTypeface(Typeface.MONOSPACE);
        }
        if (picked >= 0 && picked < n && grow > 0.95f) {
            boolean future = nowIdx >= 0 && picked > nowIdx;
            double v = future ? (ghost != null && picked < ghost.length ? ghost[picked] : Double.NaN) : values[picked];
            float x = picked * slot + slot / 2;
            float y = ok(v) ? (float) (base - Math.max(0, v) / max * (base - top)) : base;
            String name = future ? ghostName : mainName;
            String[] ls = {name + " " + (ok(v) ? ChartTip.num(v) : "—") + " " + unit};
            int[] cs = {future ? 0xFF9A9A9A : mainColor};
            if (!future && ghost != null && picked < ghost.length && ok(ghost[picked])) {
                ls = new String[]{ls[0], ghostName + " " + ChartTip.num(ghost[picked]) + " " + unit};
                cs = new int[]{cs[0], 0xFF9A9A9A};
            }
            tip.draw(c, x, y, w, h, picked < tips.length ? tips[picked] : picked < labels.length ? labels[picked] : "", ls, cs);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (values.length == 0) return true;
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            int i = (int) (e.getX() / (getWidth() / (float) values.length));
            i = Math.max(0, Math.min(values.length - 1, i));
            if (i != picked) { picked = i; invalidate(); }
        } else if (a == MotionEvent.ACTION_UP) {
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() { return super.performClick(); }
}
