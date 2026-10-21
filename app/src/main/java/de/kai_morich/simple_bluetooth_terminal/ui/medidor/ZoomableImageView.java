package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import android.annotation.SuppressLint;
import android.widget.ImageView;

/** Imagen con zoom por pellizco y arrastre (diagrama de conexión a pantalla completa). */
@SuppressLint("AppCompatCustomView")
public class ZoomableImageView extends ImageView {
    private final android.graphics.Matrix matrix = new android.graphics.Matrix();
    private final android.graphics.Matrix savedMatrix = new android.graphics.Matrix();

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 8.0f;

    private float startX = 0f, startY = 0f;
    private float midX = 0f, midY = 0f;
    private float lastSpacing = 0f;

    private static final int NONE = 0, DRAG = 1, ZOOM = 2;
    private int mode = NONE;

    public ZoomableImageView(android.content.Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);

        setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {

                case android.view.MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix);
                    startX = event.getX();
                    startY = event.getY();
                    mode = DRAG;
                    break;

                case android.view.MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        lastSpacing = getSpacing(event);
                        midX = (event.getX(0) + event.getX(1)) / 2f;
                        midY = (event.getY(0) + event.getY(1)) / 2f;
                        savedMatrix.set(matrix);
                        mode = ZOOM;
                    }
                    break;

                case android.view.MotionEvent.ACTION_MOVE:
                    if (mode == ZOOM && event.getPointerCount() >= 2) {
                        float newSpacing = getSpacing(event);
                        float spacingDiff = newSpacing - lastSpacing;

                        //  Factor muy gradual
                        if (Math.abs(spacingDiff) > 1f) {
                            float[] values = new float[9];
                            matrix.getValues(values);
                            float currentScale = values[android.graphics.Matrix.MSCALE_X];

                            //  Zoom gradual: cada pixel = muy poco cambio
                            float scaleDelta = 1f + (spacingDiff * 0.005f);

                            float newScale = currentScale * scaleDelta;

                            //  Limitar escala
                            if (newScale < MIN_SCALE) scaleDelta = MIN_SCALE / currentScale;
                            else if (newScale > MAX_SCALE) scaleDelta = MAX_SCALE / currentScale;

                            matrix.postScale(scaleDelta, scaleDelta, midX, midY);
                            fixTranslation();
                            setImageMatrix(matrix);
                            savedMatrix.set(matrix);
                            lastSpacing = newSpacing; //  frame a frame
                        }

                    } else if (mode == DRAG && event.getPointerCount() == 1) {
                        float dx = event.getX() - startX;
                        float dy = event.getY() - startY;

                        if (Math.abs(dx) > 1f || Math.abs(dy) > 1f) {
                            matrix.set(savedMatrix);
                            matrix.postTranslate(dx, dy);
                            fixTranslation();
                            setImageMatrix(matrix);
                        }
                    }
                    break;

                case android.view.MotionEvent.ACTION_POINTER_UP:
                    savedMatrix.set(matrix);
                    if (event.getPointerCount() == 2) {
                        int remainingIndex = (event.getActionIndex() == 0) ? 1 : 0;
                        startX = event.getX(remainingIndex);
                        startY = event.getY(remainingIndex);
                        mode = DRAG;
                    } else {
                        mode = NONE;
                    }
                    break;

                case android.view.MotionEvent.ACTION_UP:
                    mode = NONE;
                    break;

                case android.view.MotionEvent.ACTION_CANCEL:
                    mode = NONE;
                    break;
            }
            return true;
        });
    }

    public void initMatrix() {
        post(() -> {
            android.graphics.drawable.Drawable d = getDrawable();
            if (d == null) return;
            float vW = getWidth(), vH = getHeight();
            if (vW == 0 || vH == 0) return;
            float dW = d.getIntrinsicWidth(), dH = d.getIntrinsicHeight();
            if (dW == 0 || dH == 0) return;

            //  Al abrir: escala 1:1
            float scale = 1.0f;
            float dx = (vW - dW * scale) / 2f;
            float dy = (vH - dH * scale) / 2f;
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(dx, dy);
            savedMatrix.set(matrix);
            setImageMatrix(matrix);
        });
    }

    public void resetMatrix() {
        post(() -> {
            android.graphics.drawable.Drawable d = getDrawable();
            if (d == null) return;
            float vW = getWidth(), vH = getHeight();
            if (vW == 0 || vH == 0) return;
            float dW = d.getIntrinsicWidth(), dH = d.getIntrinsicHeight();
            if (dW == 0 || dH == 0) return;

            //  Reset: ajustar para que quepa completa
            float scale = Math.min(vW / dW, vH / dH);
            float dx = (vW - dW * scale) / 2f;
            float dy = (vH - dH * scale) / 2f;
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(dx, dy);
            savedMatrix.set(matrix);
            setImageMatrix(matrix);
        });
    }
    private float getSpacing(android.view.MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void fixTranslation() {
        float[] values = new float[9];
        matrix.getValues(values);
        float currentScale = values[android.graphics.Matrix.MSCALE_X];
        float transX = values[android.graphics.Matrix.MTRANS_X];
        float transY = values[android.graphics.Matrix.MTRANS_Y];

        android.graphics.drawable.Drawable d = getDrawable();
        if (d == null) return;

        float vW = getWidth(), vH = getHeight();
        float scaledW = d.getIntrinsicWidth() * currentScale;
        float scaledH = d.getIntrinsicHeight() * currentScale;

        float fixX = 0f, fixY = 0f;

        if (scaledW <= vW) {
            fixX = (vW - scaledW) / 2f - transX;
        } else {
            if (transX > 0) fixX = -transX;
            else if (transX + scaledW < vW) fixX = vW - (transX + scaledW);
        }

        if (scaledH <= vH) {
            fixY = (vH - scaledH) / 2f - transY;
        } else {
            if (transY > 0) fixY = -transY;
            else if (transY + scaledH < vH) fixY = vH - (transY + scaledH);
        }

        matrix.postTranslate(fixX, fixY);
    }
}
