package de.kai_morich.simple_bluetooth_terminal.ui.charts;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Locale;

/** Globito con el valor de un punto de una gráfica (se dibuja al tocarla). */
public final class ChartTip {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float dp;

    public ChartTip(float dp) {
        this.dp = dp;
        fill.setColor(0xF2222222);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1f * dp);
        stroke.setColor(0xFF555555);
        title.setColor(0xFFFFFFFF);
        title.setTextSize(11 * dp);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        body.setColor(0xFFEEEEEE);
        body.setTextSize(11 * dp);
        body.setTypeface(Typeface.MONOSPACE);
    }

    /** "12" para 12.3, "1.2" para 1.23: sin decimales cuando el número ya es grande. */
    public static String num(double v) {
        return Math.abs(v) >= 100 ? String.format(Locale.US, "%,.0f", v) : String.format(Locale.US, "%.1f", v);
    }

    /**
     * @param ax,ay  punto que se señala (el globito va arriba; si no cabe, abajo)
     * @param lines  renglones de valores; colors[i] pinta el puntito de cada uno
     */
    public void draw(Canvas c, float ax, float ay, float viewW, float viewH, String head, String[] lines, int[] colors) {
        float pad = 8 * dp, lineH = 15 * dp, dotW = 12 * dp;
        float w = title.measureText(head);
        for (String l : lines) w = Math.max(w, dotW + body.measureText(l));
        w += 2 * pad;
        float h = pad + 13 * dp + lines.length * lineH + pad - 3 * dp;
        float left = Math.max(4 * dp, Math.min(viewW - w - 4 * dp, ax - w / 2f));
        float top = ay - h - 10 * dp;
        if (top < 2 * dp) top = Math.min(viewH - h - 2 * dp, ay + 10 * dp);
        box.set(left, top, left + w, top + h);
        c.drawRoundRect(box, 8 * dp, 8 * dp, fill);
        c.drawRoundRect(box, 8 * dp, 8 * dp, stroke);
        float y = top + pad + 10 * dp;
        c.drawText(head, left + pad, y, title);
        for (int i = 0; i < lines.length; i++) {
            y += lineH;
            dot.setColor(colors[i]);
            c.drawCircle(left + pad + 3 * dp, y - 4 * dp, 3 * dp, dot);
            c.drawText(lines[i], left + pad + dotW, y, body);
        }
    }
}
