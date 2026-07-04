package com.aditya.philipsremote;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Main Remote Activity — Phase 2/3 upgrade.
 *
 * New features:
 *  • Play/Pause, Next, Previous track buttons
 *  • Numeric keypad (0–9) for USB track select / FM freq
 *  • Hold-to-repeat for Vol+, Vol-, Bass+, Bass- (fires every 400ms while held)
 *  • Visual flash animation on button press
 *  • Haptic feedback on every IR send
 *  • Source-mode tabs: AUDIO / PLAYBACK / NUMPAD
 *  • Updated status showing NEC default
 *  • Share/Export discovered codes
 */
public class MainActivity extends AppCompatActivity {

    private IrHelper irHelper;
    private Prefs    prefs;

    // Hold-to-repeat
    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private Runnable repeatRunnable;
    private static final int REPEAT_INITIAL_DELAY_MS = 500;
    private static final int REPEAT_INTERVAL_MS      = 350;

    // Tab state
    private static final int TAB_AUDIO     = 0;
    private static final int TAB_PLAYBACK  = 1;
    private static final int TAB_NUMPAD    = 2;
    private int currentTab = TAB_AUDIO;

    // Tab containers
    private View panelAudio, panelPlayback, panelNumpad;
    private Button tabAudio, tabPlayback, tabNumpad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        irHelper = new IrHelper(this);
        prefs    = new Prefs(this);

        setupStatus();
        setupTabs();
        setupButtons();

