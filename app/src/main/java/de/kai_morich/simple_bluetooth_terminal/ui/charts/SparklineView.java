package de.kai_morich.simple_bluetooth_terminal.ui.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/** Mini línea del día de un nodo (A2): sin ejes, un punto al final; las horas sin dato (NaN) se omiten. */
public class SparklineView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float dp;
    private double[] values = new double[0];

    public SparklineView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.6f * dp);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(0xFF1E7BFF);
        dot.setColor(0xFF1E7BFF);
    }

    public void setColor(int color) {
        line.setColor(color);
        dot.setColor(color);
        invalidate();
    }

    /** @param v valores en orden (se normalizan al alto de la vista) */
    public void set(double[] v) {
        values = v != null ? v : new double[0];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 3 * dp;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double d : values) {
            if (Double.isNaN(d) || Double.isInfinite(d)) continue;
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        if (min == Double.MAX_VALUE) return;
        if (max - min < 1e-9) { max += 1; min -= 1; }
        int n = values.length;
        path.reset();
        boolean pen = false;
        float lx = 0, ly = 0;
        for (int i = 0; i < n; i++) {
            double d = values[i];
            if (Double.isNaN(d) || Double.isInfinite(d)) { pen = false; continue; }
            float x = pad + (w - 2 * pad) * i / Math.max(1, n - 1);
            float y = (float) (h - pad - (d - min) / (max - min) * (h - 2 * pad));
            if (!pen) { path.moveTo(x, y); pen = true; } else path.lineTo(x, y);
            lx = x;
            ly = y;
        }
        c.drawPath(path, line);
        c.drawCircle(lx, ly, 2.2f * dp, dot);
    }
}
