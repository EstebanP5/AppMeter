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

/** Barras apiladas (K2): Solar abajo, CFE arriba. La última barra puede ir atenuada ("en curso"). Tocar una barra la selecciona. */
public class StackedBarsView extends View {

    public interface OnBar { void on(int index); }

    private final Paint solarP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cfeP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private String[] labels = new String[0];
    private double[] solar = new double[0], cfe = new double[0];
    private boolean dimLast;
    private int selected = -1;
    private int labelEvery = 1;
    private OnBar listener;
    private float grow = 1f; // 0..1: las barras crecen desde la base
    private ValueAnimator anim;
    private int axisColor = 0xFFDDDDDD, labelColor = 0xFF888888;
    private String emptyText = "Sin datos";
    private final ChartTip tip;
    private String[] tips = new String[0];
    private String solarName = "Solar", cfeName = "CFE", unit = "kWh";

    public StackedBarsView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        solarP.setColor(0xFFF2B01E);
        cfeP.setColor(0xFF1E7BFF);
        axis.setColor(0xFFDDDDDD);
        axis.setStrokeWidth(1f * dp);
        text.setColor(0xFF888888);
        text.setTextSize(10 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT);
        sel.setStyle(Paint.Style.STROKE);
        sel.setStrokeWidth(1.5f * dp);
        sel.setColor(0xFF161616);
        tip = new ChartTip(dp);
    }

    /** Titulos del globito por barra (p. ej. "Lun 13"); si faltan se usa la etiqueta del eje. */
    public void setTips(String[] titles) { this.tips = titles != null ? titles : new String[0]; }

    /** Nombres de las dos series en el globito; vacio = esa serie no se nombra. */
    public void setNames(String solarName, String cfeName) {
        this.solarName = solarName;
        this.cfeName = cfeName;
    }

    public void setUnit(String unit) { this.unit = unit; }

    public void setOnBar(OnBar l) { listener = l; }

    /** @param labelEvery pinta una etiqueta de cada N barras (la última siempre). */
    public void set(String[] labels, double[] solar, double[] cfe, boolean dimLast, int labelEvery) {
        this.labels = labels;
        this.solar = solar;
        this.cfe = cfe;
        this.dimLast = dimLast;
        this.labelEvery = Math.max(1, labelEvery);
        this.selected = -1;
        animateIn();
    }

    /** Colores para usarlo sobre una tarjeta oscura. */
    public void setDark(boolean dark) {
        axisColor = dark ? 0xFF333333 : 0xFFDDDDDD;
        labelColor = dark ? 0xFF888888 : 0xFF888888;
        axis.setColor(axisColor);
        text.setColor(labelColor);
    }

    /** Las barras crecen desde la base con un pequeño escalonado de izquierda a derecha. */
    public void animateIn() {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(700);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(x -> { grow = (float) x.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }

    public void setSelected(int i) { selected = i; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        int n = solar.length;
        float w = getWidth(), h = getHeight();
        float base = h - 20 * dp, top = 8 * dp;
        c.drawLine(0, base, w, base, axis);
        double max = 0;
        for (int i = 0; i < n; i++) max = Math.max(max, solar[i] + cfe[i]);
        if (n == 0 || max <= 0) {
            text.setTextSize(12 * dp);
            c.drawText(emptyText, w / 2, (top + base) / 2, text);
            text.setTextSize(10 * dp);
            return;
        }
        float slot = w / n, bar = slot * 0.7f;
        int tipAt = -1;
        float tipX = 0, tipY = 0;
        for (int i = 0; i < n; i++) {
            float x = i * slot + (slot - bar) / 2;
            // Cada barra arranca un poco después que la anterior
            float g = Math.max(0f, Math.min(1f, (grow * (1f + 0.5f) - 0.5f * i / Math.max(1, n - 1))));
            float sh = (float) (solar[i] / max * (base - top)) * g;
            float ch = (float) (cfe[i] / max * (base - top)) * g;
            int alpha = dimLast && i == n - 1 ? 115 : 255; // 45 %
            solarP.setAlpha(alpha);
            cfeP.setAlpha(alpha);
            c.drawRect(x, base - sh, x + bar, base, solarP);
            c.drawRect(x, base - sh - ch, x + bar, base - sh, cfeP);
            if (i == selected) {
                c.drawRect(x - dp, base - sh - ch - dp, x + bar + dp, base, sel);
                tipAt = i;
                tipX = x + bar / 2;
                tipY = base - sh - ch;
            }
            if ((i % labelEvery == 0 || i == n - 1) && i < labels.length) {
                c.drawText(labels[i], x + bar / 2, h - 5 * dp, text);
            }
        }
        if (tipAt >= 0 && grow > 0.95f) {
            String[] ls = new String[3];
            int[] cs = new int[3];
            int k = 0;
            if (solar[tipAt] > 0) { ls[k] = (solarName.isEmpty() ? "" : solarName + " ") + ChartTip.num(solar[tipAt]) + " " + unit; cs[k++] = 0xFFF2B01E; }
            if (cfe[tipAt] > 0) { ls[k] = (cfeName.isEmpty() ? "" : cfeName + " ") + ChartTip.num(cfe[tipAt]) + " " + unit; cs[k++] = 0xFF1E7BFF; }
            if (solar[tipAt] > 0 && cfe[tipAt] > 0) { ls[k] = "Total " + ChartTip.num(solar[tipAt] + cfe[tipAt]) + " " + unit; cs[k++] = 0xFFFFFFFF; }
            if (k == 0) { ls[k] = "0 " + unit; cs[k++] = 0xFF888888; }
            String head = tipAt < tips.length ? tips[tipAt] : tipAt < labels.length ? labels[tipAt] : "";
            tip.draw(c, tipX, tipY, w, h, head, java.util.Arrays.copyOf(ls, k), java.util.Arrays.copyOf(cs, k));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (solar.length == 0) return true;
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            int i = (int) (e.getX() / (getWidth() / (float) solar.length));
            if (i >= 0 && i < solar.length && i != selected) {
                selected = i;
                invalidate();
                if (listener != null) listener.on(i);
            }
        } else if (a == MotionEvent.ACTION_UP) {
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() { return super.performClick(); }
}
