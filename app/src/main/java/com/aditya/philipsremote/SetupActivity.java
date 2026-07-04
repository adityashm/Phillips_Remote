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
    private Button btnSendTest, btnConfirm, btnNext, btnSave, btnProtocolNec, btnProtocolRc5;
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
        seekAddr.setMax(IrProtocol.maxAddress(protocol));
        seekCmd.setMax(IrProtocol.maxCommand(protocol));

        if (userChanged) {
            scanAddress = nec ? NECEncoder.ADDR_DEFAULT : RC5Encoder.ADDR_AUDIO;
            scanCommand = nec ? NECEncoder.CMD_VOL_UP : RC5Encoder.CMD_VOL_UP;
        } else {
            scanAddress = Math.min(scanAddress, IrProtocol.maxAddress(protocol));
            scanCommand = Math.min(scanCommand, IrProtocol.maxCommand(protocol));
        }

        seekAddr.setProgress(scanAddress);
        seekCmd.setProgress(scanCommand);

        btnProtocolNec.setBackgroundTintList(getColorStateList(
                nec ? android.R.color.holo_blue_dark : android.R.color.darker_gray));
        btnProtocolRc5.setBackgroundTintList(getColorStateList(
                nec ? android.R.color.darker_gray : android.R.color.holo_blue_dark));

        tvProtocol.setText(nec
                ? "Protocol: NEC @ 38 kHz (try this first for MMS8085B)"
                : "Protocol: RC-5 @ 36 kHz (European Philips gear)");

        btnNext.setText(nec
                ? "▶ Auto-Scan NEC Addresses"
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
        boolean nec = IrProtocol.NEC.equals(prefs.getProtocol());
        switch (target) {
            case "power":    return nec ? NECEncoder.CMD_POWER     : RC5Encoder.CMD_POWER;
            case "vol_up":   return nec ? NECEncoder.CMD_VOL_UP   : RC5Encoder.CMD_VOL_UP;
            case "vol_down": return nec ? NECEncoder.CMD_VOL_DOWN : RC5Encoder.CMD_VOL_DOWN;
            case "mute":     return nec ? NECEncoder.CMD_MUTE     : RC5Encoder.CMD_MUTE;
            case "bass_up":  return nec ? NECEncoder.CMD_BASS_UP  : RC5Encoder.CMD_BASS_UP;
            case "bass_down":return nec ? NECEncoder.CMD_BASS_DOWN: RC5Encoder.CMD_BASS_DOWN;
            case "bt":       return nec ? NECEncoder.CMD_BT       : RC5Encoder.CMD_BT;
            case "aux":      return nec ? NECEncoder.CMD_AUX      : RC5Encoder.CMD_AUX;
            case "fm":       return nec ? NECEncoder.CMD_FM       : RC5Encoder.CMD_FM;
            case "usb":      return nec ? NECEncoder.CMD_USB      : RC5Encoder.CMD_USB;
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
        String message = nec
                ? "Sends VOL+ on common NEC addresses (0x00, 0x01, 0xFF, …) every 1.5 s.\n\n"
                + "Watch your MMS8085B — when volume changes, tap STOP and note the address.\n\n"
                + "If nothing responds, use the Address slider to try other values (0–255)."
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
        btnNext.setText(IrProtocol.NEC.equals(prefs.getProtocol())
                ? "▶ Auto-Scan NEC Addresses"
                : "▶ Auto-Scan RC-5 Addresses (0–31)");
        progressBar.setVisibility(View.GONE);
        tvInstruction.setText(message);
    }

    private int scanTotalSteps() {
        return IrProtocol.NEC.equals(prefs.getProtocol())
                ? NECEncoder.COMMON_ADDRESSES.length
                : 32;
    }

    private int addressForScanStep(int step) {
        if (IrProtocol.NEC.equals(prefs.getProtocol())) {
            return NECEncoder.COMMON_ADDRESSES[step];
        }
        return step;
    }

    private void doNextScanStep() {
        if (!autoScanRunning || scanStep >= scanTotalSteps()) {
            stopAutoScan("Scan complete. If the speaker responded, confirm the address shown.");
            return;
        }

        scanAddress = addressForScanStep(scanStep);
        int cmd = IrProtocol.NEC.equals(prefs.getProtocol())
                ? NECEncoder.CMD_VOL_UP
                : RC5Encoder.CMD_VOL_UP;

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
        tvInstruction.setText(
            "STEP 1: Keep NEC selected (default) and run Auto-Scan.\n" +
            "STEP 2: Select each button below and adjust Command until it works.\n" +
            "STEP 3: Tap CONFIRM ✔ after each button, then SAVE ALL."
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
