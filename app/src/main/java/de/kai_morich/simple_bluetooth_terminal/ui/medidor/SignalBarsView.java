package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** 4 barras de señal de 4dp de ancho y 5/9/13/18dp de alto: activas en color, el resto en gris. */
public class SignalBarsView extends View {

    private static final int[] HEIGHTS = {5, 9, 13, 18};

    private final Paint paint = new Paint();
    private final float dp;
    private int active;
    private int color = 0xFF1F9D4C;

    public SignalBarsView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
    }

    public void set(int activeBars, int activeColor) {
        active = activeBars;
        color = activeColor;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) (22 * dp), (int) (18 * dp)); // 4×4 + 3×2
    }

    @Override
    protected void onDraw(Canvas c) {
        for (int i = 0; i < 4; i++) {
            paint.setColor(i < active ? color : 0xFFDDDDDD);
            float left = i * 6 * dp, hgt = HEIGHTS[i] * dp;
            c.drawRect(left, getHeight() - hgt, left + 4 * dp, getHeight(), paint);
        }
    }
}
