package com.aditya.philipsremote;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.widget.Toast;

public class IrHelper {

    private final ConsumerIrManager irManager;
    private final Context context;
    private boolean toggle = false;
    private static final int DEFAULT_REPEAT_COUNT = 2;

    public IrHelper(Context context) {
        this.context = context;
        irManager = (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE);
    }

    public boolean hasIrBlaster() {
        return irManager != null && irManager.hasIrEmitter();
    }

    /**
     * Send a single RC-5 command.
     * Alternates the toggle bit on each call (correct RC-5 behaviour).
     */
    public void send(int address, int command) {
        if (!hasIrBlaster()) {
            Toast.makeText(context, "No IR blaster found!", Toast.LENGTH_SHORT).show();
            return;
        }
        int[] pattern = RC5Encoder.encodeRepeated(address, command, toggle, DEFAULT_REPEAT_COUNT);
        toggle = !toggle; // flip toggle bit for next press
        irManager.transmit(RC5Encoder.CARRIER_FREQ, pattern);
    }

    /**
     * Send with explicit toggle (for setup scanner).
     */
    public void send(int address, int command, boolean toggleBit) {
        if (!hasIrBlaster()) return;
        int[] pattern = RC5Encoder.encodeRepeated(address, command, toggleBit, DEFAULT_REPEAT_COUNT);
        irManager.transmit(RC5Encoder.CARRIER_FREQ, pattern);
    }

    public void resetToggle() {
        toggle = false;
    }
}
