package de.kai_morich.simple_bluetooth_terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class FasorView extends View {

    // ===== CONFIGURACIÓN ORIGINAL =====
    private float[] magnitudes = {0.5f, 0.7f, 0.6f}; // Magnitudes normalizadas 0-1
    private float[] angles = {0f, 120f, 240f}; // Ángulos en grados
    private int[] colors = {Color.RED, Color.YELLOW, Color.GREEN};

    // ===== EJES INDEPENDIENTES (2, 3 o 4 canales) =====
    private boolean useThreeAxisMode = false; // Modo nuevo activable
    private static final int MAX_CANALES = 4;
    private int channelCount = 3;
    // Los ejes se reparten en partes iguales: 3 canales -> 0/120/240, 4 -> 0/90/180/270
    private float[] axisAngles = {0f, 120f, 240f, 0f};
    private String[] axisLabels = {"L1", "L2", "L3", "L4"};

    // ✅ COLORES ACTUALIZADOS SEGÚN ESPECIFICACIONES
    private static final int[] AXIS_COLORS = {
            Color.rgb(150, 150, 150), // CH1 - Gris claro
            Color.rgb(120, 120, 120), // CH2 - Gris medio
            Color.rgb(100, 100, 100), // CH3 - Gris oscuro
            Color.rgb(85, 85, 85)     // CH4 - Gris más oscuro
    };

    // Mismo orden en ambos: canal 1..4 (igual que las columnas de la tabla)
    private static final int[] VOLTAGE_COLORS = {
            Color.WHITE,              // CH1 - Blanco
            Color.rgb(255, 60, 60),   // CH2 - Rojo
            Color.rgb(30, 100, 255),  // CH3 - Azul
            Color.rgb(60, 220, 120)   // CH4 - Verde
    };
    private static final int[] CURRENT_COLORS = {
            Color.WHITE,              // CH1 - Blanco (con contorno oscuro)
            Color.rgb(255, 60, 60),   // CH2 - Rojo
            Color.rgb(30, 100, 255),  // CH3 - Azul fuerte
            Color.rgb(60, 220, 120)   // CH4 - Verde
    };



    // Arrays para datos independientes
    private float[] individualMagnitudes = new float[MAX_CANALES];
    private float[] individualAngles = new float[MAX_CANALES];
    private String unit = "V";
    private String title = "Fasores";
    private float maxMagnitude = 100.0f; // Escala automática
    private boolean autoScale = true;
    private boolean isVoltage = false;

    // ✅ NUEVAS VARIABLES PARA AUTO-ESCALADO MEJORADO
    private static final float MIN_SCALE = 0.1f;  // Escala mínima (0.1V o 0.1A)
    private static final float SCALE_MARGIN = 1.3f; // Margen del 30% sobre el valor máximo

    // ===== PAINT OBJECTS =====
    private Paint vectorPaint;
    private Paint axisPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint backgroundPaint;

    public FasorView(Context context) {
        super(context);
        init();
    }

    public FasorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FasorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Paint para vectores (modo original)
        vectorPaint = new Paint();
        vectorPaint.setStrokeWidth(6f);
        vectorPaint.setStyle(Paint.Style.STROKE);
        vectorPaint.setAntiAlias(true);
        vectorPaint.setStrokeCap(Paint.Cap.ROUND);

        // Paint para ejes
        axisPaint = new Paint();
        axisPaint.setColor(Color.WHITE);
        axisPaint.setStrokeWidth(2f);
        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setAntiAlias(true);

        // Paint para grilla
        gridPaint = new Paint();
        gridPaint.setColor(Color.GRAY);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAlpha(100);

        // Paint para texto
        textPaint = new Paint();
        textPaint.setColor(Color.CYAN);
        textPaint.setTextSize(20f);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Paint para fondo
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    // ===== MÉTODOS PÚBLICOS ORIGINALES =====

    public void setVectors(float[] mags, float[] angs) {
        if (mags.length >= 3 && angs.length >= 3) {
            System.arraycopy(mags, 0, this.magnitudes, 0, 3);
            System.arraycopy(angs, 0, this.angles, 0, 3);
            invalidate();
        }
    }

    public void setVoltageMode(boolean voltage) {
        this.isVoltage = voltage;
        invalidate();
    }

    // ===== ✅ MÉTODOS REQUERIDOS POR FasoresActivity =====

    /** Cuántos fasores se dibujan (2, 3 o 4). Reparte los ejes en partes iguales. */
    public void setChannelCount(int count) {
        count = Math.max(1, Math.min(MAX_CANALES, count));
        if (count == channelCount) return;
        channelCount = count;
        for (int i = 0; i < MAX_CANALES; i++) {
            axisAngles[i] = 360f * i / channelCount;
        }
        invalidate();
    }

    /** Magnitudes y ángulos de cada canal; se leen los primeros channelCount. */
    public void setValues(float[] mags, float[] angs) {
        for (int i = 0; i < MAX_CANALES; i++) {
            individualMagnitudes[i] = (mags != null && i < mags.length) ? mags[i] : 0f;
            individualAngles[i]     = (angs != null && i < angs.length) ? angs[i] : 0f;
        }
        invalidate();
    }

    // ===== NUEVOS MÉTODOS PÚBLICOS PARA 3 EJES =====

    /**
     * Activa el modo de 3 ejes independientes
     */
    public void setThreeAxisMode(boolean enabled) {
        this.useThreeAxisMode = enabled;
        invalidate();
    }

    /**
     * Establece unidad y título
     */
    public void setUnit(String unit) {
        this.unit = unit;
        invalidate();
    }

    public void setTitle(String title) {
        this.title = title;
        invalidate();
    }

    /**
     * Habilita/deshabilita auto-escalado
     */
    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
    }

    /**
     * Establece escala manual
     */
    public void setScale(float maxMagnitude) {
        this.autoScale = false;
        this.maxMagnitude = maxMagnitude;
        invalidate();
    }

    // ===== MÉTODO onDraw PRINCIPAL =====

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (useThreeAxisMode) {
            drawThreeAxisMode(canvas);
        } else {
            drawOriginalMode(canvas);
        }
    }

    // ===== MODO ORIGINAL (COMPATIBILIDAD) =====

    private void drawOriginalMode(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = Math.min(width, height) / 2f - 50f;

        // Fondo
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        // Grilla circular básica
        for (int i = 1; i <= 3; i++) {
            float r = radius * i / 3f;
            canvas.drawCircle(centerX, centerY, r, gridPaint);
        }

        // Ejes X e Y
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, axisPaint);
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, axisPaint);

        // Dibujar vectores originales
        for (int i = 0; i < Math.min(magnitudes.length, colors.length); i++) {
            if (magnitudes[i] > 0) {
                vectorPaint.setColor(colors[i]);

                float angleRad = (float) Math.toRadians(angles[i] - 90); // -90 para que 0° sea arriba
                float vectorLength = radius * magnitudes[i];
                float endX = centerX + vectorLength * (float) Math.cos(angleRad);
                float endY = centerY + vectorLength * (float) Math.sin(angleRad);

                canvas.drawLine(centerX, centerY, endX, endY, vectorPaint);

                // Punto en la punta
                Paint dotPaint = new Paint();
                dotPaint.setColor(colors[i]);
                dotPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(endX, endY, 8f, dotPaint);
            }
        }
    }

    // ===== MODO 3 EJES INDEPENDIENTES MEJORADO =====

    private void drawThreeAxisMode(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float centerX = width / 2f;
        float centerY = height / 2f;
        // ✅ RADIO MÁS GRANDE - REDUCIR MARGEN
        float radius = Math.min(width, height) / 2f - 60f; // Era 100f, ahora 60f

        // ✅ Auto-escalar si está habilitado (MEJORADO)
        if (autoScale) {
            updateScaleImproved();
        }

        // 1. Fondo con gradiente sutil
        drawEnhancedBackground(canvas, width, height);

        // 2. Título mejorado
        drawEnhancedTitle(canvas, centerX);

        // 3. Grilla circular mejorada
        drawEnhancedGrid(canvas, centerX, centerY, radius);

        // 4. Los 3 ejes fijos separados correctamente
        drawThreeAxesCorrected(canvas, centerX, centerY, radius);

        // 5. Centro (punto de referencia)
        drawCenterPoint(canvas, centerX, centerY);

        // 6. ✅ FASORES CORREGIDOS - CADA UNO CON ÁNGULOS RELATIVOS
        drawPhasorsOnAxesCorrected(canvas, centerX, centerY, radius);

        // 7. Etiquetas de valores mejoradas
        drawEnhancedValueLabels(canvas);

        // 8. Escala mejorada
        drawEnhancedScale(canvas);
    }

    // ===== MÉTODOS DE DIBUJO MEJORADOS =====

    private void drawEnhancedBackground(Canvas canvas, int width, int height) {
        // Fondo negro con gradiente sutil
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        // Gradiente radial sutil desde el centro
        Paint gradientPaint = new Paint();
        gradientPaint.setShader(new LinearGradient(0, 0, 0, height,
                Color.rgb(20, 20, 20), Color.rgb(5, 5, 5), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, gradientPaint);
    }

    private void drawEnhancedTitle(Canvas canvas, float centerX) {
        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(32f); // Más grande
        titlePaint.setAntiAlias(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);
        titlePaint.setShadowLayer(3f, 2f, 2f, Color.GRAY); // Sombra
        canvas.drawText(title, centerX, 45f, titlePaint);
    }

    private void drawEnhancedGrid(Canvas canvas, float centerX, float centerY, float radius) {
        // Círculos concéntricos con diferentes intensidades
        Paint[] gridPaints = new Paint[4];
        for (int i = 0; i < 4; i++) {
            gridPaints[i] = new Paint();
            gridPaints[i].setColor(Color.GRAY);
            gridPaints[i].setStrokeWidth(1f);
            gridPaints[i].setStyle(Paint.Style.STROKE);
            gridPaints[i].setAntiAlias(true);
            gridPaints[i].setAlpha(80 - i * 15); // Más tenue hacia el exterior
        }

        for (int i = 1; i <= 4; i++) {
            float circleRadius = radius * i / 4f;
            canvas.drawCircle(centerX, centerY, circleRadius, gridPaints[i-1]);
        }
    }

    private void drawThreeAxesCorrected(Canvas canvas, float centerX, float centerY, float radius) {
        Paint[] axisPaints = new Paint[channelCount];

        for (int i = 0; i < channelCount; i++) {
            axisPaints[i] = new Paint();
            axisPaints[i].setColor(AXIS_COLORS[i]);
            axisPaints[i].setStrokeWidth(4f); // Más grueso
            axisPaints[i].setStyle(Paint.Style.STROKE);
            axisPaints[i].setAntiAlias(true);
            axisPaints[i].setShadowLayer(2f, 1f, 1f, Color.BLACK); // Sombra

            // ✅ ÁNGULOS CORRECTOS: 0°, 120°, 240°
            float axisAngleRad = (float) Math.toRadians(axisAngles[i] - 90); // -90 para que 0° esté arriba

            // Calcular puntos del eje (línea completa)
            float endX = centerX + radius * (float) Math.cos(axisAngleRad);
            float endY = centerY + radius * (float) Math.sin(axisAngleRad);
            float startX = centerX - radius * (float) Math.cos(axisAngleRad);
            float startY = centerY - radius * (float) Math.sin(axisAngleRad);

            // Dibujar eje completo
            canvas.drawLine(startX, startY, endX, endY, axisPaints[i]);

            // Etiqueta del eje mejorada
            Paint labelPaint = new Paint();
            labelPaint.setColor(AXIS_COLORS[i]);
            labelPaint.setTextSize(22f); // Más grande
            labelPaint.setAntiAlias(true);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setFakeBoldText(true);

            float labelX = centerX + (radius + 40) * (float) Math.cos(axisAngleRad);
            float labelY = centerY + (radius + 40) * (float) Math.sin(axisAngleRad) + 8;
            canvas.drawText(axisLabels[i] + " (" + (int)axisAngles[i] + "°)", labelX, labelY, labelPaint);
        }
    }

    private void drawCenterPoint(Canvas canvas, float centerX, float centerY) {
        Paint centerPaint = new Paint();
        centerPaint.setColor(Color.WHITE);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setShadowLayer(3f, 0f, 0f, Color.CYAN); // Glow effect
        canvas.drawCircle(centerX, centerY, 8f, centerPaint);
    }

    // ✅ FASORES CON ÁNGULOS RELATIVOS A SU BASE (0°, 120°, 240°) Y COLORES ACTUALIZADOS - CORREGIDO
    private void drawPhasorsOnAxesCorrected(Canvas canvas, float centerX, float centerY, float radius) {
        // ✅ SELECCIONAR ARRAY DE COLORES SEGÚN EL TIPO DE FASOR
        int[] colors = isVoltage ? VOLTAGE_COLORS : CURRENT_COLORS;

        for (int i = 0; i < channelCount; i++) {
            float rawMagnitude = individualMagnitudes[i];
            boolean isNegative = rawMagnitude < 0;
            float absMagnitude = Math.abs(rawMagnitude);

            if (absMagnitude < 0.001f) continue;

            float baseAngle = axisAngles[i];
            float deviceAngle = individualAngles[i];
            float angleOffset = isNegative ? 180f : 0f;
            float normalizedDeviceAngle = deviceAngle % 360f;
            if (normalizedDeviceAngle < 0) normalizedDeviceAngle += 360f;
            float finalAngle = baseAngle + normalizedDeviceAngle + angleOffset;

            float phasorAngleRad = (float) Math.toRadians(finalAngle - 90);
            float normalizedMagnitude = absMagnitude / maxMagnitude;
            if (normalizedMagnitude < 0.05f && normalizedMagnitude > 0) {
                normalizedMagnitude = 0.05f;
            }
            float phasorLength = radius * normalizedMagnitude;

            float phasorX = centerX + phasorLength * (float) Math.cos(phasorAngleRad);
            float phasorY = centerY + phasorLength * (float) Math.sin(phasorAngleRad);

            // ✅ USAR COLOR DEL ARRAY CORRECTO
            if (i == 0) { // Canal 1 - Blanco con contorno
                Paint outlinePaint = new Paint();
                outlinePaint.setColor(Color.rgb(50, 50, 50));
                outlinePaint.setStrokeWidth(12f);
                outlinePaint.setStyle(Paint.Style.STROKE);
                outlinePaint.setAntiAlias(true);
                outlinePaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(centerX, centerY, phasorX, phasorY, outlinePaint);

                Paint phasorPaint = new Paint();
                phasorPaint.setColor(Color.WHITE);
                phasorPaint.setStrokeWidth(8f);
                phasorPaint.setStyle(Paint.Style.STROKE);
                phasorPaint.setAntiAlias(true);
                phasorPaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(centerX, centerY, phasorX, phasorY, phasorPaint);

                Paint centerLinePaint = new Paint();
                centerLinePaint.setColor(Color.BLACK);
                centerLinePaint.setStrokeWidth(2f);
                centerLinePaint.setStyle(Paint.Style.STROKE);
                centerLinePaint.setAntiAlias(true);
                centerLinePaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(centerX, centerY, phasorX, phasorY, centerLinePaint);

                Paint outlineDotPaint = new Paint();
                outlineDotPaint.setColor(Color.rgb(50, 50, 50));
                outlineDotPaint.setStyle(Paint.Style.FILL);
                outlineDotPaint.setAntiAlias(true);
                canvas.drawCircle(phasorX, phasorY, 12f, outlineDotPaint);

                Paint dotPaint = new Paint();
                dotPaint.setColor(Color.WHITE);
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setAntiAlias(true);
                canvas.drawCircle(phasorX, phasorY, 10f, dotPaint);

                Paint innerDotPaint = new Paint();
                innerDotPaint.setColor(Color.BLACK);
                innerDotPaint.setStyle(Paint.Style.FILL);
                innerDotPaint.setAntiAlias(true);
                canvas.drawCircle(phasorX, phasorY, 3f, innerDotPaint);

            } else { // Canal 2 y 3 - Usar color del array correcto
                Paint phasorPaint = new Paint();
                phasorPaint.setColor(colors[i]); // ✅ USAR ARRAY CORRECTO
                phasorPaint.setStrokeWidth(8f);
                phasorPaint.setStyle(Paint.Style.STROKE);
                phasorPaint.setAntiAlias(true);
                phasorPaint.setStrokeCap(Paint.Cap.ROUND);
                phasorPaint.setShadowLayer(4f, 0f, 0f, colors[i]);

                canvas.drawLine(centerX, centerY, phasorX, phasorY, phasorPaint);

                Paint dotPaint = new Paint();
                dotPaint.setColor(colors[i]); // ✅ USAR ARRAY CORRECTO
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setShadowLayer(6f, 0f, 0f, colors[i]);
                canvas.drawCircle(phasorX, phasorY, 10f, dotPaint);

                Paint innerDotPaint = new Paint();
                innerDotPaint.setColor(Color.WHITE);
                innerDotPaint.setStyle(Paint.Style.FILL);
                innerDotPaint.setAntiAlias(true);
                canvas.drawCircle(phasorX, phasorY, 4f, innerDotPaint);
            }

            drawProjectionLineToBase(canvas, centerX, centerY, phasorX, phasorY, i, radius);

            System.out.printf("Canal %d: Base=%.0f°, Dispositivo=%.1f°, Inversión=%s, Final=%.1f°, Mag=%.3f%s, Color=%s%n",
                    i+1, baseAngle, deviceAngle, isNegative ? "SÍ(+180°)" : "NO",
                    finalAngle, rawMagnitude, unit,
                    (i == 0 ? "BLANCO" : (colors[i] == Color.RED ? "ROJO" : "AZUL")));
        }
    }

    // ===== MÉTODO PARA LÍNEA DE PROYECCIÓN A LA BASE =====
    private void drawProjectionLineToBase(Canvas canvas, float centerX, float centerY,
                                          float phasorX, float phasorY, int channelIndex, float radius) {
        // Línea punteada desde la punta del fasor hacia su eje base
        float baseAngleRad = (float) Math.toRadians(axisAngles[channelIndex] - 90);
        float baseX = centerX + radius * 0.9f * (float) Math.cos(baseAngleRad);
        float baseY = centerY + radius * 0.9f * (float) Math.sin(baseAngleRad);

        Paint projectionPaint = new Paint();
        projectionPaint.setColor(CURRENT_COLORS[channelIndex]);
        projectionPaint.setStrokeWidth(2f);
        projectionPaint.setStyle(Paint.Style.STROKE);
        projectionPaint.setAntiAlias(true);
        projectionPaint.setAlpha(100);
        projectionPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8, 8}, 0));

        canvas.drawLine(phasorX, phasorY, baseX, baseY, projectionPaint);
    }

    // ✅ CAMBIO SIMPLE: Solo mostrar ángulos negativos correctamente
    private void drawEnhancedValueLabels(Canvas canvas) {
        float startY = getHeight() - (channelCount * 22f + 34f);

        // Fondo con gradiente más compacto
        Paint bgPaint = new Paint();
        bgPaint.setShader(new LinearGradient(10f, startY - 30f, getWidth() - 10f, getHeight() - 10f,
                Color.argb(200, 0, 0, 0), Color.argb(150, 20, 20, 20), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(10f, startY - 30f, getWidth() - 10f, getHeight() - 10f, 8f, 8f, bgPaint);

        for (int i = 0; i < channelCount; i++) {
            float y = startY + i * 22f;

            // Color del texto igual al fasor
            Paint labelPaint = new Paint();
            labelPaint.setColor(CURRENT_COLORS[i]);
            labelPaint.setTextSize(14f);
            labelPaint.setAntiAlias(true);
            labelPaint.setFakeBoldText(true);
            labelPaint.setShadowLayer(1f, 0.5f, 0.5f, Color.BLACK);

            // ✅ SIMPLE: Normalizar ángulo a -180° a +180°
            float deviceAngle = individualAngles[i];

            // Normalizar a rango -180° a +180°
            while (deviceAngle > 180f) deviceAngle -= 360f;
            while (deviceAngle < -180f) deviceAngle += 360f;

            // ✅ FORMATO DINÁMICO: Más decimales para valores pequeños
            String magnitudeStr;
            if (individualMagnitudes[i] < 1.0f) {
                magnitudeStr = String.format("%.3f", individualMagnitudes[i]);
            } else if (individualMagnitudes[i] < 10.0f) {
                magnitudeStr = String.format("%.2f", individualMagnitudes[i]);
            } else {
                magnitudeStr = String.format("%.1f", individualMagnitudes[i]);
            }

            String label = String.format("%s: %s%s ∠%.1f°",
                    axisLabels[i], magnitudeStr, unit, deviceAngle);
            canvas.drawText(label, 15f, y, labelPaint);
        }
    }

    private void drawEnhancedScale(Canvas canvas) {
        // ✅ Escala con formato dinámico
        String scaleText;
        if (maxMagnitude < 1.0f) {
            scaleText = String.format("Escala: %.3f %s", maxMagnitude, unit);
        } else if (maxMagnitude < 10.0f) {
            scaleText = String.format("Escala: %.2f %s", maxMagnitude, unit);
        } else {
            scaleText = String.format("Escala: %.1f %s", maxMagnitude, unit);
        }

        Paint scalePaint = new Paint();
        scalePaint.setColor(Color.YELLOW);
        scalePaint.setTextSize(18f);
        scalePaint.setAntiAlias(true);
        scalePaint.setTextAlign(Paint.Align.RIGHT);
        scalePaint.setFakeBoldText(true);
        scalePaint.setShadowLayer(2f, 1f, 1f, Color.BLACK);
        canvas.drawText(scaleText, getWidth() - 25f, 80f, scalePaint);
    }

    // ===== ✅ MÉTODO DE AUTO-ESCALADO MEJORADO =====
    private void updateScaleImproved() {
        // ✅ Encontrar la magnitud máxima ABSOLUTA
        float max = 0;
        for (int i = 0; i < channelCount; i++) {
            float absMag = Math.abs(individualMagnitudes[i]);
            if (absMag > max) max = absMag;
        }

        if (max < 0.001f) {
            maxMagnitude = MIN_SCALE;
            return;
        }

        // ...existing code...
        float targetScale = max * SCALE_MARGIN;

        // ...existing code... (resto del método igual)
        if (targetScale < 0.1f) {
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{0.01f, 0.02f, 0.05f, 0.1f});
        } else if (targetScale < 1.0f) {
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{0.1f, 0.2f, 0.5f, 1.0f});
        } else if (targetScale < 10f) {
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{1f, 2f, 5f, 10f});
        } else if (targetScale < 50f) {
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{10f, 20f, 50f});
        } else if (targetScale < 100f) {
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{50f, 100f});
        } else if (targetScale < 250f) {
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{100f, 150f, 200f, 250f});
        } else if (targetScale < 500f) {
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{250f, 300f, 400f, 500f});
        } else if (targetScale < 1000f) {
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{500f, 750f, 1000f});
        } else {
            maxMagnitude = (float)(Math.ceil(targetScale / 100) * 100);
        }

        if (maxMagnitude < MIN_SCALE) {
            maxMagnitude = MIN_SCALE;
        }

        System.out.printf("AUTO-SCALE: max=%.3f, target=%.3f, final=%.3f %s%n",
                max, targetScale, maxMagnitude, unit);
    }

    /**
     * ✅ Redondea a un "número bonito" del array proporcionado
     * Encuentra el primer número en el array que sea >= al valor target
     */
    private float roundToNiceNumber(float target, float[] niceNumbers) {
        for (float nice : niceNumbers) {
            if (nice >= target) {
                return nice;
            }
        }
        // Si ninguno es suficiente, devolver el último
        return niceNumbers[niceNumbers.length - 1];
    }

    // ===== ✅ MÉTODO ANTIGUO MANTENIDO POR COMPATIBILIDAD =====
    private void updateScale() {
        updateScaleImproved(); // Redirigir al método mejorado
    }
}