        if (!prefs.isSetupDone()) {
            Toast.makeText(this,
                "First time? Tap ⚙ Setup → run Quick Scan to find your speaker's IR code.",
                Toast.LENGTH_LONG).show();
        }
    }

    // ── Status bar ────────────────────────────────────────────────────────
    private void setupStatus() {
        TextView tv = findViewById(R.id.tvStatus);
        if (!irHelper.hasIrBlaster()) {
            tv.setText("⚠️ No IR Blaster detected on this device");
            tv.setTextColor(getColor(android.R.color.holo_red_light));
        } else {
            tv.setText("✅ IR Ready  •  " + prefs.getProtocol()
                + "  •  Addr: 0x" + Integer.toHexString(prefs.getAddr()).toUpperCase()
                + "  •  Point at subwoofer ▸");
        }
    }

    // ── Tab navigation ────────────────────────────────────────────────────
    private void setupTabs() {
        panelAudio    = findViewById(R.id.panelAudio);
        panelPlayback = findViewById(R.id.panelPlayback);
        panelNumpad   = findViewById(R.id.panelNumpad);
        tabAudio      = findViewById(R.id.tabAudio);
        tabPlayback   = findViewById(R.id.tabPlayback);
        tabNumpad     = findViewById(R.id.tabNumpad);

        tabAudio.setOnClickListener(v -> switchTab(TAB_AUDIO));
        tabPlayback.setOnClickListener(v -> switchTab(TAB_PLAYBACK));
        tabNumpad.setOnClickListener(v -> switchTab(TAB_NUMPAD));

        switchTab(TAB_AUDIO); // default
    }

    private void switchTab(int tab) {
        currentTab = tab;
        panelAudio.setVisibility(tab == TAB_AUDIO    ? View.VISIBLE : View.GONE);
        panelPlayback.setVisibility(tab == TAB_PLAYBACK ? View.VISIBLE : View.GONE);
        panelNumpad.setVisibility(tab == TAB_NUMPAD  ? View.VISIBLE : View.GONE);

        int activeColor   = 0xFF1565C0;
        int inactiveColor = 0xFF2A2D36;
        tabAudio.setBackgroundColor(tab == TAB_AUDIO    ? activeColor : inactiveColor);
        tabPlayback.setBackgroundColor(tab == TAB_PLAYBACK ? activeColor : inactiveColor);
        tabNumpad.setBackgroundColor(tab == TAB_NUMPAD  ? activeColor : inactiveColor);
    }

    // ── Button wiring ─────────────────────────────────────────────────────
    private void setupButtons() {
        // Power + Mute (tap only)
        irBtn(R.id.btnPower, () -> prefs.getPower());
        irBtn(R.id.btnMute,  () -> prefs.getMute());

        // Source buttons (tap only)
        irBtn(R.id.btnBT,  () -> prefs.getBt());
        irBtn(R.id.btnAux, () -> prefs.getAux());
        irBtn(R.id.btnFM,  () -> prefs.getFm());
        irBtn(R.id.btnUSB, () -> prefs.getUsb());

        // Volume — hold to repeat
        irHoldBtn(R.id.btnVolUp,   () -> prefs.getVolUp());
        irHoldBtn(R.id.btnVolDown, () -> prefs.getVolDown());

        // Bass — hold to repeat
        irHoldBtn(R.id.btnBassUp,   () -> prefs.getBassUp());
        irHoldBtn(R.id.btnBassDown, () -> prefs.getBassDown());

        // Playback (tap only)
        irBtn(R.id.btnPlayPause, () -> prefs.getPlayPause());
        irBtn(R.id.btnNext,      () -> prefs.getNext());
        irBtn(R.id.btnPrev,      () -> prefs.getPrev());

        // Numeric keypad
        int[] numIds = {
            R.id.btnNum0, R.id.btnNum1, R.id.btnNum2,
            R.id.btnNum3, R.id.btnNum4, R.id.btnNum5,
            R.id.btnNum6, R.id.btnNum7, R.id.btnNum8,
            R.id.btnNum9
        };
        for (int i = 0; i < numIds.length; i++) {
            final int digit = i;
            irBtn(numIds[i], () -> prefs.getNum(digit));
        }

        // Setup button
        findViewById(R.id.btnSetup).setOnClickListener(v ->
            startActivity(new Intent(this, SetupActivity.class)));
    }

    // ── IR helper — tap only ──────────────────────────────────────────────
    /** Wires a button to send one IR frame on tap, with haptic + flash. */
    private void irBtn(int viewId, CommandGetter getter) {
        View v = findViewById(viewId);
        if (v == null) return;
        v.setOnClickListener(view -> {
            irHelper.send(prefs.getAddr(), getter.get());
            flash(view);
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        });
    }

    // ── IR helper — hold to repeat ────────────────────────────────────────
    /** Wires a button to fire immediately on press, then repeat every REPEAT_INTERVAL_MS
     *  for as long as the finger is held. Stops on release. */
    private void irHoldBtn(int viewId, CommandGetter getter) {
        View v = findViewById(viewId);
        if (v == null) return;
        v.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // First send immediately
                    irHelper.send(prefs.getAddr(), getter.get());
                    flash(view);
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    // Schedule repeating
                    repeatRunnable = new Runnable() {
                        @Override public void run() {
                            irHelper.send(prefs.getAddr(), getter.get());
                            flash(view);
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            repeatHandler.postDelayed(this, REPEAT_INTERVAL_MS);
                        }
                    };
                    repeatHandler.postDelayed(repeatRunnable, REPEAT_INITIAL_DELAY_MS);
                    view.setPressed(true);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    repeatHandler.removeCallbacks(repeatRunnable);
                    view.setPressed(false);
                    return true;
            }
            return false;
        });
    }

    // ── Visual flash on button press ──────────────────────────────────────
    private void flash(View v) {
        v.animate().alpha(0.4f).setDuration(60)
            .withEndAction(() -> v.animate().alpha(1.0f).setDuration(80).start())
            .start();
    }

    // ── Lambda helper interface ───────────────────────────────────────────
    @FunctionalInterface
    interface CommandGetter { int get(); }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        setupStatus(); // refresh addr display after returning from Setup
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        repeatHandler.removeCallbacksAndMessages(null);
    }
}
