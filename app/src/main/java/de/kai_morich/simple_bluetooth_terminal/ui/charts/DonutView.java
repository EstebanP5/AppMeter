package de.kai_morich.simple_bluetooth_terminal.ui.charts;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

/** Dona sol / CFE con el % solar en el centro. El arco solar se dibuja con animación. */
public class DonutView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float dp;
    private float solarPct;   // 0..100 mostrado
    private boolean hasData;

    public DonutView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(8 * dp);
        ring.setStrokeCap(Paint.Cap.BUTT);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setTextSize(11 * dp);
    }

    /** @param pct porcentaje solar (0–100); anima desde el valor anterior. */
    public void set(float pct) {
        final float from = hasData ? solarPct : 0f;
        hasData = true;
        final float to = Math.max(0f, Math.min(100f, pct));
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(750);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(x -> {
            solarPct = (float) x.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float pad = ring.getStrokeWidth() / 2f + 1 * dp;
        box.set(pad, pad, s - pad, s - pad);
        ring.setColor(0xFF1E7BFF);               // CFE: anillo completo
        c.drawArc(box, 0, 360, false, ring);
        ring.setColor(0xFFF2B01E);               // Sol: arco desde arriba, en sentido horario
        c.drawArc(box, -90, 360f * solarPct / 100f, false, ring);
        String label = hasData ? String.format(Locale.US, "%.0f%%", solarPct) : "--";
        c.drawText(label, s / 2f, s / 2f + 4 * dp, text);
    }
}
