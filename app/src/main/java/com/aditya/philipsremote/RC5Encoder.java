package com.aditya.philipsremote;

import java.util.ArrayList;
import java.util.List;

/**
 * Philips RC-5 IR Protocol Encoder
 *
 * RC-5 Frame: S1 S2 T A4 A3 A2 A1 A0 C5 C4 C3 C2 C1 C0  (14 bits)
 * Carrier:    36 kHz
 * Half-bit:   889 µs
 * Bit-1 (Manchester): space(889) → mark(889)
 * Bit-0 (Manchester): mark(889) → space(889)
 *
 * Known Philips RC-5 addresses:
 *   0x00 = TV
 *   0x05 = VCR
 *   0x10 = Amplifier / Audio (most likely for MMS8085B)
 *   0x12 = CD Player
 *   0x1A = Tape
 *
 * If address 16 (0x10) doesn't work, run SetupActivity to scan addresses 0–31.
 */
public class RC5Encoder {

    public static final int CARRIER_FREQ = 36000; // 36 kHz
    public static final int HALF_BIT     = 889;   // microseconds

    // ------- Known Philips RC-5 commands (address 16 = Audio Amplifier) -------
    public static final int ADDR_AUDIO   = 16; // Try this first for MMS8085B
    public static final int ADDR_CD      = 18; // Fallback
    public static final int ADDR_TAPE    = 26;

    // Standard Philips RC-5 Audio commands
    public static final int CMD_POWER    = 12;  // 0x0C
    public static final int CMD_MUTE     = 13;  // 0x0D
    public static final int CMD_VOL_UP   = 16;  // 0x10
    public static final int CMD_VOL_DOWN = 17;  // 0x11
    public static final int CMD_BASS_UP  = 24;  // 0x18
    public static final int CMD_BASS_DOWN= 25;  // 0x19
    // Source/input commands — these vary by model, scan to find yours
    public static final int CMD_BT       = 6;   // 0x06 (try also 4, 5, 7)
    public static final int CMD_AUX      = 8;   // 0x08 (AUD/CD)
    public static final int CMD_FM       = 3;   // 0x03 (try also 65, 66)
    public static final int CMD_USB      = 2;   // 0x02 (try also 9, 10)

    /**
     * Encode an RC-5 command into a ConsumerIrManager pattern (microseconds).
     *
     * @param address  5-bit device address (0–31)
     * @param command  6-bit command (0–63)
     * @param toggle   toggle bit (alternate between true/false for successive presses)
     * @return int[] pattern for ConsumerIrManager.transmit()
     */
    public static int[] encode(int address, int command, boolean toggle) {

        // Build 14-bit RC-5 frame
        int[] bits = new int[14];
        bits[0] = 1;                    // S1 = start bit (always 1)
        bits[1] = 1;                    // S2 = start bit (always 1)
        bits[2] = toggle ? 1 : 0;       // T  = toggle bit

        // 5-bit address (MSB first)
        for (int i = 0; i < 5; i++) {
            bits[3 + i] = (address >> (4 - i)) & 1;
        }
        // 6-bit command (MSB first)
        for (int i = 0; i < 6; i++) {
            bits[8 + i] = (command >> (5 - i)) & 1;
        }

        // Manchester encode → half-period list  (0 = space/off, 1 = mark/on)
        // Bit-1: [space, mark]  |  Bit-0: [mark, space]
        List<Integer> halfPeriods = new ArrayList<>();
        for (int b : bits) {
            if (b == 1) {
                halfPeriods.add(0); // first half: space
                halfPeriods.add(1); // second half: mark
            } else {
                halfPeriods.add(1); // first half: mark
                halfPeriods.add(0); // second half: space
            }
        }

        // ConsumerIrManager pattern must START with a mark (IR on).
        // Drop any leading spaces.
        int start = 0;
        while (start < halfPeriods.size() && halfPeriods.get(start) == 0) start++;

        // RLE-compress into durations (mark-space-mark-space…)
        List<Integer> durations = new ArrayList<>();
        int i = start;
        while (i < halfPeriods.size()) {
            int level = halfPeriods.get(i);
            int count = 0;
            while (i < halfPeriods.size() && halfPeriods.get(i) == level) {
                count++;
                i++;
            }
            durations.add(count * HALF_BIT);
        }

        // Remove trailing space (last element) — some devices need it dropped
        // ConsumerIrManager handles trailing automatically, so leave it.

        return durations.stream().mapToInt(x -> x).toArray();
    }

    /**
     * Convenience: encode with toggle = false (single press).
     */
    public static int[] encode(int address, int command) {
        return encode(address, command, false);
    }
}
