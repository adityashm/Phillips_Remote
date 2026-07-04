package com.aditya.philipsremote;

import java.util.ArrayList;
import java.util.List;

/**
 * Philips RC-6 Mode 0 IR Protocol Encoder
 *
 * RC-6 Mode 0 Frame (20 bits):
 *   S1 S2 F2 F1 F0 T A7 A6 A5 A4 A3 A2 A1 A0 C7 C6 C5 C4 C3 C2 C1 C0
 *   (Start x2, Field bits x3, Toggle bit, Address x8, Command x8)
 *
 * Carrier:    36 kHz
 * Tick:       444 µs (889 µs per bit, same as RC-5)
 * Header:     mark(2664 µs) → space(888 µs) → mark(444 µs) → space(444 µs)
 *             (The last two are S1 start bit = 1)
 * Manchester: Bit-1: [mark, space], Bit-0: [space, mark]
 *
 * Known Philips RC-6 addresses:
 *   0x10 = Audio/Pre-amp (MMS8085B address)
 *
 * Commands for MMS8085B (from similar Philips soundbar TAB5105/79):
 *   Vol+: 0x10, Vol-: 0x11, Mute: 0x0D, Power: 0xC7, BT: 0x69, AUX: 0x6B
 */
public class RC6Encoder {

    public static final int CARRIER_FREQ = 36000;
    public static final int TICK         = 444;
    public static final int HEADER_MARK  = 2664;
    public static final int HEADER_SPACE = 888;
    public static final int REPEAT_GAP   = 88916;

    public static final int ADDR_AUDIO = 0x10;

    public static final int CMD_POWER    = 0xC7;
    public static final int CMD_MUTE     = 0x0D;
    public static final int CMD_VOL_UP   = 0x10;
    public static final int CMD_VOL_DOWN = 0x11;
    public static final int CMD_BASS_UP  = 0x16;
    public static final int CMD_BASS_DOWN= 0x17;
    public static final int CMD_TREBLE_UP  = 0x18;
    public static final int CMD_TREBLE_DOWN= 0x19;
    public static final int CMD_BT       = 0x69;
    public static final int CMD_AUX      = 0x6B;
    public static final int CMD_FM       = 0x6A;
    public static final int CMD_USB      = 0x6C;

    public static int[] encode(int address, int command, boolean toggle) {
        if (address < 0 || address > 255) {
            throw new IllegalArgumentException("RC-6 address must be 0..255");
        }
        if (command < 0 || command > 255) {
            throw new IllegalArgumentException("RC-6 command must be 0..255");
        }

        int[] bits = new int[19]; // S2-F0-T-Addr-Cmd (S1 is implicit in header)
        bits[0] = 1; // S2 = start bit 2
        bits[1] = 0; // F2 = field bit 2 (usually 0 for audio)
        bits[2] = 0; // F1 = field bit 1 (usually 0 for audio)
        bits[3] = 0; // F0 = field bit 0 (usually 0 for audio - Mode 0)
        bits[4] = toggle ? 1 : 0; // T = toggle bit (double width)

        for (int i = 0; i < 8; i++) {
            bits[5 + i] = (address >> (7 - i)) & 1;
        }
        for (int i = 0; i < 8; i++) {
            bits[13 + i] = (command >> (7 - i)) & 1;
        }

        List<Integer> halfPeriods = new ArrayList<>();
        for (int i = 0; i < bits.length; i++) {
            int b = bits[i];
            int repeat = (i == 4) ? 2 : 1;
            for (int r = 0; r < repeat; r++) {
                if (b == 1) {
                    halfPeriods.add(1);
                    halfPeriods.add(0);
                } else {
                    halfPeriods.add(0);
                    halfPeriods.add(1);
                }
            }
        }

        List<Integer> durations = new ArrayList<>();
        durations.add(HEADER_MARK);
        durations.add(HEADER_SPACE);
        durations.add(TICK);
        durations.add(TICK);

        int i = 0;
        while (i < halfPeriods.size()) {
            int level = halfPeriods.get(i);
            int count = 0;
            while (i < halfPeriods.size() && halfPeriods.get(i) == level) {
                count++;
                i++;
            }
            durations.add(count * TICK);
        }

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

    public static int[] encode(int address, int command) {
        return encode(address, command, false);
    }
}