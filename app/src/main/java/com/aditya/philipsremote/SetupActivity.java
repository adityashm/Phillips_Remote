package com.aditya.philipsremote;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Setup / Code Scanner Activity — Phase 1/2 upgrade.
 *
 * Features:
 *  • Protocol selector (NEC / RC-5 / RC-6)
 *  • Quick Scan — 25 highest-probability NEC addresses (≈37 s)
 *  • Deep Scan  — ALL 256 NEC addresses (≈6.5 min), runs on background thread
 *  • Command Sweep — tries every command 0x00–0xFF on the confirmed address
 *  • Manual address + command sliders for fine-tuning
 *  • Per-button target selector (including new Play/Pause, Next, Prev)
 *  • Save & export discovered codes
 */
public class SetupActivity extends AppCompatActivity {

    // ── Scan modes ────────────────────────────────────────────────────────
    private static final int MODE_IDLE          = 0;
    private static final int MODE_QUICK_SCAN    = 1;
    private static final int MODE_DEEP_SCAN     = 2;
    private static final int MODE_CMD_SWEEP     = 3;

    private static final long SCAN_STEP_MS = 1500; // ms between IR sends

    // ── State ─────────────────────────────────────────────────────────────
    private IrHelper irHelper;
    private Prefs prefs;

    private int scanAddress = 0;
    private int scanCommand = 0;
    private int currentMode = MODE_IDLE;
    private String currentScanTarget = "power";

    // ── UI views ──────────────────────────────────────────────────────────
    private TextView tvInstruction, tvCurrentCode, tvSavedCodes, tvProtocol;
    private Button   btnProtocolNec, btnProtocolRc5, btnProtocolRc6;
    private Button   btnSendTest, btnConfirm, btnSave;
    private Button   btnQuickScan, btnDeepScan, btnCmdSweep;
    private ProgressBar progressBar;
    private SeekBar  seekAddr, seekCmd;
    private TextView tvAddrVal, tvCmdVal;

    // ── Threading ─────────────────────────────────────────────────────────
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scanExecutor;

    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        irHelper = new IrHelper(this);
        prefs    = new Prefs(this);
        scanExecutor = Executors.newSingleThreadExecutor();

