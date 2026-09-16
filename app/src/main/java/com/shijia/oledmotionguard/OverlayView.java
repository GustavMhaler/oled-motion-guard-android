package com.shijia.oledmotionguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.view.View;

import java.util.Random;

public class OverlayView extends View {
    private static final int RIBBONS = 0;
    private static final int RAINBOW = 1;
    private static final int CONFETTI = 2;
    private static final int PIXEL_SHIFT = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] particleX = new float[160];
    private final float[] particleY = new float[160];
    private final float[] particleFall = new float[160];
    private final float[] particleSway = new float[160];
    private final float[] particleSize = new float[160];
    private final int[] particleColor = new int[160];
    private int mode = RIBBONS;
    private float progress;

    public OverlayView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        makeParticles();
    }

    public void setMode(String name) {
        if ("rainbow".equals(name)) mode = RAINBOW;
        else if ("confetti".equals(name)) mode = CONFETTI;
        else if ("pixel_shift".equals(name)) mode = PIXEL_SHIFT;
        else mode = RIBBONS;
    }

    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    private void makeParticles() {
        Random random = new Random(20260916L);
        for (int i = 0; i < particleX.length; i++) {
            particleX[i] = random.nextFloat();
            particleY[i] = random.nextFloat() * 1.25f - 1.0f;
            particleFall[i] = 0.12f + random.nextFloat() * 0.24f;
            particleSway[i] = -0.12f + random.nextFloat() * 0.24f;
            particleSize[i] = 0.45f + random.nextFloat() * 0.55f;
            particleColor[i] = hsvColor(random.nextFloat(), 0.78f, 0.82f, 220);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        float time = progress * 20f;
        switch (mode) {
            case RAINBOW:
                drawRainbow(canvas, time);
                break;
            case CONFETTI:
                drawConfetti(canvas, time);
                break;
            case PIXEL_SHIFT:
                drawPixelShift(canvas, time);
                break;
            default:
                drawRibbons(canvas, time);
                break;
        }
    }

    private void drawRibbons(Canvas canvas, float time) {
        float width = getWidth();
        float height = getHeight();
        float step = Math.max(42f, width / 22f);
        stroke.setStrokeWidth(Math.max(14f, height / 34f));
        for (int band = 0; band < 9; band++) {
            Path path = new Path();
            float baseline = (band + 0.5f) * height / 9f;
            boolean first = true;
            for (float x = -step; x <= width + step; x += step) {
                float y = baseline
                        + (float) Math.sin(x / Math.max(180f, width * 0.16f) + time * 1.35f + band)
                        * height * 0.075f
                        + (float) Math.cos(x / Math.max(260f, width * 0.23f) - time * 0.85f)
                        * height * 0.035f;
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
            stroke.setColor(hsvColor(band / 9f + time * 0.055f, 0.78f, 0.72f, 215));
            canvas.drawPath(path, stroke);
        }
    }

    private void drawRainbow(Canvas canvas, float time) {
        float width = getWidth();
        float height = getHeight();
        int bands = 28;
        float bandHeight = height / bands + 3f;
        for (int band = 0; band < bands; band++) {
            float y = band * height / bands;
            float offset = (float) Math.sin(time * 1.1f + band * 0.55f) * width * 0.12f;
            Path path = new Path();
            path.moveTo(-width, y - bandHeight);
            path.lineTo(width, y - bandHeight + offset);
            path.lineTo(width, y + bandHeight + offset);
            path.lineTo(-width, y + bandHeight);
            path.close();
            paint.setColor(hsvColor(band / (float) bands + time * 0.06f, 0.78f, 0.62f, 185));
            canvas.drawPath(path, paint);
        }
    }

    private void drawConfetti(Canvas canvas, float time) {
        float width = getWidth();
        float height = getHeight();
        for (int i = 0; i < particleX.length; i++) {
            float x = (particleX[i] * width
                    + (float) Math.sin(time * 1.6f + particleY[i] * 5f)
                    * width * particleSway[i]) % width;
            if (x < 0) x += width;
            float y = ((particleY[i] + time * particleFall[i]) % 1.25f) * height;
            float length = Math.max(8f, particleSize[i] * Math.min(width, height) * 0.035f);
            paint.setColor(particleColor[i]);
            canvas.drawRect(x, y, x + length, y + Math.max(4f, length / 4f), paint);
        }
    }

    private void drawPixelShift(Canvas canvas, float time) {
        float width = getWidth();
        float height = getHeight();
        int columns = 18;
        int rows = 10;
        float cellWidth = width / columns;
        float cellHeight = height / rows;
        RectF rect = new RectF();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float hue = column / (float) columns * 0.6f
                        + row / (float) rows * 0.4f + time * 0.07f;
                float wobble = (float) Math.sin(time * 1.1f + column * 0.4f + row) * 0.08f;
                paint.setColor(hsvColor(hue + wobble, 0.78f, 0.55f, 175));
                float x = column * cellWidth
                        + (float) Math.sin(time * 0.7f + row) * cellWidth * 0.16f;
                float y = row * cellHeight
                        + (float) Math.cos(time * 0.8f + column) * cellHeight * 0.12f;
                rect.set(x, y, x + cellWidth + 2f, y + cellHeight + 2f);
                canvas.drawRect(rect, paint);
            }
        }
    }

    private static int hsvColor(float hue, float saturation, float value, int alpha) {
        float normalized = hue - (float) Math.floor(hue);
        return Color.HSVToColor(alpha, new float[]{normalized * 360f, saturation, value});
    }
}
