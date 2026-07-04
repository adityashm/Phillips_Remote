package com.aditya.philipsremote;

import java.util.ArrayList;
import java.util.List;

/**
 * NEC IR protocol encoder.
 *
 * Many multimedia speaker remotes use NEC-style frames:
 * 9ms header mark, 4.5ms header space, 8-bit address, inverse address,
 * 8-bit command, inverse command, then a final mark.
 */
public class NECEncoder {
    public static final int CARRIER_FREQ = 38000;

    /** Common starting guesses for China-market multimedia remotes (verify in Setup). */
    public static final int ADDR_DEFAULT   = 0x00;
    public static final int CMD_POWER      = 0x45;
    public static final int CMD_MUTE       = 0x44;
    public static final int CMD_VOL_UP     = 0x46;
    public static final int CMD_VOL_DOWN   = 0x47;
    public static final int CMD_BASS_UP    = 0x48;
    public static final int CMD_BASS_DOWN  = 0x49;
    public static final int CMD_BT         = 0x4A;
    public static final int CMD_AUX        = 0x4B;
    public static final int CMD_FM         = 0x4C;
    public static final int CMD_USB        = 0x4D;

    /** Addresses to try first when auto-scanning.
     *  Covers standard Chinese OEM multimedia speaker chipsets, Philips India range,
     *  and common budget remote addresses. Full 0–255 sweep takes ~6 min, so we target
     *  the most likely candidates first. */
    public static final int[] COMMON_ADDRESSES = {
            // Generic Chinese OEM — most common for India-market MMS speakers
            0x00, 0x01, 0x02, 0x04, 0x05, 0x06, 0x07, 0x08,
            // Philips/OEM multimedia-specific addresses reported on forums
            0x10, 0x12, 0x14, 0x20,
            // High-byte addresses common for budget remote chipsets
            0x40, 0x56, 0x59, 0x5E,
            // Inverted-address style (addr + ~addr in 16-bit NEC extended)
            0x80, 0xF7, 0xFA, 0xFC, 0xFD, 0xFE, 0xFF,
            // A few more spotted in OEM multimedia remote dumps
            0x09, 0x0A
    };

    private static final int HEADER_MARK = 9000;
    private static final int HEADER_SPACE = 4500;
    private static final int BIT_MARK = 560;
    private static final int ZERO_SPACE = 560;
    private static final int ONE_SPACE = 1690;
    private static final int FRAME_GAP = 40000;

    public static int[] encode(int address, int command) {
        if (address < 0 || address > 255) {
            throw new IllegalArgumentException("NEC address must be 0..255");
        }
        if (command < 0 || command > 255) {
            throw new IllegalArgumentException("NEC command must be 0..255");
        }

        List<Integer> durations = new ArrayList<>();
        durations.add(HEADER_MARK);
        durations.add(HEADER_SPACE);
        appendByte(durations, address);
        appendByte(durations, address ^ 0xFF);
        appendByte(durations, command);
        appendByte(durations, command ^ 0xFF);
        durations.add(BIT_MARK);
        return durations.stream().mapToInt(x -> x).toArray();
    }

    public static int[] encodeRepeated(int address, int command, int repeatCount) {
        if (repeatCount <= 1) {
            return encode(address, command);
        }

        int[] frame = encode(address, command);
        List<Integer> repeated = new ArrayList<>();
        for (int i = 0; i < repeatCount; i++) {
            for (int duration : frame) {
                repeated.add(duration);
            }
            if (i < repeatCount - 1) {
                repeated.add(FRAME_GAP);
            }
        }
        return repeated.stream().mapToInt(x -> x).toArray();
    }

    private static void appendByte(List<Integer> durations, int value) {
        for (int i = 0; i < 8; i++) {
            durations.add(BIT_MARK);
            durations.add(((value >> i) & 1) == 1 ? ONE_SPACE : ZERO_SPACE);
        }
    }
}
