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
 * Step 1: Scan addresses (0–31) with VOL+ command to find which address
 *         makes the MMS8085B respond (volume increases).
 *
 * Step 2: Once address found, fine-tune individual commands.
 *
 * Step 3: Save confirmed codes.
 */
public class SetupActivity extends AppCompatActivity {

    private IrHelper irHelper;
    private Prefs prefs;

    // Current scan state
    private int scanAddress = 0;
    private int scanCommand = 0;
    private String currentScanTarget = "power"; // which button we're scanning for

    private TextView tvInstruction, tvCurrentCode, tvSavedCodes;
    private Button btnSendTest, btnConfirm, btnNext, btnSave;
    private ProgressBar progressBar;

    // Manual seekbars for fine control
    private SeekBar seekAddr, seekCmd;
    private TextView tvAddrVal, tvCmdVal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        irHelper = new IrHelper(this);
        prefs = new Prefs(this);

        bindViews();
        setupListeners();
        updateDisplay();
    }

    private void bindViews() {
        tvInstruction   = findViewById(R.id.tvInstruction);
        tvCurrentCode   = findViewById(R.id.tvCurrentCode);
        tvSavedCodes    = findViewById(R.id.tvSavedCodes);
        btnSendTest     = findViewById(R.id.btnSendTest);
        btnConfirm      = findViewById(R.id.btnConfirm);
        btnNext         = findViewById(R.id.btnNext);
        btnSave         = findViewById(R.id.btnSave);
        progressBar     = findViewById(R.id.progressBar);
        seekAddr        = findViewById(R.id.seekAddr);
        seekCmd         = findViewById(R.id.seekCmd);
        tvAddrVal       = findViewById(R.id.tvAddrVal);
        tvCmdVal        = findViewById(R.id.tvCmdVal);

        seekAddr.setMax(31);
        seekCmd.setMax(127);
        scanAddress = prefs.getAddr();
        scanCommand = prefs.getVolUp(); // default to VOL+
        seekAddr.setProgress(scanAddress);
        seekCmd.setProgress(scanCommand);
    }

    private void setupListeners() {

        // SeekBar: Address
        seekAddr.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                scanAddress = p;
                tvAddrVal.setText("Address: " + p);
                tvCurrentCode.setText("Will send → Addr:" + scanAddress + " Cmd:" + scanCommand);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        // SeekBar: Command
        seekCmd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                scanCommand = p;
                tvCmdVal.setText("Command: " + p);
                tvCurrentCode.setText("Will send → Addr:" + scanAddress + " Cmd:" + scanCommand);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        // Send the current code once
        btnSendTest.setOnClickListener(v -> {
            irHelper.send(scanAddress, scanCommand);
            tvCurrentCode.setText("Sent → Addr:" + scanAddress + " Cmd:" + scanCommand);
        });

        // Confirm this code works for current target button
        btnConfirm.setOnClickListener(v -> saveCurrentCode());

        // Auto-scan: cycle through all addresses (0–31) sending VOL+ every 1.5s
        btnNext.setOnClickListener(v -> runAutoScan());

        // Save and go back
        btnSave.setOnClickListener(v -> {
            prefs.setSetupDone(true);
            Toast.makeText(this, "Codes saved! Going back to remote.", Toast.LENGTH_LONG).show();
            updateSavedCodesDisplay();
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
        });

        // Instruction button to switch scan target
        setupTargetButtons();
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
        int[] defaultCmds = {
            RC5Encoder.CMD_POWER, RC5Encoder.CMD_VOL_UP, RC5Encoder.CMD_VOL_DOWN,
            RC5Encoder.CMD_MUTE, RC5Encoder.CMD_BASS_UP, RC5Encoder.CMD_BASS_DOWN,
            RC5Encoder.CMD_BT, RC5Encoder.CMD_AUX, RC5Encoder.CMD_FM, RC5Encoder.CMD_USB
        };
        for (int i = 0; i < ids.length; i++) {
            final String label = labels[i];
            final int defCmd  = defaultCmds[i];
            Button b = findViewById(ids[i]);
            if (b == null) continue;
            b.setOnClickListener(v -> {
                currentScanTarget = label;
                scanCommand = defCmd;
                seekCmd.setProgress(scanCommand);
                tvInstruction.setText("Now scanning for: " + label.toUpperCase().replace("_"," ")
                        + "\nAdjust sliders until button works, then tap CONFIRM ✔");
                tvCurrentCode.setText("Will send → Addr:" + scanAddress + " Cmd:" + scanCommand);
            });
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
            + " → Addr:" + scanAddress + " Cmd:" + scanCommand,
            Toast.LENGTH_SHORT).show();
        updateSavedCodesDisplay();
    }

    private boolean autoScanRunning = false;

    private void runAutoScan() {
        if (autoScanRunning) {
            autoScanRunning = false;
            btnNext.setText("▶ Auto-Scan Addresses");
            progressBar.setVisibility(View.GONE);
            return;
        }
        autoScanRunning = true;
        btnNext.setText("⏹ Stop Scan");
        progressBar.setVisibility(View.VISIBLE);
        scanAddress = 0;

        new AlertDialog.Builder(this)
            .setTitle("Auto Address Scan")
            .setMessage("The app will send Addr=0..31 with VOL+ every 1.5 seconds.\n\n" +
                        "Watch your MMS8085B — when the volume changes, " +
                        "tap STOP and note the address shown.\n\n" +
                        "Point your phone IR at the SUBWOOFER front panel.")
            .setPositiveButton("Start", (d, w) -> doNextScanStep())
            .setNegativeButton("Cancel", (d, w) -> { autoScanRunning = false; })
            .show();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private void doNextScanStep() {
        if (!autoScanRunning || scanAddress > 31) {
            autoScanRunning = false;
            btnNext.setText("▶ Auto-Scan Addresses");
            progressBar.setVisibility(View.GONE);
            tvInstruction.setText("Scan complete. If the speaker responded, confirm the address shown.");
            return;
        }
        int cmd = RC5Encoder.CMD_VOL_UP; // use VOL+ as the test command
        irHelper.send(scanAddress, cmd);
        tvCurrentCode.setText("Scanning → Addr:" + scanAddress + " Cmd:" + cmd + " (VOL+)");
        seekAddr.setProgress(scanAddress);
        progressBar.setProgress((scanAddress * 100) / 31);
        scanAddress++;
        handler.postDelayed(this::doNextScanStep, 1500);
    }

    private void updateDisplay() {
        scanAddress = prefs.getAddr();
        seekAddr.setProgress(scanAddress);
        tvAddrVal.setText("Address: " + scanAddress);
        tvCmdVal.setText("Command: " + scanCommand);
        tvCurrentCode.setText("Will send → Addr:" + scanAddress + " Cmd:" + scanCommand);
        tvInstruction.setText(
            "STEP 1: Tap Auto-Scan to find the correct address.\n" +
            "STEP 2: Select each button below and adjust Command slider until it works.\n" +
            "STEP 3: Tap CONFIRM ✔ after each button, then SAVE ALL."
        );
        updateSavedCodesDisplay();
    }

    private void updateSavedCodesDisplay() {
        tvSavedCodes.setText(
            "Saved codes:\n" +
            "Addr=" + prefs.getAddr() + " | Power=" + prefs.getPower() +
            " | Mute=" + prefs.getMute() + " | Vol+=" + prefs.getVolUp() +
            " | Vol-=" + prefs.getVolDown() + "\n" +
            "Bass+=" + prefs.getBassUp() + " | Bass-=" + prefs.getBassDown() +
            " | BT=" + prefs.getBt() + " | AUX=" + prefs.getAux() +
            " | FM=" + prefs.getFm() + " | USB=" + prefs.getUsb()
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoScanRunning = false;
        handler.removeCallbacksAndMessages(null);
    }
}
