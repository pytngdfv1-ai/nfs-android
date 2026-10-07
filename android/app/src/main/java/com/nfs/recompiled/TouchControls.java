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
    private static final int KIND_CHEATS = 3;

    private static class Btn {
        final String label;
        final int key;
        final int kind;
        final boolean alwaysVisible;
        final RectF r;
        boolean down = false;

        Btn(String label, int key, int kind, boolean alwaysVisible, RectF r) {
            this.label = label;
            this.key = key;
            this.kind = kind;
            this.alwaysVisible = alwaysVisible;
            this.r = r;
        }
    }

    private static class Cheat {
        final String code;
        final String desc;

        Cheat(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

    private final Cheat[] cheats = new Cheat[] {
            new Cheat("ELNINO", "Coche El Ni\u00F1o"),
            new Cheat("MERC", "Mercedes CLK-GTR"),
            new Cheat("JAG", "Jaguar XJR-15"),
            new Cheat("DCOP", "Diablo SV polic\u00EDa"),
            new Cheat("ECOP", "El Ni\u00F1o polic\u00EDa"),
            new Cheat("CARS", "Todos los coches extra"),
            new Cheat("EMPIRE", "Pista Empire City"),
            new Cheat("MONKEY", "Manual act\u00FAa como autom\u00E1tico"),
            new Cheat("GOFAST", "Velocidad extrema"),
            new Cheat("RUSHHOUR", "M\u00E1s tr\u00E1fico"),
    };

    private final List<Btn> buttons = new ArrayList<Btn>();
    private final RectF panel = new RectF();
    private final RectF closeBtn = new RectF();
    private final RectF[] cells = new RectF[10];

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint codePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint descPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final long startTime = SystemClock.uptimeMillis();
    private boolean controlsOn = false;
    private boolean panelOpen = false;
    private boolean typing = false;
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

        titlePaint.setColor(0xFFFFD54F);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(17f * density);
        titlePaint.setFakeBoldText(true);

        codePaint.setColor(0xFFFFFFFF);
        codePaint.setTextAlign(Paint.Align.LEFT);
        codePaint.setTextSize(15f * density);
        codePaint.setFakeBoldText(true);

        descPaint.setColor(0xFFB0BEC5);
        descPaint.setTextAlign(Paint.Align.LEFT);
        descPaint.setTextSize(11f * density);

        for (int i = 0; i < cells.length; i++) {
            cells[i] = new RectF();
        }
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

        // Siempre disponibles (arriba al centro): CTRL, TRUCOS, SALTAR
        buttons.add(new Btn("CTRL", 0, KIND_TOGGLE, true,
                new RectF(w / 2f - 104 * d, 6 * d, w / 2f - 44 * d, 38 * d)));
        buttons.add(new Btn("TRUCOS", 0, KIND_CHEATS, true,
                new RectF(w / 2f - 38 * d, 6 * d, w / 2f + 42 * d, 38 * d)));
        buttons.add(new Btn("SALTAR", 0, KIND_SKIP, true,
                new RectF(w / 2f + 48 * d, 6 * d, w / 2f + 128 * d, 38 * d)));

        // ENTER siempre visible (abajo al centro)
        buttons.add(new Btn("ENTER", KeyEvent.KEYCODE_ENTER, KIND_KEY, true,
                new RectF(w / 2f - 44 * d, h - 10 * d - 44 * d, w / 2f + 44 * d, h - 10 * d)));

        // Volante (abajo izquierda)
        buttons.add(new Btn("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT, KIND_KEY, false,
                new RectF(m, h - m - big, m + big, h - m)));
        buttons.add(new Btn("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT, KIND_KEY, false,
                new RectF(m + big + gap, h - m - big, m + 2 * big + gap, h - m)));

        // Acelerador y freno (abajo derecha)
        buttons.add(new Btn("FRENO", KeyEvent.KEYCODE_DPAD_DOWN, KIND_KEY, false,
                new RectF(w - m - big, h - m - mid, w - m, h - m)));
        buttons.add(new Btn("ACEL", KeyEvent.KEYCODE_DPAD_UP, KIND_KEY, false,
                new RectF(w - m - big, h - m - mid - gap - big, w - m, h - m - mid - gap)));

        // Freno de mano
        float hx = w - m - big - gap - mid;
        buttons.add(new Btn("MANO", KeyEvent.KEYCODE_SPACE, KIND_KEY, false,
                new RectF(hx, h - m - mid, hx + mid, h - m)));

        // Cambios y camara
        float rowY = h - m - mid - gap - small;
        buttons.add(new Btn("Z-", KeyEvent.KEYCODE_Z, KIND_KEY, false,
                new RectF(hx, rowY, hx + small, rowY + small)));
        buttons.add(new Btn("CAM", KeyEvent.KEYCODE_C, KIND_KEY, false,
                new RectF(hx + small + 8 * d, rowY, hx + 2 * small + 8 * d, rowY + small)));
        buttons.add(new Btn("A+", KeyEvent.KEYCODE_A, KIND_KEY, false,
                new RectF(hx, rowY - small - 8 * d, hx + small, rowY - 8 * d)));

        // Pausa (arriba izquierda)
        buttons.add(new Btn("ESC", KeyEvent.KEYCODE_ESCAPE, KIND_KEY, false,
                new RectF(10 * d, 6 * d, 10 * d + 56 * d, 6 * d + 36 * d)));

        layoutPanel(w, h);
    }

    // Panel de trucos: 2 columnas x 5 filas
    private void layoutPanel(int w, int h) {
        float d = density;
        float pw = Math.min(w - 40 * d, 540 * d);
        float ph = Math.min(h - 16 * d, 300 * d);
        float left = (w - pw) / 2f;
        float top = (h - ph) / 2f;
        panel.set(left, top, left + pw, top + ph);

        float titleH = 40 * d;
        closeBtn.set(panel.right - 40 * d, panel.top + 4 * d, panel.right - 6 * d, panel.top + 36 * d);

        float pad = 10 * d;
        float cw = (pw - pad * 3) / 2f;
        float rows = 5;
        float ch = (ph - titleH - pad * (rows + 1) + pad) / rows;
        for (int i = 0; i < cells.length; i++) {
            int col = i % 2;
            int row = i / 2;
            float x = panel.left + pad + col * (cw + pad);
            float y = panel.top + titleH + pad + row * (ch + pad);
            cells[i].set(x, y, x + cw, y + ch);
        }
    }

    private boolean introActive() {
        return SystemClock.uptimeMillis() - startTime < INTRO_MS;
    }

    private boolean isVisible(Btn b) {
        if (b.kind == KIND_SKIP) return introActive();
        if (b.alwaysVisible) return true;
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

    // Escribe una palabra letra por letra (como en el teclado de la PC)
    private void typeWord(String word) {
        if (typing) return;
        typing = true;
        final String w = word.toLowerCase(java.util.Locale.ROOT);
        final long step = 160;
        for (int i = 0; i < w.length(); i++) {
            final char c = w.charAt(i);
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    pressChar(c);
                }
            }, 250 + step * i);
        }
        postDelayed(new Runnable() {
            @Override
            public void run() {
                typing = false;
            }
        }, 250 + step * w.length() + 100);
    }

    private void pressChar(char c) {
        final int code = KeyEvent.KEYCODE_A + (c - 'a');
        SDLActivity.onNativeKeyDown(code);
        NfsActivity.nativeTypeChar(c);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                SDLActivity.onNativeKeyUp(code);
            }
        }, 60);
    }

    private void handlePanelTouch(float x, float y) {
        if (closeBtn.contains(x, y)) {
            panelOpen = false;
        } else {
            boolean picked = false;
            for (int i = 0; i < cells.length; i++) {
                if (cells[i].contains(x, y)) {
                    panelOpen = false;
                    typeWord(cheats[i].code);
                    picked = true;
                    break;
                }
            }
            if (!picked && !panel.contains(x, y)) {
                panelOpen = false;
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();

        // Con el panel de trucos abierto, todo toque es para el panel
        if (panelOpen) {
            if (action == MotionEvent.ACTION_DOWN) {
                handlePanelTouch(e.getX(idx), e.getY(idx));
            }
            return true;
        }

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
            if (b.kind == KIND_CHEATS) {
                releaseAll();
                panelOpen = true;
                invalidate();
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
            if (b.kind == KIND_SKIP || b.kind == KIND_CHEATS) alpha = 0x80;
            fill.setColor((alpha << 24) | 0x00FFFFFF);
            canvas.drawRoundRect(b.r, radius, radius, fill);
            canvas.drawRoundRect(b.r, radius, radius, stroke);
            float ty = b.r.centerY() - (text.descent() + text.ascent()) / 2f;
            canvas.drawText(b.label, b.r.centerX(), ty, text);
        }

        if (panelOpen) {
            fill.setColor(0xF0101830);
            canvas.drawRoundRect(panel, radius, radius, fill);
            canvas.drawRoundRect(panel, radius, radius, stroke);

            float ty = panel.top + 26 * density;
            canvas.drawText("TRUCOS  (toca uno)", panel.centerX(), ty, titlePaint);

            fill.setColor(0x55FF5252);
            canvas.drawRoundRect(closeBtn, radius, radius, fill);
            float cy = closeBtn.centerY() - (text.descent() + text.ascent()) / 2f;
            canvas.drawText("X", closeBtn.centerX(), cy, text);

            for (int i = 0; i < cells.length; i++) {
                RectF c = cells[i];
                fill.setColor(0x33FFFFFF);
                canvas.drawRoundRect(c, radius, radius, fill);
                float tx = c.left + 12 * density;
                float codeY = c.top + c.height() * 0.45f;
                float descY = c.top + c.height() * 0.80f;
                canvas.drawText(cheats[i].code, tx, codeY, codePaint);
                canvas.drawText(cheats[i].desc, tx, descY, descPaint);
            }
        }
    }
}
