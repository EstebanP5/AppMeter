package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Diagrama fasorial del diseño (viewBox 100×100): círculos guía, un vector por canal con su etiqueta
 * en la punta. Referencia: 0° arriba, ángulos en sentido horario. Cuadrado, de 128dp como máximo.
 */
public class PhasorView extends View {

    private static final float CX = 50f, CY = 52f, R_MAX = 34f;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    private String title = "", subtitle = "";
    private float[] mag = new float[0], angle = new float[0];
    private String[] labels = new String[0];
    private int[] colors = new int[0];
    private boolean[] bad = new boolean[0];
    private float stroke = 2.5f;

    public PhasorView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        bg.setColor(0xFF0B0B0B);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(0.8f);
        vec.setStrokeCap(Paint.Cap.ROUND);
        txt.setTypeface(Typeface.MONOSPACE);
    }

    /**
     * @param mag   largo relativo de cada vector, 0..1
     * @param angle grados en sentido horario desde arriba
     * @param bad   canales con error: se les agrega " ✕" a la etiqueta
     */
    public void setData(String title, String subtitle, float[] mag, float[] angle, String[] labels,
                        int[] colors, boolean[] bad, float stroke) {
        this.title = title;
        this.subtitle = subtitle;
        this.mag = mag;
        this.angle = angle;
        this.labels = labels;
        this.colors = colors;
        this.bad = bad;
        this.stroke = stroke;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = Math.min((int) (128 * dp), MeasureSpec.getSize(w));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = getWidth() / 100f;
        c.save();
        c.scale(s, s);
        c.drawRoundRect(new RectF(0, 0, 100, 100), 8, 8, bg);

        ring.setColor(0xFF2C2C2C);
        c.drawCircle(CX, CY, 36f, ring);
        ring.setColor(0xFF222222);
        c.drawCircle(CX, CY, 20f, ring);

        vec.setStrokeWidth(stroke);
        txt.setTextSize(7.5f);
        txt.setFakeBoldText(true);
        txt.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < mag.length; i++) {
            double th = Math.toRadians(angle[i]);
            float dx = (float) Math.sin(th), dy = (float) -Math.cos(th);
            float len = R_MAX * Math.max(0f, Math.min(1f, mag[i]));
            vec.setColor(colors[i]);
            txt.setColor(colors[i]);
            c.drawLine(CX, CY, CX + dx * len, CY + dy * len, vec);
            // La etiqueta va un poco más allá de la punta (mínimo fuera del círculo interior)
            float lr = Math.max(len, 10f) + 7f;
            String label = labels[i] + (bad.length > i && bad[i] ? " ✕" : "");
            c.drawText(label, CX + dx * lr, CY + dy * lr + 2.5f, txt);
        }
        vec.setColor(0xFFCCFFEE);
        vec.setStyle(Paint.Style.FILL);
        c.drawCircle(CX, CY, 2.5f, vec);
        vec.setStyle(Paint.Style.STROKE);

        txt.setColor(0xFFFFFFFF);
        txt.setTextSize(9f);
        txt.setTextAlign(Paint.Align.LEFT);
        c.drawText(title, 5f, 10f, txt);
        txt.setColor(0xFF888888);
        txt.setTextSize(6.5f);
        txt.setFakeBoldText(false);
        txt.setTextAlign(Paint.Align.RIGHT);
        c.drawText(subtitle, 95f, 10f, txt);
        c.restore();
    }
}
