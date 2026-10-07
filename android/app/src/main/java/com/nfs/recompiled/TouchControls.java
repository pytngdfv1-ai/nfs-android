package com.nfs.recompiled;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        final boolean round;
        final RectF r;

        Btn(String label, int key, int kind, boolean alwaysVisible, boolean round, RectF r) {
            this.label = label;
            this.key = key;
            this.kind = kind;
            this.alwaysVisible = alwaysVisible;
            this.round = round;
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
            new Cheat("CARS", "Todos los coches"),
            new Cheat("EMPIRE", "Pista Empire City"),
            new Cheat("MONKEY", "Manual = autom\u00E1tico"),
            new Cheat("GOFAST", "Velocidad extrema"),
            new Cheat("RUSHHOUR", "M\u00E1s tr\u00E1fico"),
    };

    private final List<Btn> buttons = new ArrayList<Btn>();
    private final Set<Integer> held = new HashSet<Integer>();

    // Palanca de volante
    private float stickCx, stickCy, stickR;
    private float knobDx = 0, knobDy = 0;

    // Panel de trucos
    private final RectF panel = new RectF();
    private final RectF closeBtn = new RectF();
    private final RectF enterBtn = new RectF();
    private final RectF echoBox = new RectF();
    private final RectF[] cells = new RectF[10];
    private final StringBuilder typed = new StringBuilder();

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint codePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint descPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint echoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final long startTime = SystemClock.uptimeMillis();
    private boolean controlsOn = false;
    private boolean panelOpen = false;
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

        smallText.setColor(0xFFFFFFFF);
        smallText.setTextAlign(Paint.Align.CENTER);
        smallText.setTextSize(13f * density);
        smallText.setFakeBoldText(true);

        titlePaint.setColor(0xFFFFD54F);
        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setTextSize(13f * density);
        titlePaint.setFakeBoldText(true);

        codePaint.setColor(0xFFFFFFFF);
        codePaint.setTextAlign(Paint.Align.LEFT);
        codePaint.setTextSize(15f * density);
        codePaint.setFakeBoldText(true);

        descPaint.setColor(0xFFB0BEC5);
        descPaint.setTextAlign(Paint.Align.LEFT);
        descPaint.setTextSize(10f * density);

        echoPaint.setColor(0xFF80FF80);
        echoPaint.setTextAlign(Paint.Align.LEFT);
        echoPaint.setTextSize(16f * density);
        echoPaint.setFakeBoldText(true);

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

    // ------------------------------------------------------------------ layout

    private void addKey(String label, int key, boolean always, boolean round, float l, float t, float r, float b) {
        buttons.add(new Btn(label, key, KIND_KEY, always, round, new RectF(l, t, r, b)));
    }

    private void addRound(String label, int key, float cx, float cy, float radius) {
        addKey(label, key, false, true, cx - radius, cy - radius, cx + radius, cy + radius);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float d = density;
        float m = 14 * d;
        buttons.clear();

        // El juego se dibuja en 4:3 centrado: calcular las franjas negras laterales
        float gameW = h * 4f / 3f;
        float barW = Math.max(0f, (w - gameW) / 2f);
        // Zona de controles: la franja negra (o 150dp en el borde si es muy angosta)
        float zw = Math.max(barW, 150 * d);
        float s = Math.max(0.7f, Math.min(1.25f, zw / (190 * d)));
        float leftC = zw / 2f;
        float rightC = w - zw / 2f;

        // Siempre disponibles (arriba al centro): CTRL, TRUCOS, SALTAR
        buttons.add(new Btn("CTRL", 0, KIND_TOGGLE, true, false,
                new RectF(w / 2f - 104 * d, 6 * d, w / 2f - 44 * d, 38 * d)));
        buttons.add(new Btn("TRUCOS", 0, KIND_CHEATS, true, false,
                new RectF(w / 2f - 38 * d, 6 * d, w / 2f + 42 * d, 38 * d)));
        buttons.add(new Btn("SALTAR", 0, KIND_SKIP, true, false,
                new RectF(w / 2f + 48 * d, 6 * d, w / 2f + 128 * d, 38 * d)));

        // ENTER siempre visible (abajo al centro)
        addKey("ENTER", KeyEvent.KEYCODE_ENTER, true, false,
                w / 2f - 44 * d, h - 10 * d - 44 * d, w / 2f + 44 * d, h - 10 * d);

        // ---------- Franja izquierda ----------
        // Pausa (arriba)
        addKey("ESC", KeyEvent.KEYCODE_ESCAPE, false, false,
                leftC - 28 * d, 6 * d, leftC + 28 * d, 42 * d);

        // Palanca de volante (abajo)
        stickR = Math.min(68 * d * s, zw * 0.42f);
        stickCx = leftC;
        stickCy = h - m - stickR;

        // Cruceta (arriba de la palanca)
        float sb = 42 * d * s;
        float g = 3 * d;
        float cx = leftC;
        float cy = h * 0.36f;
        addKey("\u25B2", KeyEvent.KEYCODE_DPAD_UP, false, false,
                cx - sb / 2, cy - sb / 2 - g - sb, cx + sb / 2, cy - sb / 2 - g);
        addKey("\u25BC", KeyEvent.KEYCODE_DPAD_DOWN, false, false,
                cx - sb / 2, cy + sb / 2 + g, cx + sb / 2, cy + sb / 2 + g + sb);
        addKey("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT, false, false,
                cx - sb / 2 - g - sb, cy - sb / 2, cx - sb / 2 - g, cy + sb / 2);
        addKey("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT, false, false,
                cx + sb / 2 + g, cy - sb / 2, cx + sb / 2 + g + sb, cy + sb / 2);

        // ---------- Franja derecha ----------
        // Pedales (abajo)
        float gasW = 84 * d * s;
        float gasH = 132 * d * s;
        float brkW = 72 * d * s;
        float brkH = 100 * d * s;
        float pgap = 10 * d * s;
        float totalW = brkW + pgap + gasW;
        float left0 = rightC - totalW / 2f;
        addKey("FRENO", KeyEvent.KEYCODE_DPAD_DOWN, false, false,
                left0, h - m - brkH, left0 + brkW, h - m);
        addKey("GAS", KeyEvent.KEYCODE_DPAD_UP, false, false,
                left0 + brkW + pgap, h - m - gasH, left0 + totalW, h - m);

        // Botones redondos en rombo (arriba de los pedales)
        float pedalsTop = h - m - gasH;
        float rr = 27 * d * s;
        float ox = 54 * d * s;
        float oy = 50 * d * s;
        float ry = pedalsTop - 8 * d - oy - rr;
        addRound("A+", KeyEvent.KEYCODE_A, rightC, ry - oy, rr);          // arriba
        addRound("CAM", KeyEvent.KEYCODE_C, rightC - ox, ry, rr);         // izquierda
        addRound("Z-", KeyEvent.KEYCODE_Z, rightC + ox, ry, rr);          // derecha
        addRound("MANO", KeyEvent.KEYCODE_SPACE, rightC, ry + oy, rr);    // abajo

        layoutPanel(w, h);
    }

    // Panel de trucos: ocupa todo el ancho, arriba; el teclado queda abajo
    private void layoutPanel(int w, int h) {
        float d = density;
        float ph = Math.min(h * 0.5f, 200 * d);
        panel.set(0, 0, w, ph);

        float bar = 40 * d;
        closeBtn.set(w - 10 * d - 44 * d, 4 * d, w - 10 * d, 4 * d + 32 * d);
        enterBtn.set(closeBtn.left - 8 * d - 90 * d, 4 * d, closeBtn.left - 8 * d, 4 * d + 32 * d);
        echoBox.set(230 * d, 4 * d, enterBtn.left - 10 * d, 4 * d + 32 * d);

        float pad = 8 * d;
        int cols = 5;
        float cw = (w - pad * (cols + 1)) / cols;
        float ch = (ph - bar - pad * 3) / 2f;
        for (int i = 0; i < cells.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float x = pad + col * (cw + pad);
            float y = bar + pad + row * (ch + pad);
            cells[i].set(x, y, x + cw, y + ch);
        }
    }

    // ------------------------------------------------------------------ teclas

    private boolean introActive() {
        return SystemClock.uptimeMillis() - startTime < INTRO_MS;
    }

    private boolean isVisible(Btn b) {
        if (b.kind == KIND_SKIP) return introActive();
        if (b.alwaysVisible) return true;
        return controlsOn;
    }

    private boolean contains(Btn b, float x, float y) {
        if (b.round) {
            float dx = x - b.r.centerX();
            float dy = y - b.r.centerY();
            float rad = b.r.width() / 2f;
            return dx * dx + dy * dy <= rad * rad;
        }
        return b.r.contains(x, y);
    }

    private Btn hit(float x, float y) {
        for (Btn b : buttons) {
            if (isVisible(b) && contains(b, x, y)) return b;
        }
        return null;
    }

    private boolean inStick(float x, float y) {
        if (!controlsOn) return false;
        float dx = x - stickCx;
        float dy = y - stickCy;
        float rad = stickR * 1.5f;
        return dx * dx + dy * dy <= rad * rad;
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
        for (Integer k : new ArrayList<Integer>(held)) {
            SDLActivity.onNativeKeyUp(k);
        }
        held.clear();
        knobDx = 0;
        knobDy = 0;
    }

    private void syncKeys(Set<Integer> wanted) {
        for (Integer k : new ArrayList<Integer>(held)) {
            if (!wanted.contains(k)) {
                held.remove(k);
                SDLActivity.onNativeKeyUp(k);
            }
        }
        for (Integer k : wanted) {
            if (!held.contains(k)) {
                held.add(k);
                SDLActivity.onNativeKeyDown(k);
            }
        }
    }

    // ------------------------------------------------------------------ teclado de trucos

    private void sendChar(char c) {
        if (c == '\n') {
            sendEnter();
            return;
        }
        char lc = Character.toLowerCase(c);
        typed.append(lc);
        if (lc >= 'a' && lc <= 'z') {
            tapKey(KeyEvent.KEYCODE_A + (lc - 'a'));
        } else if (lc >= '0' && lc <= '9') {
            tapKey(KeyEvent.KEYCODE_0 + (lc - '0'));
        } else if (lc == ' ') {
            tapKey(KeyEvent.KEYCODE_SPACE);
        }
        NfsActivity.nativeTypeChar(lc);
        invalidate();
    }

    private void sendBackspace() {
        if (typed.length() > 0) typed.setLength(typed.length() - 1);
        tapKey(KeyEvent.KEYCODE_DEL);
        invalidate();
    }

    private void sendEnter() {
        typed.setLength(0);
        tapKey(KeyEvent.KEYCODE_ENTER);
        invalidate();
    }

    private void setPanel(boolean open) {
        if (open == panelOpen) return;
        panelOpen = open;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (open) {
            releaseAll();
            typed.setLength(0);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
            if (imm != null) {
                imm.restartInput(this);
            }
            post(new Runnable() {
                @Override
                public void run() {
                    InputMethodManager m2 = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (m2 != null) {
                        m2.showSoftInput(TouchControls.this, 0);
                    }
                }
            });
        } else {
            if (imm != null) {
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
            }
            setFocusable(false);
            setFocusableInTouchMode(false);
            clearFocus();
            if (getContext() instanceof NfsActivity) {
                ((NfsActivity) getContext()).restoreSdlFocus();
            }
        }
        invalidate();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return panelOpen;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (!panelOpen) return null;
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new CheatInputConnection(this);
    }

    private class CheatInputConnection extends BaseInputConnection {
        private String composing = "";

        CheatInputConnection(View v) {
            super(v, false);
        }

        @Override
        public boolean commitText(CharSequence t, int newCursorPosition) {
            String s = t.toString();
            if (composing.length() > 0 && s.startsWith(composing)) {
                s = s.substring(composing.length());
            }
            composing = "";
            for (int i = 0; i < s.length(); i++) sendChar(s.charAt(i));
            return true;
        }

        @Override
        public boolean setComposingText(CharSequence t, int newCursorPosition) {
            String s = t.toString();
            if (s.startsWith(composing)) {
                for (int i = composing.length(); i < s.length(); i++) sendChar(s.charAt(i));
            } else if (composing.startsWith(s)) {
                for (int i = s.length(); i < composing.length(); i++) sendBackspace();
            } else {
                for (int i = 0; i < composing.length(); i++) sendBackspace();
                for (int i = 0; i < s.length(); i++) sendChar(s.charAt(i));
            }
            composing = s;
            return true;
        }

        @Override
        public boolean finishComposingText() {
            composing = "";
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int before, int after) {
            for (int i = 0; i < before; i++) sendBackspace();
            return true;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent ev) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                int k = ev.getKeyCode();
                if (k == KeyEvent.KEYCODE_ENTER) {
                    sendEnter();
                } else if (k == KeyEvent.KEYCODE_DEL) {
                    sendBackspace();
                } else {
                    int u = ev.getUnicodeChar();
                    if (u > 0) sendChar((char) u);
                }
            }
            return true;
        }

        @Override
        public boolean performEditorAction(int actionCode) {
            sendEnter();
            return true;
        }
    }

    // ------------------------------------------------------------------ toques

    private void handlePanelTouch(float x, float y) {
        if (closeBtn.contains(x, y)) {
            setPanel(false);
        } else if (enterBtn.contains(x, y)) {
            sendEnter();
        }
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
            float x = e.getX(idx);
            float y = e.getY(idx);
            Btn b = hit(x, y);
            if (b == null && !inStick(x, y)) {
                return false; // el toque pasa al juego (menus con el dedo)
            }
            if (b != null) {
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
                    setPanel(true);
                    return true;
                }
            }
        }

        // Teclas que deben estar presionadas ahora
        Set<Integer> wanted = new HashSet<Integer>();
        boolean stickTouched = false;
        if (action != MotionEvent.ACTION_CANCEL) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == idx) {
                    continue;
                }
                float x = e.getX(i);
                float y = e.getY(i);
                if (inStick(x, y)) {
                    stickTouched = true;
                    float dx = x - stickCx;
                    float dy = y - stickCy;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    float max = stickR * 0.6f;
                    if (len > max && len > 0) {
                        knobDx = dx / len * max;
                        knobDy = dy / len * max;
                    } else {
                        knobDx = dx;
                        knobDy = dy;
                    }
                    if (dx < -0.22f * stickR) {
                        wanted.add(KeyEvent.KEYCODE_DPAD_LEFT);
                    } else if (dx > 0.22f * stickR) {
                        wanted.add(KeyEvent.KEYCODE_DPAD_RIGHT);
                    }
                } else {
                    Btn b = hit(x, y);
                    if (b != null && b.kind == KIND_KEY) wanted.add(b.key);
                }
            }
        }
        if (!stickTouched) {
            knobDx = 0;
            knobDy = 0;
        }
        syncKeys(wanted);
        invalidate();
        return true;
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = 10 * density;

        for (Btn b : buttons) {
            if (!isVisible(b)) continue;
            boolean pressed = b.kind == KIND_KEY && held.contains(b.key);
            int alpha = pressed ? 0x99 : 0x40;
            if (b.kind == KIND_TOGGLE) alpha = controlsOn ? 0x80 : 0x50;
            if (b.kind == KIND_SKIP || b.kind == KIND_CHEATS) alpha = 0x80;
            fill.setColor((alpha << 24) | 0x00FFFFFF);
            Paint tp = b.round ? smallText : text;
            if (b.round) {
                float rad = b.r.width() / 2f;
                canvas.drawCircle(b.r.centerX(), b.r.centerY(), rad, fill);
                canvas.drawCircle(b.r.centerX(), b.r.centerY(), rad, stroke);
            } else {
                canvas.drawRoundRect(b.r, radius, radius, fill);
                canvas.drawRoundRect(b.r, radius, radius, stroke);
            }
            float ty = b.r.centerY() - (tp.descent() + tp.ascent()) / 2f;
            canvas.drawText(b.label, b.r.centerX(), ty, tp);
        }

        // Palanca de volante
        if (controlsOn) {
            fill.setColor(0x26FFFFFF);
            canvas.drawCircle(stickCx, stickCy, stickR, fill);
            canvas.drawCircle(stickCx, stickCy, stickR, stroke);
            fill.setColor(0x88FFFFFF);
            canvas.drawCircle(stickCx + knobDx, stickCy + knobDy, stickR * 0.38f, fill);
        }

        // Panel de trucos (arriba) + eco de lo que escribes
        if (panelOpen) {
            fill.setColor(0xF2101830);
            canvas.drawRect(panel, fill);
            canvas.drawLine(0, panel.bottom, panel.right, panel.bottom, stroke);

            canvas.drawText("Teclea el truco y pulsa ENTER", 12 * density, 4 * density + 21 * density, titlePaint);

            fill.setColor(0x33FFFFFF);
            canvas.drawRoundRect(echoBox, radius, radius, fill);
            String echo = typed.length() > 0 ? typed.toString() : "...";
            float ey = echoBox.centerY() - (echoPaint.descent() + echoPaint.ascent()) / 2f;
            canvas.drawText(echo, echoBox.left + 10 * density, ey, echoPaint);

            fill.setColor(0x66448AFF);
            canvas.drawRoundRect(enterBtn, radius, radius, fill);
            float by = enterBtn.centerY() - (text.descent() + text.ascent()) / 2f;
            canvas.drawText("ENTER", enterBtn.centerX(), by, text);

            fill.setColor(0x66FF5252);
            canvas.drawRoundRect(closeBtn, radius, radius, fill);
            canvas.drawText("X", closeBtn.centerX(), by, text);

            for (int i = 0; i < cells.length; i++) {
                RectF c = cells[i];
                fill.setColor(0x33FFFFFF);
                canvas.drawRoundRect(c, radius, radius, fill);
                float tx = c.left + 10 * density;
                canvas.drawText(cheats[i].code, tx, c.top + c.height() * 0.46f, codePaint);
                canvas.drawText(cheats[i].desc, tx, c.top + c.height() * 0.82f, descPaint);
            }
        }
    }
}
