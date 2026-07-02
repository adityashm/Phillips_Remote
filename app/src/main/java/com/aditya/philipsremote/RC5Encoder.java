package com.aditya.philipsremote;

import java.util.ArrayList;
import java.util.List;

/**
 * Philips RC-5 IR Protocol Encoder
 *
 * RC-5 Frame: S1 S2 T A4 A3 A2 A1 A0 C5 C4 C3 C2 C1 C0  (14 bits)
 * RC-5x uses S2 as an inverted command extension bit, allowing commands 0-127.
 * Carrier:    36 kHz
 * Half-bit:   889 µs
 * Bit-1 (Manchester): space(889) → mark(889)
 * Bit-0 (Manchester): mark(889) → space(889)
 *
 * Known Philips RC-5 addresses:
 *   0x00 = TV
 *   0x05 = VCR
 *   0x10 = Pre-amp / Audio (best first guess for MMS8085B)
 *   0x11 = Tuner
 *   0x12 = Recorder
 *   0x14 = CD Player
 *   0x1A = CD-R
 *
 * If address 16 (0x10) doesn't work, run SetupActivity to scan addresses 0–31.
 */
public class RC5Encoder {

    public static final int CARRIER_FREQ = 36000; // 36 kHz
    public static final int HALF_BIT     = 889;   // microseconds
    public static final int REPEAT_GAP   = 89000; // approx. 114ms period minus one 25ms frame

    // ------- Philips RC-5 system addresses -------
    public static final int ADDR_AUDIO     = 16; // Pre-amp / audio; try first for MMS8085B
    public static final int ADDR_TUNER     = 17;
    public static final int ADDR_RECORDER  = 18;
    public static final int ADDR_AUDIO_2   = 19; // Pre-amp 2
    public static final int ADDR_CD        = 20;
    public static final int ADDR_AUDIO_SYS = 21; // Audio stack / combi
    public static final int ADDR_CDR       = 26;

    // Standard Philips RC-5 Audio commands
    public static final int CMD_POWER    = 12;  // 0x0C
    public static final int CMD_MUTE     = 13;  // 0x0D
    public static final int CMD_VOL_UP   = 16;  // 0x10
    public static final int CMD_VOL_DOWN = 17;  // 0x11
    public static final int CMD_BASS_UP  = 22;  // 0x16
    public static final int CMD_BASS_DOWN= 23;  // 0x17
    public static final int CMD_TREBLE_UP  = 24;  // 0x18
    public static final int CMD_TREBLE_DOWN= 25;  // 0x19
    // Source/input commands — these vary by model, scan to find yours
    public static final int CMD_BT       = 6;   // 0x06 (try also 4, 5, 7)
    public static final int CMD_AUX      = 8;   // 0x08 (AUD/CD)
    public static final int CMD_FM       = 3;   // 0x03 (try also 65, 66)
    public static final int CMD_USB      = 2;   // 0x02 (try also 9, 10)

    /**
     * Encode an RC-5 command into a ConsumerIrManager pattern (microseconds).
     *
     * @param address  5-bit device address (0–31)
     * @param command  RC-5/RC-5x command (0–127)
     * @param toggle   toggle bit (alternate between true/false for successive presses)
     * @return int[] pattern for ConsumerIrManager.transmit()
     */
    public static int[] encode(int address, int command, boolean toggle) {

        if (address < 0 || address > 31) {
            throw new IllegalArgumentException("RC-5 address must be 0..31");
        }
        if (command < 0 || command > 127) {
            throw new IllegalArgumentException("RC-5 command must be 0..127");
        }

        // Build 14-bit RC-5/RC-5x frame
        int[] bits = new int[14];
        bits[0] = 1;                    // S1 = start bit (always 1)
        bits[1] = command < 64 ? 1 : 0; // S2 = start bit, or inverted command bit 6 in RC-5x
        bits[2] = toggle ? 1 : 0;       // T  = toggle bit

        // 5-bit address (MSB first)
        for (int i = 0; i < 5; i++) {
            bits[3 + i] = (address >> (4 - i)) & 1;
        }
        // 6-bit command payload (MSB first)
        int lowCommand = command & 0x3F;
        for (int i = 0; i < 6; i++) {
            bits[8 + i] = (lowCommand >> (5 - i)) & 1;
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

    public static int[] encodeRepeated(int address, int command, boolean toggle, int repeatCount) {
        if (repeatCount <= 1) {
            return encode(address, command, toggle);
        }

        int[] frame = encode(address, command, toggle);
        List<Integer> repeated = new ArrayList<>();
        for (int i = 0; i < repeatCount; i++) {
            for (int duration : frame) {
                repeated.add(duration);
            }
            if (i < repeatCount - 1) {
                if (repeated.size() % 2 == 1) {
                    repeated.add(REPEAT_GAP);
                } else {
                    repeated.set(repeated.size() - 1, repeated.get(repeated.size() - 1) + REPEAT_GAP);
                }
            }
        }
        return repeated.stream().mapToInt(x -> x).toArray();
    }

    /**
     * Convenience: encode with toggle = false (single press).
     */
    public static int[] encode(int address, int command) {
        return encode(address, command, false);
    }
}
