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

    // ===== NUEVAS VARIABLES PARA 3 EJES =====
    private boolean useThreeAxisMode = false; // Modo nuevo activable
    private static final float[] AXIS_ANGLES = {0f, 120f, 240f}; // Ejes fijos
    private static final String[] AXIS_LABELS = {"L1", "L2", "L3"};

    // ✅ COLORES ACTUALIZADOS SEGÚN ESPECIFICACIONES
    private static final int[] AXIS_COLORS = {
            Color.rgb(150, 150, 150), // CH1 - Gris claro
            Color.rgb(120, 120, 120), // CH2 - Gris medio
            Color.rgb(100, 100, 100)  // CH3 - Gris oscuro
    };
    private static final int[] PHASOR_COLORS = {
            Color.WHITE,              // CH1 - Blanco (con contorno oscuro)
            Color.rgb(255, 60, 60),   // CH2 - Rojo
            Color.rgb(30, 100, 255)   // CH3 - Azul fuerte
    };

    // Arrays para datos independientes
    private float[] individualMagnitudes = {0.0f, 0.0f, 0.0f};
    private float[] individualAngles = {0.0f, 120.0f, 240.0f};
    private String unit = "V";
    private String title = "Fasores";
    private float maxMagnitude = 100.0f; // Escala automática
    private boolean autoScale = true;

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

    // ===== ✅ MÉTODOS REQUERIDOS POR FasoresActivity =====

    /**
     * Establece las magnitudes de los 3 fasores
     */
    public void setMagnitudes(float mag1, float mag2, float mag3) {
        this.individualMagnitudes[0] = mag1;
        this.individualMagnitudes[1] = mag2;
        this.individualMagnitudes[2] = mag3;
        invalidate(); // Redibujar
    }

    /**
     * Establece los ángulos de los 3 fasores
     */
    public void setAngles(float ang1, float ang2, float ang3) {
        this.individualAngles[0] = ang1;
        this.individualAngles[1] = ang2;
        this.individualAngles[2] = ang3;
        invalidate(); // Redibujar
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
     * Establece valores para modo 3 ejes
     */
    public void setPhasorValues(float[] magnitudes, float[] angles) {
        if (magnitudes.length >= 3 && angles.length >= 3) {
            System.arraycopy(magnitudes, 0, this.individualMagnitudes, 0, 3);
            System.arraycopy(angles, 0, this.individualAngles, 0, 3);
            invalidate();
        }
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
        Paint[] axisPaints = new Paint[3];

        for (int i = 0; i < 3; i++) {
            axisPaints[i] = new Paint();
            axisPaints[i].setColor(AXIS_COLORS[i]);
            axisPaints[i].setStrokeWidth(4f); // Más grueso
            axisPaints[i].setStyle(Paint.Style.STROKE);
            axisPaints[i].setAntiAlias(true);
            axisPaints[i].setShadowLayer(2f, 1f, 1f, Color.BLACK); // Sombra

            // ✅ ÁNGULOS CORRECTOS: 0°, 120°, 240°
            float axisAngleRad = (float) Math.toRadians(AXIS_ANGLES[i] - 90); // -90 para que 0° esté arriba

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
            canvas.drawText(AXIS_LABELS[i] + " (" + (int)AXIS_ANGLES[i] + "°)", labelX, labelY, labelPaint);
        }
    }

    private void drawCenterPoint(Canvas canvas, float centerX, float centerY) {
        Paint centerPaint = new Paint();
        centerPaint.setColor(Color.WHITE);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setShadowLayer(3f, 0f, 0f, Color.CYAN); // Glow effect
        canvas.drawCircle(centerX, centerY, 8f, centerPaint);
    }

    // ✅ FASORES CON ÁNGULOS RELATIVOS A SU BASE (0°, 120°, 240°) Y COLORES ACTUALIZADOS
    private void drawPhasorsOnAxesCorrected(Canvas canvas, float centerX, float centerY, float radius) {
        for (int i = 0; i < 3; i++) {
            // ✅ MOSTRAR INCLUSO MAGNITUDES PEQUEÑAS (>=0.001)
            if (individualMagnitudes[i] < 0.001f) continue; // Solo omitir si es prácticamente cero

            // ✅ CALCULAR ÁNGULO RELATIVO A LA BASE DEL CANAL
            float baseAngle = AXIS_ANGLES[i];  // Base: 0°, 120°, 240°
            float deviceAngle = individualAngles[i];  // Ángulo del dispositivo

            // ✅ NORMALIZAR EL ÁNGULO DEL DISPOSITIVO (0-360°)
            float normalizedDeviceAngle = deviceAngle % 360f;
            if (normalizedDeviceAngle < 0) normalizedDeviceAngle += 360f;

            // ✅ CALCULAR ÁNGULO FINAL = BASE + ÁNGULO_RELATIVO
            float relativeAngle = normalizedDeviceAngle;
            float finalAngle = baseAngle + relativeAngle;

            // ✅ DIBUJAR FASOR EN SU POSICIÓN FINAL
            float phasorAngleRad = (float) Math.toRadians(finalAngle - 90); // -90 para que 0° esté arriba

            // ✅ CALCULAR LONGITUD NORMALIZADA - SIEMPRE VISIBLE
            float normalizedMagnitude = individualMagnitudes[i] / maxMagnitude;
            // Asegurar longitud mínima visible (5% del radio)
            if (normalizedMagnitude < 0.05f && normalizedMagnitude > 0) {
                normalizedMagnitude = 0.05f;
            }
            float phasorLength = radius * normalizedMagnitude;

            // Calcular posición final del fasor
            float phasorX = centerX + phasorLength * (float) Math.cos(phasorAngleRad);
            float phasorY = centerY + phasorLength * (float) Math.sin(phasorAngleRad);

            // ✅ APLICAR COLORES SEGÚN ESPECIFICACIONES
            if (i == 0) { // Canal 1 - Blanco con contorno oscuro y línea central negra
                // Primero dibujar contorno oscuro (más grueso)
                Paint outlinePaint = new Paint();
                outlinePaint.setColor(Color.rgb(50, 50, 50)); // Gris muy oscuro
                outlinePaint.setStrokeWidth(12f); // Más grueso que el fasor principal
                outlinePaint.setStyle(Paint.Style.STROKE);
                outlinePaint.setAntiAlias(true);
                outlinePaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(centerX, centerY, phasorX, phasorY, outlinePaint);

                // Luego dibujar línea blanca encima
                Paint phasorPaint = new Paint();
                phasorPaint.setColor(Color.WHITE);
                phasorPaint.setStrokeWidth(8f);
                phasorPaint.setStyle(Paint.Style.STROKE);
                phasorPaint.setAntiAlias(true);
                phasorPaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(centerX, centerY, phasorX, phasorY, phasorPaint);

                // ✅ AGREGAR LÍNEA NEGRA CENTRAL DELGADA PARA IDENTIFICACIÓN
                Paint centerLinePaint = new Paint();
                centerLinePaint.setColor(Color.BLACK);
                centerLinePaint.setStrokeWidth(2f); // Línea delgada en el centro
                centerLinePaint.setStyle(Paint.Style.STROKE);
                centerLinePaint.setAntiAlias(true);
                centerLinePaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(centerX, centerY, phasorX, phasorY, centerLinePaint);

                // Punta con contorno oscuro
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

                // Círculo interior negro para identificación
                Paint innerDotPaint = new Paint();
                innerDotPaint.setColor(Color.BLACK);
                innerDotPaint.setStyle(Paint.Style.FILL);
                innerDotPaint.setAntiAlias(true);
                canvas.drawCircle(phasorX, phasorY, 3f, innerDotPaint);

            } else { // Canal 2 (rojo) y Canal 3 (azul fuerte)
                // Paint para el fasor
                Paint phasorPaint = new Paint();
                phasorPaint.setColor(PHASOR_COLORS[i]);
                phasorPaint.setStrokeWidth(8f);
                phasorPaint.setStyle(Paint.Style.STROKE);
                phasorPaint.setAntiAlias(true);
                phasorPaint.setStrokeCap(Paint.Cap.ROUND);
                phasorPaint.setShadowLayer(4f, 0f, 0f, PHASOR_COLORS[i]);

                // Dibujar fasor
                canvas.drawLine(centerX, centerY, phasorX, phasorY, phasorPaint);

                // Dibujar punta del fasor (círculo con glow)
                Paint dotPaint = new Paint();
                dotPaint.setColor(PHASOR_COLORS[i]);
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setShadowLayer(6f, 0f, 0f, PHASOR_COLORS[i]);
                canvas.drawCircle(phasorX, phasorY, 10f, dotPaint);

                // Círculo interior blanco para contraste
                Paint innerDotPaint = new Paint();
                innerDotPaint.setColor(Color.WHITE);
                innerDotPaint.setStyle(Paint.Style.FILL);
                innerDotPaint.setAntiAlias(true);
                canvas.drawCircle(phasorX, phasorY, 4f, innerDotPaint);
            }

            // ✅ LÍNEA PUNTEADA DESDE EL FASOR HACIA SU EJE BASE
            drawProjectionLineToBase(canvas, centerX, centerY, phasorX, phasorY, i, radius);

            // ✅ DEBUG: Mostrar ángulos calculados
            System.out.printf("Canal %d: Base=%.0f°, Dispositivo=%.1f°, Relativo=%.1f°, Final=%.1f°, Mag=%.3f%n",
                    i+1, baseAngle, deviceAngle, relativeAngle, finalAngle, individualMagnitudes[i]);
        }
    }

    // ===== MÉTODO PARA LÍNEA DE PROYECCIÓN A LA BASE =====
    private void drawProjectionLineToBase(Canvas canvas, float centerX, float centerY,
                                          float phasorX, float phasorY, int channelIndex, float radius) {
        // Línea punteada desde la punta del fasor hacia su eje base
        float baseAngleRad = (float) Math.toRadians(AXIS_ANGLES[channelIndex] - 90);
        float baseX = centerX + radius * 0.9f * (float) Math.cos(baseAngleRad);
        float baseY = centerY + radius * 0.9f * (float) Math.sin(baseAngleRad);

        Paint projectionPaint = new Paint();
        projectionPaint.setColor(PHASOR_COLORS[channelIndex]);
        projectionPaint.setStrokeWidth(2f);
        projectionPaint.setStyle(Paint.Style.STROKE);
        projectionPaint.setAntiAlias(true);
        projectionPaint.setAlpha(100);
        projectionPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8, 8}, 0));

        canvas.drawLine(phasorX, phasorY, baseX, baseY, projectionPaint);
    }

    // ✅ CAMBIO SIMPLE: Solo mostrar ángulos negativos correctamente
    private void drawEnhancedValueLabels(Canvas canvas) {
        float startY = getHeight() - 100f;

        // Fondo con gradiente más compacto
        Paint bgPaint = new Paint();
        bgPaint.setShader(new LinearGradient(10f, startY - 30f, getWidth() - 10f, getHeight() - 10f,
                Color.argb(200, 0, 0, 0), Color.argb(150, 20, 20, 20), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(10f, startY - 30f, getWidth() - 10f, getHeight() - 10f, 8f, 8f, bgPaint);

        for (int i = 0; i < 3; i++) {
            float y = startY + i * 22f;

            // Color del texto igual al fasor
            Paint labelPaint = new Paint();
            labelPaint.setColor(PHASOR_COLORS[i]);
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
                    AXIS_LABELS[i], magnitudeStr, unit, deviceAngle);
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
        // Encontrar la magnitud máxima
        float max = 0;
        for (float mag : individualMagnitudes) {
            if (mag > max) max = mag;
        }

        if (max < 0.001f) {
            // Si todos los valores son prácticamente cero
            maxMagnitude = MIN_SCALE;
            return;
        }

        // ✅ APLICAR MARGEN
        float targetScale = max * SCALE_MARGIN;

        // ✅ ESCALAS ADAPTATIVAS SEGÚN EL RANGO
        if (targetScale < 0.1f) {
            // Rango muy pequeño (miliamperes, milivoltios)
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{0.01f, 0.02f, 0.05f, 0.1f});
        } else if (targetScale < 1.0f) {
            // Rango pequeño (décimas)
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{0.1f, 0.2f, 0.5f, 1.0f});
        } else if (targetScale < 10f) {
            // Rango medio-bajo (unidades)
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{1f, 2f, 5f, 10f});
        } else if (targetScale < 50f) {
            // Rango medio
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{10f, 20f, 50f});
        } else if (targetScale < 100f) {
            // Rango medio-alto
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{50f, 100f});
        } else if (targetScale < 250f) {
            // Rango alto
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{100f, 150f, 200f, 250f});
        } else if (targetScale < 500f) {
            // Rango muy alto
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{250f, 300f, 400f, 500f});
        } else if (targetScale < 1000f) {
            // Rango extra alto
            maxMagnitude = roundToNiceNumber(targetScale, new float[]{500f, 750f, 1000f});
        } else {
            // Rango extremo - redondear a centenas
            maxMagnitude = (float)(Math.ceil(targetScale / 100) * 100);
        }

        // ✅ Asegurar escala mínima
        if (maxMagnitude < MIN_SCALE) {
            maxMagnitude = MIN_SCALE;
        }

        // ✅ DEBUG: Mostrar escala calculada
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