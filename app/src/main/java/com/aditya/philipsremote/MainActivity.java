package com.aditya.philipsremote;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private IrHelper irHelper;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        irHelper = new IrHelper(this);
        prefs = new Prefs(this);

        // Check IR blaster availability
        TextView statusText = findViewById(R.id.tvStatus);
        if (!irHelper.hasIrBlaster()) {
            statusText.setText("⚠️ No IR Blaster detected");
            statusText.setTextColor(getColor(android.R.color.holo_red_light));
        } else {
            statusText.setText("✅ IR Blaster Ready — Philips MMS8085B");
        }

        // Show first-run hint
        if (!prefs.isSetupDone()) {
            Toast.makeText(this,
                    "Tip: If buttons don't work, tap ⚙ Setup to scan for correct IR codes",
                    Toast.LENGTH_LONG).show();
        }

        setupButtons();
    }

    private void setupButtons() {

        // --- POWER ---
        findViewById(R.id.btnPower).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getPower());
            vibrate(v);
        });

        // --- MUTE ---
        findViewById(R.id.btnMute).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getMute());
            vibrate(v);
        });

        // --- VOL+ ---
        findViewById(R.id.btnVolUp).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getVolUp());
            vibrate(v);
        });

        // --- VOL- ---
        findViewById(R.id.btnVolDown).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getVolDown());
            vibrate(v);
        });

        // --- BASS+ ---
        findViewById(R.id.btnBassUp).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getBassUp());
            vibrate(v);
        });

        // --- BASS- ---
        findViewById(R.id.btnBassDown).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getBassDown());
            vibrate(v);
        });

        // --- SOURCE: BT ---
        findViewById(R.id.btnBT).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getBt());
            vibrate(v);
        });

        // --- SOURCE: AUX/CD ---
        findViewById(R.id.btnAux).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getAux());
            vibrate(v);
        });

        // --- SOURCE: FM ---
        findViewById(R.id.btnFM).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getFm());
            vibrate(v);
        });

        // --- SOURCE: USB ---
        findViewById(R.id.btnUSB).setOnClickListener(v -> {
            irHelper.send(prefs.getAddr(), prefs.getUsb());
            vibrate(v);
        });

        // --- SETUP BUTTON ---
        findViewById(R.id.btnSetup).setOnClickListener(v -> {
            startActivity(new Intent(this, SetupActivity.class));
        });
    }

    private void vibrate(View v) {
        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
