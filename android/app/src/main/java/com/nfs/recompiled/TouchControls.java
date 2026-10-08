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
    private static final int KIND_SKIP = 1;
    private static final int KIND_CHEATS = 2;

    private static class Btn {
        final String label;
        final int key;
        final int kind;
        final boolean bigLabel;
        final float slop; // margen extra de toque alrededor del dibujo
        final RectF r;

        Btn(String label, int key, int kind, boolean bigLabel, float slop, RectF r) {
            this.label = label;
            this.key = key;
            this.kind = kind;
            this.bigLabel = bigLabel;
            this.slop = slop;
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

    // Panel de trucos
    private final RectF panel = new RectF();
    private final RectF closeBtn = new RectF();
    private final RectF echoBox = new RectF();
    private final RectF[] cells = new RectF[10];
    private final StringBuilder typed = new StringBuilder();

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint codePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint descPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint echoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final long startTime = SystemClock.uptimeMillis();
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
        text.setTextSize(14f * density);
        text.setFakeBoldText(true);

        smallLabel.setColor(0xFFFFFFFF);
        smallLabel.setTextAlign(Paint.Align.CENTER);
        smallLabel.setTextSize(12f * density);
        smallLabel.setFakeBoldText(true);

        arrowText.setColor(0xFFFFFFFF);
        arrowText.setTextAlign(Paint.Align.CENTER);
        arrowText.setTextSize(26f * density);
        arrowText.setFakeBoldText(true);

        titlePaint.setColor(0xFFFFD54F);
        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setTextSize(11f * density);
        titlePaint.setFakeBoldText(true);

        codePaint.setColor(0xFFFFFFFF);
        codePaint.setTextAlign(Paint.Align.LEFT);
        codePaint.setTextSize(12f * density);
        codePaint.setFakeBoldText(true);

        descPaint.setColor(0xFFB0BEC5);
        descPaint.setTextAlign(Paint.Align.LEFT);
        descPaint.setTextSize(9f * density);

        echoPaint.setColor(0xFF80FF80);
        echoPaint.setTextAlign(Paint.Align.LEFT);
        echoPaint.setTextSize(14f * density);
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

    private void addKey(String label, int key, boolean bigLabel, float slop,
                        float l, float t, float r, float b) {
        buttons.add(new Btn(label, key, KIND_KEY, bigLabel, slop, new RectF(l, t, r, b)));
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
        float zw = Math.max(barW, 150 * d);
        float s = Math.max(0.7f, Math.min(1.25f, zw / (190 * d)));
        float leftC = zw / 2f;
        float rightC = w - zw / 2f;

        // Franja izquierda, arriba: TRUCOS (chico) y debajo el boton C (camara)
        buttons.add(new Btn("TRUCOS", 0, KIND_CHEATS, false, 4 * d,
                new RectF(leftC - 30 * d, 6 * d, leftC + 30 * d, 36 * d)));
        addKey("C", KeyEvent.KEYCODE_C, false, 6 * d,
                leftC - 18 * d, 44 * d, leftC + 18 * d, 44 * d + 36 * d);

        // Centro arriba: SALTAR la intro (solo al principio)
        buttons.add(new Btn("SALTAR", 0, KIND_SKIP, false, 0,
                new RectF(w / 2f - 40 * d, 6 * d, w / 2f + 40 * d, 38 * d)));

        // Franja izquierda, abajo: giro izquierda / derecha
        float dir = 64 * d * s;
        float dgap = 8 * d * s;
        float dleft = leftC - (dir * 2 + dgap) / 2f;
        float arrowSlop = 10 * d;
        addKey("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT, true, arrowSlop,
                dleft, h - m - dir, dleft + dir, h - m);
        addKey("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT, true, arrowSlop,
                dleft + dir + dgap, h - m - dir, dleft + 2 * dir + dgap, h - m);

        // Franja derecha: freno / reversa y acelerador (zona de toque amplia)
        float gasW = 84 * d * s;
        float gasH = 132 * d * s;
        float brkW = 72 * d * s;
        float brkH = 100 * d * s;
        float pgap = 10 * d * s;
        float totalW = brkW + pgap + gasW;
        float left0 = rightC - totalW / 2f;
        float pedalSlop = 18 * d;
        addKey("FRENO", KeyEvent.KEYCODE_DPAD_DOWN, false, pedalSlop,
                left0, h - m - brkH, left0 + brkW, h - m);
        addKey("GAS", KeyEvent.KEYCODE_DPAD_UP, false, pedalSlop,
                left0 + brkW + pgap, h - m - gasH, left0 + totalW, h - m);

        layoutPanel(w, h);
    }

    // Panel de trucos compacto: arriba, ancho completo (el teclado queda abajo)
    private void layoutPanel(int w, int h) {
        float d = density;
        float bar = 32 * d;
        float pad = 6 * d;
        float rowH = 36 * d;
        float ph = bar + pad + 2 * rowH + pad * 2;
        panel.set(0, 0, w, ph);

        closeBtn.set(w - 10 * d - 40 * d, 3 * d, w - 10 * d, 3 * d + 26 * d);
        echoBox.set(260 * d, 3 * d, closeBtn.left - 10 * d, 3 * d + 26 * d);

        int cols = 5;
        float cw = (w - pad * (cols + 1)) / cols;
        for (int i = 0; i < cells.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float x = pad + col * (cw + pad);
            float y = bar + pad + row * (rowH + pad);
            cells[i].set(x, y, x + cw, y + rowH);
        }
    }

    // ------------------------------------------------------------------ teclas

    private boolean introActive() {
        return SystemClock.uptimeMillis() - startTime < INTRO_MS;
    }

    private boolean isVisible(Btn b) {
        if (b.kind == KIND_SKIP) return introActive();
        return true;
    }

    // Busca el boton tocado: zona ampliada por "slop"; si hay varios, gana el mas cercano
    private Btn hit(float x, float y) {
        Btn best = null;
        float bestDist = Float.MAX_VALUE;
        for (Btn b : buttons) {
            if (!isVisible(b)) continue;
            float l = b.r.left - b.slop;
            float t = b.r.top - b.slop;
            float r = b.r.right + b.slop;
            float bt = b.r.bottom + b.slop;
            if (x >= l && x <= r && y >= t && y <= bt) {
                float dx = x - b.r.centerX();
                float dy = y - b.r.centerY();
                float dist = dx * dx + dy * dy;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = b;
                }
            }
        }
        return best;
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
        if (c == '\n' || c == '\r') {
            return; // Enter no se usa para los trucos
        }
        char lc = Character.toLowerCase(c);
        typed.append(lc);
        if (typed.length() > 16) typed.delete(0, typed.length() - 16);
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
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE
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
                    // Enter no se envia al juego
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
            return true; // Enter del teclado: no hace nada
        }
    }

    // ------------------------------------------------------------------ toques

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();

        // Con el panel de trucos abierto, todo toque es para el panel
        if (panelOpen) {
            if (action == MotionEvent.ACTION_DOWN && closeBtn.contains(e.getX(idx), e.getY(idx))) {
                setPanel(false);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            Btn b = hit(e.getX(idx), e.getY(idx));
            if (b == null) {
                return false; // el toque pasa al juego (menus con el dedo)
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

        // Teclas que deben estar presionadas ahora
        Set<Integer> wanted = new HashSet<Integer>();
        if (action != MotionEvent.ACTION_CANCEL) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == idx) {
                    continue;
                }
                Btn b = hit(e.getX(i), e.getY(i));
                if (b != null && b.kind == KIND_KEY) wanted.add(b.key);
            }
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
            int alpha = pressed ? 0xA0 : 0x50;
            if (b.kind != KIND_KEY) alpha = 0x80;
            fill.setColor((alpha << 24) | 0x00FFFFFF);
            canvas.drawRoundRect(b.r, radius, radius, fill);
            canvas.drawRoundRect(b.r, radius, radius, stroke);
            Paint tp = b.bigLabel ? arrowText : text;
            if (b.kind == KIND_CHEATS) tp = smallLabel;
            float ty = b.r.centerY() - (tp.descent() + tp.ascent()) / 2f;
            canvas.drawText(b.label, b.r.centerX(), ty, tp);
        }

        // Panel de trucos (arriba) + eco de lo que escribes
        if (panelOpen) {
            fill.setColor(0xF2101830);
            canvas.drawRect(panel, fill);
            canvas.drawLine(0, panel.bottom, panel.right, panel.bottom, stroke);

            float ty0 = 3 * density + 17 * density;
            canvas.drawText("Teclea el truco (sin Enter)", 10 * density, ty0, titlePaint);

            fill.setColor(0x33FFFFFF);
            canvas.drawRoundRect(echoBox, radius, radius, fill);
            String echo = typed.length() > 0 ? typed.toString() : "...";
            float ey = echoBox.centerY() - (echoPaint.descent() + echoPaint.ascent()) / 2f;
            canvas.drawText(echo, echoBox.left + 8 * density, ey, echoPaint);

            fill.setColor(0x66FF5252);
            canvas.drawRoundRect(closeBtn, radius, radius, fill);
            float by = closeBtn.centerY() - (text.descent() + text.ascent()) / 2f;
            canvas.drawText("X", closeBtn.centerX(), by, text);

            for (int i = 0; i < cells.length; i++) {
                RectF c = cells[i];
                fill.setColor(0x33FFFFFF);
                canvas.drawRoundRect(c, 8 * density, 8 * density, fill);
                float tx = c.left + 8 * density;
                canvas.drawText(cheats[i].code, tx, c.top + c.height() * 0.46f, codePaint);
                canvas.drawText(cheats[i].desc, tx, c.top + c.height() * 0.84f, descPaint);
            }
        }
    }
}
