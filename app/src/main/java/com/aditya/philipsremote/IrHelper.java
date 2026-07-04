package com.aditya.philipsremote;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.widget.Toast;

public class IrHelper {

    private static final int DEFAULT_REPEAT_COUNT = 5;

    private final ConsumerIrManager irManager;
    private final Context context;
    private final Prefs prefs;
    private boolean rc5Toggle = false;

    public IrHelper(Context context) {
        this.context = context;
        this.prefs = new Prefs(context);
        irManager = (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE);
    }

    public boolean hasIrBlaster() {
        return irManager != null && irManager.hasIrEmitter();
    }

    public void send(int address, int command) {
        if (!hasIrBlaster()) {
            Toast.makeText(context, "No IR blaster found!", Toast.LENGTH_SHORT).show();
            return;
        }

        String protocol = prefs.getProtocol();
        int carrier;
        int[] pattern;

        if (IrProtocol.NEC.equals(protocol)) {
            carrier = NECEncoder.CARRIER_FREQ;
            pattern = NECEncoder.encodeRepeated(address, command, DEFAULT_REPEAT_COUNT);
        } else if (IrProtocol.RC6.equals(protocol)) {
            carrier = RC6Encoder.CARRIER_FREQ;
            pattern = RC6Encoder.encodeRepeated(address, command, rc5Toggle, DEFAULT_REPEAT_COUNT);
            rc5Toggle = !rc5Toggle;
        } else {
            carrier = RC5Encoder.CARRIER_FREQ;
            pattern = RC5Encoder.encodeRepeated(address, command, rc5Toggle, DEFAULT_REPEAT_COUNT);
            rc5Toggle = !rc5Toggle;
        }

        irManager.transmit(carrier, pattern);
    }

    /** Send with explicit toggle (setup scanner). NEC ignores toggleBit. */
    public void send(int address, int command, boolean toggleBit) {
        if (!hasIrBlaster()) return;

        String protocol = prefs.getProtocol();
        int carrier;
        int[] pattern;

        if (IrProtocol.NEC.equals(protocol)) {
            carrier = NECEncoder.CARRIER_FREQ;
            pattern = NECEncoder.encodeRepeated(address, command, DEFAULT_REPEAT_COUNT);
        } else if (IrProtocol.RC6.equals(protocol)) {
            carrier = RC6Encoder.CARRIER_FREQ;
            pattern = RC6Encoder.encodeRepeated(address, command, toggleBit, DEFAULT_REPEAT_COUNT);
        } else {
            carrier = RC5Encoder.CARRIER_FREQ;
            pattern = RC5Encoder.encodeRepeated(address, command, toggleBit, DEFAULT_REPEAT_COUNT);
        }

        irManager.transmit(carrier, pattern);
    }

    public void resetToggle() {
        rc5Toggle = false;
    }

    public String getProtocol() {
        return prefs.getProtocol();
    }
}
