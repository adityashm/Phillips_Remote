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

/**
 * Setup / Code Scanner Activity
 *
 * Step 1: Pick protocol (NEC first for MMS8085B, RC-5 as fallback).
 * Step 2: Auto-scan addresses with VOL+ to find a response.
 * Step 3: Fine-tune each button command and save.
 */
public class SetupActivity extends AppCompatActivity {

    private IrHelper irHelper;
    private Prefs prefs;

    private int scanAddress = 0;
    private int scanCommand = 0;
    private int scanStep = 0;
    private String currentScanTarget = "power";

    private TextView tvInstruction, tvCurrentCode, tvSavedCodes, tvProtocol;
    private Button btnProtocolNec, btnProtocolRc5, btnProtocolRc6;
    private Button btnSendTest, btnConfirm, btnNext, btnSave;
    private ProgressBar progressBar;
    private SeekBar seekAddr, seekCmd;
    private TextView tvAddrVal, tvCmdVal;

    private boolean autoScanRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        irHelper = new IrHelper(this);
        prefs = new Prefs(this);

        bindViews();
        setupListeners();
        applyProtocol(prefs.getProtocol(), false);
        updateDisplay();
    }

    private void bindViews() {
        tvInstruction   = findViewById(R.id.tvInstruction);
        tvCurrentCode   = findViewById(R.id.tvCurrentCode);
        tvSavedCodes    = findViewById(R.id.tvSavedCodes);
        tvProtocol      = findViewById(R.id.tvProtocol);
        btnSendTest     = findViewById(R.id.btnSendTest);
        btnConfirm      = findViewById(R.id.btnConfirm);
        btnNext         = findViewById(R.id.btnNext);
        btnSave         = findViewById(R.id.btnSave);
        btnProtocolNec  = findViewById(R.id.btnProtocolNec);
        btnProtocolRc5  = findViewById(R.id.btnProtocolRc5);
        btnProtocolRc6  = findViewById(R.id.btnProtocolRc6);
        progressBar     = findViewById(R.id.progressBar);
        seekAddr        = findViewById(R.id.seekAddr);
        seekCmd         = findViewById(R.id.seekCmd);
        tvAddrVal       = findViewById(R.id.tvAddrVal);
        tvCmdVal        = findViewById(R.id.tvCmdVal);

        scanAddress = prefs.getAddr();
        scanCommand = prefs.getVolUp();
    }

    private void setupListeners() {
        btnProtocolNec.setOnClickListener(v -> applyProtocol(IrProtocol.NEC, true));
        btnProtocolRc5.setOnClickListener(v -> applyProtocol(IrProtocol.RC5, true));
        btnProtocolRc6.setOnClickListener(v -> applyProtocol(IrProtocol.RC6, true));

        seekAddr.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                scanAddress = p;
                updateCodePreview();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        seekCmd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                scanCommand = p;
                updateCodePreview();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        btnSendTest.setOnClickListener(v -> {
            irHelper.send(scanAddress, scanCommand);
            tvCurrentCode.setText("Sent → " + formatCode(scanAddress, scanCommand));
        });

        btnConfirm.setOnClickListener(v -> saveCurrentCode());

        btnNext.setOnClickListener(v -> runAutoScan());

        btnSave.setOnClickListener(v -> {
            prefs.setSetupDone(true);
            Toast.makeText(this, "Codes saved! Going back to remote.", Toast.LENGTH_LONG).show();
            updateSavedCodesDisplay();
            handler.postDelayed(this::finish, 1500);
        });

        setupTargetButtons();
    }

    private void applyProtocol(String protocol, boolean userChanged) {
        prefs.setProtocol(protocol);
        irHelper.resetToggle();

        boolean nec = IrProtocol.NEC.equals(protocol);
        boolean rc6 = IrProtocol.RC6.equals(protocol);
        seekAddr.setMax(IrProtocol.maxAddress(protocol));
        seekCmd.setMax(IrProtocol.maxCommand(protocol));

        if (userChanged) {
            if (nec) {
                scanAddress = NECEncoder.ADDR_DEFAULT;
                scanCommand = NECEncoder.CMD_VOL_UP;
            } else if (rc6) {
                scanAddress = RC6Encoder.ADDR_AUDIO;
                scanCommand = RC6Encoder.CMD_VOL_UP;
            } else {
                scanAddress = RC5Encoder.ADDR_AUDIO;
                scanCommand = RC5Encoder.CMD_VOL_UP;
            }
        } else {
            scanAddress = Math.min(scanAddress, IrProtocol.maxAddress(protocol));
            scanCommand = Math.min(scanCommand, IrProtocol.maxCommand(protocol));
        }

        seekAddr.setProgress(scanAddress);
        seekCmd.setProgress(scanCommand);

        btnProtocolNec.setBackgroundTintList(getColorStateList(
                nec ? android.R.color.holo_blue_dark : android.R.color.darker_gray));
        btnProtocolRc5.setBackgroundTintList(getColorStateList(
                rc6 ? android.R.color.darker_gray : (nec ? android.R.color.darker_gray : android.R.color.holo_blue_dark)));
        btnProtocolRc6.setBackgroundTintList(getColorStateList(
                rc6 ? android.R.color.holo_blue_dark : android.R.color.darker_gray));

        tvProtocol.setText(nec
                ? "Protocol: NEC @ 38 kHz (try this first for MMS8085B)"
                : rc6
                    ? "Protocol: RC-6 @ 36 kHz (modern Philips audio)"
                    : "Protocol: RC-5 @ 36 kHz (European Philips gear)");

        btnNext.setText(nec
                ? "▶ Auto-Scan NEC Addresses"
                : rc6
                    ? "▶ Auto-Scan RC-6 (addr 16)"
                    : "▶ Auto-Scan RC-5 Addresses (0–31)");

        updateCodePreview();
        updateSavedCodesDisplay();
    }

    private void setupTargetButtons() {
        int[] ids = {
            R.id.btnTargetPower, R.id.btnTargetVolUp, R.id.btnTargetVolDown,
            R.id.btnTargetMute, R.id.btnTargetBassUp, R.id.btnTargetBassDown,
            R.id.btnTargetBT, R.id.btnTargetAux, R.id.btnTargetFM, R.id.btnTargetUSB
        };
        String[] labels = {
            "power","vol_up","vol_down","mute","bass_up","bass_down","bt","aux","fm","usb"
        };

        for (int i = 0; i < ids.length; i++) {
            final String label = labels[i];
            Button b = findViewById(ids[i]);
            if (b == null) continue;
            b.setOnClickListener(v -> {
                currentScanTarget = label;
                scanCommand = defaultCommandFor(label);
                seekCmd.setProgress(scanCommand);
                tvInstruction.setText("Now scanning for: " + label.toUpperCase().replace("_"," ")
                        + "\nAdjust sliders until button works, then tap CONFIRM ✔");
                updateCodePreview();
            });
        }
    }

    private int defaultCommandFor(String target) {
        String protocol = prefs.getProtocol();
        boolean nec = IrProtocol.NEC.equals(protocol);
        boolean rc6 = IrProtocol.RC6.equals(protocol);
        switch (target) {
            case "power":    return nec ? NECEncoder.CMD_POWER     : (rc6 ? RC6Encoder.CMD_POWER     : RC5Encoder.CMD_POWER);
            case "vol_up":   return nec ? NECEncoder.CMD_VOL_UP   : (rc6 ? RC6Encoder.CMD_VOL_UP   : RC5Encoder.CMD_VOL_UP);
            case "vol_down": return nec ? NECEncoder.CMD_VOL_DOWN : (rc6 ? RC6Encoder.CMD_VOL_DOWN : RC5Encoder.CMD_VOL_DOWN);
            case "mute":     return nec ? NECEncoder.CMD_MUTE     : (rc6 ? RC6Encoder.CMD_MUTE     : RC5Encoder.CMD_MUTE);
            case "bass_up":  return nec ? NECEncoder.CMD_BASS_UP  : (rc6 ? RC6Encoder.CMD_BASS_UP  : RC5Encoder.CMD_BASS_UP);
            case "bass_down":return nec ? NECEncoder.CMD_BASS_DOWN: (rc6 ? RC6Encoder.CMD_BASS_DOWN: RC5Encoder.CMD_BASS_DOWN);
            case "bt":       return nec ? NECEncoder.CMD_BT       : (rc6 ? RC6Encoder.CMD_BT       : RC5Encoder.CMD_BT);
            case "aux":      return nec ? NECEncoder.CMD_AUX      : (rc6 ? RC6Encoder.CMD_AUX      : RC5Encoder.CMD_AUX);
            case "fm":       return nec ? NECEncoder.CMD_FM       : (rc6 ? RC6Encoder.CMD_FM       : RC5Encoder.CMD_FM);
            case "usb":      return nec ? NECEncoder.CMD_USB      : (rc6 ? RC6Encoder.CMD_USB      : RC5Encoder.CMD_USB);
            default:         return scanCommand;
        }
    }

    private void saveCurrentCode() {
        switch (currentScanTarget) {
            case "power":    prefs.setPower(scanCommand);    prefs.setAddr(scanAddress); break;
            case "vol_up":   prefs.setVolUp(scanCommand);   prefs.setAddr(scanAddress); break;
            case "vol_down": prefs.setVolDown(scanCommand); prefs.setAddr(scanAddress); break;
            case "mute":     prefs.setMute(scanCommand);    prefs.setAddr(scanAddress); break;
            case "bass_up":  prefs.setBassUp(scanCommand);  prefs.setAddr(scanAddress); break;
            case "bass_down":prefs.setBassDown(scanCommand);prefs.setAddr(scanAddress); break;
            case "bt":       prefs.setBt(scanCommand);      prefs.setAddr(scanAddress); break;
            case "aux":      prefs.setAux(scanCommand);     prefs.setAddr(scanAddress); break;
            case "fm":       prefs.setFm(scanCommand);      prefs.setAddr(scanAddress); break;
            case "usb":      prefs.setUsb(scanCommand);     prefs.setAddr(scanAddress); break;
        }
        Toast.makeText(this,
            "✅ Saved " + currentScanTarget.replace("_"," ").toUpperCase()
            + " → " + formatCode(scanAddress, scanCommand),
            Toast.LENGTH_SHORT).show();
        updateSavedCodesDisplay();
    }

    private void runAutoScan() {
        if (autoScanRunning) {
            stopAutoScan("Scan stopped.");
            return;
        }

        boolean nec = IrProtocol.NEC.equals(prefs.getProtocol());
        boolean rc6 = IrProtocol.RC6.equals(prefs.getProtocol());
        String message = nec
                ? "Sends VOL+ on common NEC addresses (0x00, 0x01, 0xFF, …) every 1.5 s.\n\n"
                + "Watch your MMS8085B — when volume changes, tap STOP and note the address.\n\n"
                + "If nothing responds, use the Address slider to try other values (0–255)."
                : rc6
                    ? "Sends RC-6 VOL+ (addr 0x10, cmd 0x10) repeatedly.\n\n"
                    + "RC-6 is used by recent Philips audio devices (MMS8085B, soundbars).\n\n"
                    + "If VOL+ works, adjust Command slider for other buttons, Address stays at 16."
                    : "Sends VOL+ on RC-5 addresses 0–31 every 1.5 s.\n\n"
                        + "Watch your MMS8085B — when volume changes, tap STOP and note the address.";

        new AlertDialog.Builder(this)
            .setTitle("Auto Address Scan")
            .setMessage(message + "\n\nPoint your phone IR at the SUBWOOFER front panel.")
            .setPositiveButton("Start", (d, w) -> startAutoScan())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startAutoScan() {
        autoScanRunning = true;
        scanStep = 0;
        btnNext.setText("⏹ Stop Scan");
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setMax(scanTotalSteps());
        doNextScanStep();
    }

    private void stopAutoScan(String message) {
        autoScanRunning = false;
        boolean nec = IrProtocol.NEC.equals(prefs.getProtocol());
        boolean rc6 = IrProtocol.RC6.equals(prefs.getProtocol());
        btnNext.setText(nec
                ? "▶ Auto-Scan NEC Addresses"
                : rc6
                    ? "▶ Auto-Scan RC-6 (addr 16)"
                    : "▶ Auto-Scan RC-5 Addresses (0–31)");
        progressBar.setVisibility(View.GONE);
        tvInstruction.setText(message);
    }

    private int scanTotalSteps() {
        String protocol = prefs.getProtocol();
        if (IrProtocol.NEC.equals(protocol)) {
            return NECEncoder.COMMON_ADDRESSES.length;
        }
        if (IrProtocol.RC6.equals(protocol)) {
            return 1; // Just test address 16 (audio), use manual sliders to fine-tune
        }
        return 32; // RC5 0-31
    }

    private int addressForScanStep(int step) {
        String protocol = prefs.getProtocol();
        if (IrProtocol.NEC.equals(protocol)) {
            return NECEncoder.COMMON_ADDRESSES[step];
        }
        if (IrProtocol.RC6.equals(protocol)) {
            return RC6Encoder.ADDR_AUDIO; // Always address 16
        }
        return step; // RC5
    }

    private void doNextScanStep() {
        if (!autoScanRunning || scanStep >= scanTotalSteps()) {
            stopAutoScan("Scan complete. If the speaker responded, confirm the address shown.");
            return;
        }

        scanAddress = addressForScanStep(scanStep);
        String protocol = prefs.getProtocol();
        int cmd;
        if (IrProtocol.NEC.equals(protocol)) {
            cmd = NECEncoder.CMD_VOL_UP;
        } else if (IrProtocol.RC6.equals(protocol)) {
            cmd = RC6Encoder.CMD_VOL_UP;
        } else {
            cmd = RC5Encoder.CMD_VOL_UP;
        }

        irHelper.send(scanAddress, cmd);
        tvCurrentCode.setText("Scanning → " + formatCode(scanAddress, cmd) + " (VOL+)");
        seekAddr.setProgress(scanAddress);
        progressBar.setProgress(scanStep + 1);
        scanStep++;
        handler.postDelayed(this::doNextScanStep, 1500);
    }

    private void updateDisplay() {
        seekAddr.setProgress(scanAddress);
        tvAddrVal.setText("Address: " + scanAddress + " (0x" + Integer.toHexString(scanAddress) + ")");
        tvCmdVal.setText("Command: " + scanCommand + " (0x" + Integer.toHexString(scanCommand) + ")");
        String protocol = prefs.getProtocol();
        tvInstruction.setText(
            "NEC @ 38 kHz is pre-selected (most likely for MMS8085B India).\n" +
            "Tap Auto-Scan NEC Addresses to find the right address.\n" +
            "Point the phone IR at the SUBWOOFER front panel.\n" +
            "Select each button → adjust Command → CONFIRM."
        );
        updateCodePreview();
        updateSavedCodesDisplay();
    }

    private void updateCodePreview() {
        tvAddrVal.setText("Address: " + scanAddress + " (0x" + Integer.toHexString(scanAddress) + ")");
        tvCmdVal.setText("Command: " + scanCommand + " (0x" + Integer.toHexString(scanCommand) + ")");
        tvCurrentCode.setText("Will send → " + formatCode(scanAddress, scanCommand));
    }

    private String formatCode(int address, int command) {
        return prefs.getProtocol() + " Addr:" + address + " (0x" + Integer.toHexString(address) + ")"
                + " Cmd:" + command + " (0x" + Integer.toHexString(command) + ")";
    }

    private void updateSavedCodesDisplay() {
        tvSavedCodes.setText(
            "Saved: " + prefs.getProtocol()
            + " | Addr=" + prefs.getAddr() + " (0x" + Integer.toHexString(prefs.getAddr()) + ")"
            + " | Power=" + prefs.getPower()
            + " | Mute=" + prefs.getMute()
            + " | Vol+=" + prefs.getVolUp()
            + " | Vol-=" + prefs.getVolDown() + "\n"
            + "Bass+=" + prefs.getBassUp() + " | Bass-=" + prefs.getBassDown()
            + " | BT=" + prefs.getBt() + " | AUX=" + prefs.getAux()
            + " | FM=" + prefs.getFm() + " | USB=" + prefs.getUsb()
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoScanRunning = false;
        handler.removeCallbacksAndMessages(null);
    }
}
