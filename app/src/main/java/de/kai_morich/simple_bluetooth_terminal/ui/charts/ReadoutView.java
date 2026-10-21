package de.kai_morich.simple_bluetooth_terminal.ui.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Lectura de la hora marcada en K5: hora, energía de 5 min y una fila por fase (kW · V · A · FP).
 * Se dibuja a mano con alto fijo según el número de filas: no depende de pesos ni de texto que se parta en líneas,
 * que en algunos equipos dejaba la tarjeta enorme y en blanco al ocultar fases.
 */
public class ReadoutView extends View {

    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint energy = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    private String timeText = "--:--", energyText = "";
    private final List<String[]> rows = new ArrayList<>(); // {fase, kW, V, A, FP}
    private final List<Integer> colors = new ArrayList<>();

    public ReadoutView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        time.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        time.setTextSize(18 * dp);
        time.setColor(0xFF161616);
        energy.setTextSize(12.5f * dp);
        energy.setColor(0xFF666666);
        energy.setTextAlign(Paint.Align.RIGHT);
        head.setTypeface(Typeface.MONOSPACE);
        head.setTextSize(11 * dp);
        head.setColor(0xFF777777);
        head.setTextAlign(Paint.Align.RIGHT);
        cell.setTypeface(Typeface.MONOSPACE);
        cell.setTextSize(12.5f * dp);
        cell.setColor(0xFF161616);
        cell.setTextAlign(Paint.Align.RIGHT);
        label.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        label.setTextSize(12.5f * dp);
    }

    /** @param phaseRows cada fila: {"L1", kW, V, A, FP}; phaseColors: el color de la etiqueta de cada una */
    public void set(String timeText, String energyText, List<String[]> phaseRows, List<Integer> phaseColors) {
        this.timeText = timeText;
        this.energyText = energyText;
        rows.clear();
        rows.addAll(phaseRows);
        colors.clear();
        colors.addAll(phaseColors);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = View.resolveSize(Math.round(280 * dp), wSpec);
        setMeasuredDimension(w, Math.round((50 + 22 * rows.size()) * dp));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        c.drawText(timeText, 0, 20 * dp, time);
        // La energía se encoge si no cabe junto a la hora
        float room = w - time.measureText(timeText) - 12 * dp;
        energy.setTextSize(12.5f * dp);
        while (energy.measureText(energyText) > room && energy.getTextSize() > 9 * dp) energy.setTextSize(energy.getTextSize() - dp * 0.5f);
        c.drawText(energyText, w, 19 * dp, energy);

        float labelW = 28 * dp, colW = (w - labelW) / 4f;
        String[] heads = {"kW", "V", "A", "FP"};
        for (int k = 0; k < 4; k++) c.drawText(heads[k], labelW + colW * (k + 1) - 2 * dp, 42 * dp, head);
        for (int r = 0; r < rows.size(); r++) {
            float y = (42 + 22 * (r + 1)) * dp;
            label.setColor(colors.get(r));
            c.drawText(rows.get(r)[0], 0, y, label);
            for (int k = 0; k < 4; k++) c.drawText(rows.get(r)[k + 1], labelW + colW * (k + 1) - 2 * dp, y, cell);
        }
    }
}
