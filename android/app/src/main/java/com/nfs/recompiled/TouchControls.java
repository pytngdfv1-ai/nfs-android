package com.nfs.recompiled;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import org.libsdl.app.SDLActivity;

public class TouchControls extends View {

    private static final long INTRO_MS = 45000;
    private static final int KIND_KEY = 0;
    private static final int KIND_TOGGLE = 1;
    private static final int KIND_SKIP = 2;

    private static class Btn {
        final String label;
        final int key;
        final int kind;
        final RectF r;
        boolean down = false;

        Btn(String label, int key, int kind, RectF r) {
            this.label = label;
            this.key = key;
            this.kind = kind;
            this.r = r;
        }
    }

    private final List<Btn> buttons = new ArrayList<Btn>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long startTime = SystemClock.uptimeMillis();
    private boolean controlsOn = false;
    private float density = 1f;

    public TouchControls(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * density);
        stroke.setColor(0x99FFFFFF);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(15f * density);
        text.setFakeBoldText(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        }, INTRO_MS + 200);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float d = density;
        float m = 20 * d;
        float big = 84 * d;
        float mid = 64 * d;
        float small = 44 * d;
        float gap = 10 * d;
        buttons.clear();

        // Siempre disponibles (arriba al centro)
        buttons.add(new Btn("CTRL", 0, KIND_TOGGLE,
                new RectF(w / 2f - 30 * d, 6 * d, w / 2f + 30 * d, 38 * d)));
        buttons.add(new Btn("SALTAR", 0, KIND_SKIP,
                new RectF(w / 2f + 40 * d, 6 * d, w / 2f + 120 * d, 38 * d)));

        // Volante (abajo izquierda)
        buttons.add(new Btn("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT, KIND_KEY,
                new RectF(m, h - m - big, m + big, h - m)));
        buttons.add(new Btn("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT, KIND_KEY,
                new RectF(m + big + gap, h - m - big, m + 2 * big + gap, h - m)));

        // Acelerador y freno (abajo derecha)
        buttons.add(new Btn("FRENO", KeyEvent.KEYCODE_DPAD_DOWN, KIND_KEY,
                new RectF(w - m - big, h - m - mid, w - m, h - m)));
        buttons.add(new Btn("ACEL", KeyEvent.KEYCODE_DPAD_UP, KIND_KEY,
                new RectF(w - m - big, h - m - mid - gap - big, w - m, h - m - mid - gap)));

        // Freno de mano
        float hx = w - m - big - gap - mid;
        buttons.add(new Btn("MANO", KeyEvent.KEYCODE_SPACE, KIND_KEY,
                new RectF(hx, h - m - mid, hx + mid, h - m)));

        // Cambios y camara
        float rowY = h - m - mid - gap - small;
        buttons.add(new Btn("Z-", KeyEvent.KEYCODE_Z, KIND_KEY,
                new RectF(hx, rowY, hx + small, rowY + small)));
        buttons.add(new Btn("CAM", KeyEvent.KEYCODE_C, KIND_KEY,
                new RectF(hx + small + 8 * d, rowY, hx + 2 * small + 8 * d, rowY + small)));
        buttons.add(new Btn("A+", KeyEvent.KEYCODE_A, KIND_KEY,
                new RectF(hx, rowY - small - 8 * d, hx + small, rowY - 8 * d)));

        // Pausa (arriba izquierda)
        buttons.add(new Btn("ESC", KeyEvent.KEYCODE_ESCAPE, KIND_KEY,
                new RectF(10 * d, 6 * d, 10 * d + 56 * d, 6 * d + 36 * d)));
    }

    private boolean introActive() {
        return SystemClock.uptimeMillis() - startTime < INTRO_MS;
    }

    private boolean isVisible(Btn b) {
        if (b.kind == KIND_TOGGLE) return true;
        if (b.kind == KIND_SKIP) return introActive();
        return controlsOn;
    }

    private Btn hit(float x, float y) {
        for (Btn b : buttons) {
            if (isVisible(b) && b.r.contains(x, y)) return b;
        }
        return null;
    }

    private void tapKey(final int keyCode) {
        SDLActivity.onNativeKeyDown(keyCode);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                SDLActivity.onNativeKeyUp(keyCode);
            }
        }, 90);
    }

    private void releaseAll() {
        for (Btn b : buttons) {
            if (b.down) {
                b.down = false;
                SDLActivity.onNativeKeyUp(b.key);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            Btn b = hit(e.getX(idx), e.getY(idx));
            if (b == null) {
                return false; // el toque pasa al juego (menus con el dedo)
            }
            if (b.kind == KIND_TOGGLE) {
                controlsOn = !controlsOn;
                if (!controlsOn) releaseAll();
                invalidate();
                return true;
            }
            if (b.kind == KIND_SKIP) {
                tapKey(KeyEvent.KEYCODE_ESCAPE);
                return true;
            }
        }

        // Botones de tecla que estan siendo presionados ahora
        List<Btn> now = new ArrayList<Btn>();
        if (action != MotionEvent.ACTION_CANCEL) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == idx) {
                    continue;
                }
                Btn b = hit(e.getX(i), e.getY(i));
                if (b != null && b.kind == KIND_KEY) now.add(b);
            }
        }
        for (Btn b : buttons) {
            if (b.kind != KIND_KEY) continue;
            boolean should = now.contains(b);
            if (should && !b.down) {
                b.down = true;
                SDLActivity.onNativeKeyDown(b.key);
            } else if (!should && b.down) {
                b.down = false;
                SDLActivity.onNativeKeyUp(b.key);
            }
        }
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = 10 * density;
        for (Btn b : buttons) {
            if (!isVisible(b)) continue;
            int alpha = b.down ? 0x99 : 0x40;
            if (b.kind == KIND_TOGGLE) alpha = controlsOn ? 0x80 : 0x50;
            if (b.kind == KIND_SKIP) alpha = 0x80;
            fill.setColor((alpha << 24) | 0x00FFFFFF);
            canvas.drawRoundRect(b.r, radius, radius, fill);
            canvas.drawRoundRect(b.r, radius, radius, stroke);
            float ty = b.r.centerY() - (text.descent() + text.ascent()) / 2f;
            canvas.drawText(b.label, b.r.centerX(), ty, text);
        }
    }
}