        bindViews();
        setupListeners();
        applyProtocol(prefs.getProtocol(), false);
        updateDisplay();
    }

    // ── View binding ──────────────────────────────────────────────────────
    private void bindViews() {
        tvInstruction  = findViewById(R.id.tvInstruction);
        tvCurrentCode  = findViewById(R.id.tvCurrentCode);
        tvSavedCodes   = findViewById(R.id.tvSavedCodes);
        tvProtocol     = findViewById(R.id.tvProtocol);
        btnSendTest    = findViewById(R.id.btnSendTest);
        btnConfirm     = findViewById(R.id.btnConfirm);
        btnSave        = findViewById(R.id.btnSave);
        btnQuickScan   = findViewById(R.id.btnQuickScan);
        btnDeepScan    = findViewById(R.id.btnDeepScan);
        btnCmdSweep    = findViewById(R.id.btnCmdSweep);
        btnProtocolNec = findViewById(R.id.btnProtocolNec);
        btnProtocolRc5 = findViewById(R.id.btnProtocolRc5);
        btnProtocolRc6 = findViewById(R.id.btnProtocolRc6);
        progressBar    = findViewById(R.id.progressBar);
        seekAddr       = findViewById(R.id.seekAddr);
        seekCmd        = findViewById(R.id.seekCmd);
        tvAddrVal      = findViewById(R.id.tvAddrVal);
        tvCmdVal       = findViewById(R.id.tvCmdVal);

        scanAddress = prefs.getAddr();
        scanCommand = prefs.getVolUp();
    }

    // ── Listeners ─────────────────────────────────────────────────────────
    private void setupListeners() {
        // Protocol toggles
        btnProtocolNec.setOnClickListener(v -> applyProtocol(IrProtocol.NEC, true));
        btnProtocolRc5.setOnClickListener(v -> applyProtocol(IrProtocol.RC5, true));
        btnProtocolRc6.setOnClickListener(v -> applyProtocol(IrProtocol.RC6, true));

        // Sliders
        seekAddr.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { scanAddress = p; updateCodePreview(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        seekCmd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { scanCommand = p; updateCodePreview(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        // Manual send + confirm
        btnSendTest.setOnClickListener(v -> {
            irHelper.send(scanAddress, scanCommand);
            tvCurrentCode.setText("Sent → " + fmtCode(scanAddress, scanCommand));
        });
        btnConfirm.setOnClickListener(v -> saveCurrentCode());

        // Scan buttons
        btnQuickScan.setOnClickListener(v -> onScanButtonClicked(MODE_QUICK_SCAN));
        btnDeepScan.setOnClickListener(v -> onScanButtonClicked(MODE_DEEP_SCAN));
        btnCmdSweep.setOnClickListener(v -> onScanButtonClicked(MODE_CMD_SWEEP));

        // Save
        btnSave.setOnClickListener(v -> {
            prefs.setSetupDone(true);
            Toast.makeText(this, "Codes saved! Returning to remote.", Toast.LENGTH_LONG).show();
            updateSavedCodesDisplay();
            mainHandler.postDelayed(this::finish, 1500);
        });

        setupTargetButtons();
    }

    // ── Protocol apply ────────────────────────────────────────────────────
    private void applyProtocol(String protocol, boolean userChanged) {
        stopAllScans("Protocol changed.");
        prefs.setProtocol(protocol);
        irHelper.resetToggle();

        boolean nec = IrProtocol.NEC.equals(protocol);
        boolean rc6 = IrProtocol.RC6.equals(protocol);

        seekAddr.setMax(IrProtocol.maxAddress(protocol));
        seekCmd.setMax(IrProtocol.maxCommand(protocol));

        if (userChanged) {
            scanAddress = nec ? NECEncoder.ADDR_DEFAULT
                        : rc6 ? RC6Encoder.ADDR_AUDIO
                        :        RC5Encoder.ADDR_AUDIO;
            scanCommand = nec ? NECEncoder.CMD_VOL_UP
                        : rc6 ? RC6Encoder.CMD_VOL_UP
                        :        RC5Encoder.CMD_VOL_UP;
        } else {
            scanAddress = Math.min(scanAddress, IrProtocol.maxAddress(protocol));
            scanCommand = Math.min(scanCommand, IrProtocol.maxCommand(protocol));
        }

        seekAddr.setProgress(scanAddress);
        seekCmd.setProgress(scanCommand);

        // Highlight selected protocol button
        int activeColor  = android.R.color.holo_blue_dark;
        int inactiveColor = android.R.color.darker_gray;
        btnProtocolNec.setBackgroundTintList(getColorStateList(nec  ? activeColor : inactiveColor));
        btnProtocolRc5.setBackgroundTintList(getColorStateList(!nec && !rc6 ? activeColor : inactiveColor));
        btnProtocolRc6.setBackgroundTintList(getColorStateList(rc6  ? activeColor : inactiveColor));

        tvProtocol.setText(nec ? "Protocol: NEC @ 38 kHz  ◀ Try this first for MMS8085B"
                          : rc6 ? "Protocol: RC-6 @ 36 kHz  (modern Philips soundbars)"
                          :        "Protocol: RC-5 @ 36 kHz  (classic Philips audio gear)");

        // Deep Scan only makes sense for NEC (256 addresses)
        btnDeepScan.setEnabled(nec);
        btnDeepScan.setAlpha(nec ? 1.0f : 0.4f);

        updateCodePreview();
        updateSavedCodesDisplay();
    }

    // ── Target button selector ────────────────────────────────────────────
    private void setupTargetButtons() {
        int[] ids = {
            R.id.btnTargetPower, R.id.btnTargetVolUp,  R.id.btnTargetVolDown,
            R.id.btnTargetMute,  R.id.btnTargetBassUp, R.id.btnTargetBassDown,
            R.id.btnTargetBT,    R.id.btnTargetAux,    R.id.btnTargetFM,
            R.id.btnTargetUSB,   R.id.btnTargetPlay,   R.id.btnTargetNext,
            R.id.btnTargetPrev
        };
        String[] labels = {
            "power","vol_up","vol_down","mute","bass_up","bass_down",
            "bt","aux","fm","usb","play_pause","next","prev"
        };

        for (int i = 0; i < ids.length; i++) {
            final String label = labels[i];
            Button b = findViewById(ids[i]);
            if (b == null) continue;
            b.setOnClickListener(v -> {
                currentScanTarget = label;
                scanCommand = defaultCommandFor(label);
                seekCmd.setProgress(scanCommand);
                tvInstruction.setText("Configuring: " + label.toUpperCase().replace("_", " ")
                        + "\nAdjust sliders → Send Test → Confirm ✔ when it works.");
                updateCodePreview();
            });
        }
    }

    private int defaultCommandFor(String target) {
        String p = prefs.getProtocol();
        boolean nec = IrProtocol.NEC.equals(p);
        boolean rc6 = IrProtocol.RC6.equals(p);
        switch (target) {
            case "power":      return nec ? NECEncoder.CMD_POWER     : (rc6 ? RC6Encoder.CMD_POWER     : RC5Encoder.CMD_POWER);
            case "vol_up":     return nec ? NECEncoder.CMD_VOL_UP    : (rc6 ? RC6Encoder.CMD_VOL_UP    : RC5Encoder.CMD_VOL_UP);
            case "vol_down":   return nec ? NECEncoder.CMD_VOL_DOWN  : (rc6 ? RC6Encoder.CMD_VOL_DOWN  : RC5Encoder.CMD_VOL_DOWN);
            case "mute":       return nec ? NECEncoder.CMD_MUTE      : (rc6 ? RC6Encoder.CMD_MUTE      : RC5Encoder.CMD_MUTE);
            case "bass_up":    return nec ? NECEncoder.CMD_BASS_UP   : (rc6 ? RC6Encoder.CMD_BASS_UP   : RC5Encoder.CMD_BASS_UP);
            case "bass_down":  return nec ? NECEncoder.CMD_BASS_DOWN : (rc6 ? RC6Encoder.CMD_BASS_DOWN : RC5Encoder.CMD_BASS_DOWN);
            case "bt":         return nec ? NECEncoder.CMD_BT        : (rc6 ? RC6Encoder.CMD_BT        : RC5Encoder.CMD_BT);
            case "aux":        return nec ? NECEncoder.CMD_AUX       : (rc6 ? RC6Encoder.CMD_AUX       : RC5Encoder.CMD_AUX);
            case "fm":         return nec ? NECEncoder.CMD_FM        : (rc6 ? RC6Encoder.CMD_FM        : RC5Encoder.CMD_FM);
            case "usb":        return nec ? NECEncoder.CMD_USB       : (rc6 ? RC6Encoder.CMD_USB       : RC5Encoder.CMD_USB);
            case "play_pause": return nec ? NECEncoder.CMD_PLAY_PAUSE : 0x43;
            case "next":       return nec ? NECEncoder.CMD_NEXT       : 0x40;
            case "prev":       return nec ? NECEncoder.CMD_PREV       : 0x41;
            default:           return scanCommand;
        }
    }

    // ── Save confirmed code ───────────────────────────────────────────────
    private void saveCurrentCode() {
        prefs.setAddr(scanAddress);
        switch (currentScanTarget) {
            case "power":      prefs.setPower(scanCommand);     break;
            case "vol_up":     prefs.setVolUp(scanCommand);     break;
            case "vol_down":   prefs.setVolDown(scanCommand);   break;
            case "mute":       prefs.setMute(scanCommand);      break;
            case "bass_up":    prefs.setBassUp(scanCommand);    break;
            case "bass_down":  prefs.setBassDown(scanCommand);  break;
            case "bt":         prefs.setBt(scanCommand);        break;
            case "aux":        prefs.setAux(scanCommand);       break;
            case "fm":         prefs.setFm(scanCommand);        break;
            case "usb":        prefs.setUsb(scanCommand);       break;
            case "play_pause": prefs.setPlayPause(scanCommand); break;
            case "next":       prefs.setNext(scanCommand);      break;
            case "prev":       prefs.setPrev(scanCommand);      break;
        }
        Toast.makeText(this,
            "✅ Saved " + currentScanTarget.replace("_", " ").toUpperCase()
            + " → " + fmtCode(scanAddress, scanCommand),
            Toast.LENGTH_SHORT).show();
        updateSavedCodesDisplay();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SCAN ENGINE
    // ═════════════════════════════════════════════════════════════════════

    private void onScanButtonClicked(int mode) {
        if (currentMode != MODE_IDLE) {
            // Stop whatever is running
            stopAllScans("Scan stopped by user.");
            return;
        }

        String title, message;
        String duration;

        if (mode == MODE_QUICK_SCAN) {
            duration = "~37 sec";
            title = "Quick Scan — " + NECEncoder.COMMON_ADDRESSES.length + " addresses";
            message = "Sends VOL+ on " + NECEncoder.COMMON_ADDRESSES.length + " common NEC "
                    + "addresses (" + duration + ").\n\n"
                    + "Watch your MMS8085B — when volume changes, tap STOP and note the address shown.";
        } else if (mode == MODE_DEEP_SCAN) {
            duration = "~6.5 min";
            title = "Deep Scan — All 256 NEC Addresses";
            message = "Sweeps every NEC address from 0x00 to 0xFF (" + duration + ").\n\n"
                    + "This WILL find your speaker's address if it uses NEC.\n\n"
                    + "Point phone IR at the SUBWOOFER front panel and leave it.";
        } else { // CMD_SWEEP
            duration = "~6.5 min";
            title = "Command Sweep — " + currentScanTarget.toUpperCase().replace("_", " ");
            message = "Sweeps ALL 256 commands (0x00–0xFF) at your confirmed address "
                    + "(0x" + Integer.toHexString(scanAddress) + ").\n\n"
                    + "Watch your MMS8085B — when the function triggers, tap STOP.\n\n"
                    + "Make sure you've confirmed the address first!";
        }

        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message + "\n\nPoint phone IR at SUBWOOFER front panel.")
            .setPositiveButton("▶ Start", (d, w) -> startScan(mode))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startScan(int mode) {
        currentMode = mode;
        progressBar.setVisibility(View.VISIBLE);

        int total;
        if (mode == MODE_QUICK_SCAN) {
            total = NECEncoder.COMMON_ADDRESSES.length;
            btnQuickScan.setText("⏹ Stop Quick Scan");
        } else if (mode == MODE_DEEP_SCAN) {
            total = NECEncoder.TOTAL_NEC_ADDRESSES;
            btnDeepScan.setText("⏹ Stop Deep Scan");
        } else {
            total = 256; // command sweep
            btnCmdSweep.setText("⏹ Stop Command Sweep");
        }
        progressBar.setMax(total);
        progressBar.setProgress(0);

        final int scanTotal = total;

        scanExecutor.submit(() -> {
            for (int step = 0; step < scanTotal; step++) {
                if (currentMode == MODE_IDLE) break; // stopped externally

                final int addr, cmd;
                if (mode == MODE_QUICK_SCAN) {
                    addr = NECEncoder.COMMON_ADDRESSES[step];
                    cmd  = necVolUp();
                } else if (mode == MODE_DEEP_SCAN) {
                    addr = step; // 0x00 → 0xFF
                    cmd  = necVolUp();
                } else {
                    addr = scanAddress; // use confirmed address
                    cmd  = step;       // sweep commands 0x00→0xFF
                }

                irHelper.send(addr, cmd);

                final int finalStep = step;
                final int finalAddr = addr;
                final int finalCmd  = cmd;
                mainHandler.post(() -> {
                    scanAddress = finalAddr;
                    scanCommand = finalCmd;
                    seekAddr.setProgress(finalAddr);
                    seekCmd.setProgress(finalCmd);
                    progressBar.setProgress(finalStep + 1);
                    String label = (mode == MODE_CMD_SWEEP)
                            ? "Sweeping CMD → " + fmtCode(finalAddr, finalCmd)
                            : "Scanning → " + fmtCode(finalAddr, finalCmd) + " (VOL+)";
                    tvCurrentCode.setText(label);
                    updateCodePreview();
                });

                try { Thread.sleep(SCAN_STEP_MS); } catch (InterruptedException e) { break; }
            }

            mainHandler.post(() -> {
                if (currentMode != MODE_IDLE) {
                    stopAllScans("Scan complete. If speaker responded — confirm the address/command shown.");
                }
            });
        });
    }

    private void stopAllScans(String message) {
        currentMode = MODE_IDLE;
        mainHandler.post(() -> {
            progressBar.setVisibility(View.GONE);
            btnQuickScan.setText("▶ Quick Scan (25 addr)");
            btnDeepScan.setText("▶ Deep Scan (ALL 256)");
            btnCmdSweep.setText("🔍 Command Sweep");
            tvInstruction.setText(message);
        });
    }

    /** Returns the best VOL+ command for current protocol. */
    private int necVolUp() {
        String p = prefs.getProtocol();
        if (IrProtocol.RC6.equals(p)) return RC6Encoder.CMD_VOL_UP;
        if (IrProtocol.RC5.equals(p)) return RC5Encoder.CMD_VOL_UP;
        return NECEncoder.CMD_VOL_UP;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  UI helpers
    // ═════════════════════════════════════════════════════════════════════

    private void updateDisplay() {
        seekAddr.setProgress(scanAddress);
        tvInstruction.setText(
            "NEC @ 38 kHz is pre-selected (most likely for MMS8085B India).\n"
          + "1. Tap Quick Scan — watch for any volume change on speaker.\n"
          + "2. If nothing: try Deep Scan (ALL 256 addresses).\n"
          + "3. When speaker responds → Stop → Confirm the address.\n"
          + "4. Select each button below → adjust Command → Confirm.");
        updateCodePreview();
        updateSavedCodesDisplay();
    }

    private void updateCodePreview() {
        tvAddrVal.setText("Address: " + scanAddress + "  (0x" + Integer.toHexString(scanAddress) + ")");
        tvCmdVal.setText("Command: " + scanCommand  + "  (0x" + Integer.toHexString(scanCommand)  + ")");
        tvCurrentCode.setText("Will send → " + fmtCode(scanAddress, scanCommand));
    }

    private String fmtCode(int addr, int cmd) {
        return prefs.getProtocol()
            + "  Addr:" + addr + " (0x" + Integer.toHexString(addr) + ")"
            + "  Cmd:"  + cmd  + " (0x" + Integer.toHexString(cmd)  + ")";
    }

    private void updateSavedCodesDisplay() {
        tvSavedCodes.setText(
            "Protocol: " + prefs.getProtocol()
          + "  |  Addr: " + prefs.getAddr() + " (0x" + Integer.toHexString(prefs.getAddr()) + ")\n"
          + "Power="    + prefs.getPower()    + "  Mute="    + prefs.getMute()
          + "  Vol+="   + prefs.getVolUp()    + "  Vol-="    + prefs.getVolDown() + "\n"
          + "Bass+="    + prefs.getBassUp()   + "  Bass-="   + prefs.getBassDown()
          + "  BT="     + prefs.getBt()       + "  AUX="     + prefs.getAux() + "\n"
          + "FM="       + prefs.getFm()       + "  USB="     + prefs.getUsb()
          + "  Play="   + prefs.getPlayPause()+ "  Next="    + prefs.getNext()
          + "  Prev="   + prefs.getPrev()
        );
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        currentMode = MODE_IDLE;
        if (scanExecutor != null) scanExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
