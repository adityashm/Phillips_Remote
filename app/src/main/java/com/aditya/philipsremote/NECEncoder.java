package com.aditya.philipsremote;

import java.util.ArrayList;
import java.util.List;

/**
 * NEC IR protocol encoder — 38 kHz, standard 32-bit.
 *
 * Frame: 9ms mark | 4.5ms space | [Addr 8-bit] [~Addr] [Cmd 8-bit] [~Cmd] | 560µs end mark
 * Bit-0: 560µs mark + 560µs space
 * Bit-1: 560µs mark + 1690µs space
 *
 * DEFAULT GUESSES for MMS8085B (addr=0x00, common OEM chip).
 * Run SetupActivity → Deep Scan to find the real address.
 * Once address confirmed, use Command Sweep per button.
 */
public class NECEncoder {

    public static final int CARRIER_FREQ = 38000;

    // ── Default address (most common OEM starting point) ──────────────────
    public static final int ADDR_DEFAULT = 0x00;

    // ── Command guesses (address=0x00, standard generic OEM layout) ───────
    // These are starting points only — confirm via Command Sweep in Setup.
    public static final int CMD_POWER      = 0x45;
    public static final int CMD_MUTE       = 0x44;
    public static final int CMD_VOL_UP     = 0x46;
    public static final int CMD_VOL_DOWN   = 0x47;
    public static final int CMD_BASS_UP    = 0x48;
    public static final int CMD_BASS_DOWN  = 0x49;

    // Source inputs
    public static final int CMD_BT         = 0x4A;
    public static final int CMD_AUX        = 0x4B;
    public static final int CMD_FM         = 0x4C;
    public static final int CMD_USB        = 0x4D;

    // Playback controls (USB/BT mode) — scan to confirm
    public static final int CMD_PLAY_PAUSE = 0x43;
    public static final int CMD_NEXT       = 0x40;
    public static final int CMD_PREV       = 0x41;

    // Numeric keys (USB track select / FM freq input) — scan to confirm
    public static final int CMD_NUM_0      = 0x19;
    public static final int CMD_NUM_1      = 0x16;
    public static final int CMD_NUM_2      = 0x0C;
    public static final int CMD_NUM_3      = 0x18;
    public static final int CMD_NUM_4      = 0x5E;
    public static final int CMD_NUM_5      = 0x08;
    public static final int CMD_NUM_6      = 0x1C;
    public static final int CMD_NUM_7      = 0x5A;
    public static final int CMD_NUM_8      = 0x42;
    public static final int CMD_NUM_9      = 0x52;

    // ── Address sweep lists ───────────────────────────────────────────────
    /** Quick scan: 25 highest-probability addresses (runs in ~37 seconds). */
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

    /** Deep scan: all 256 NEC addresses (runs ~6.5 min @ 1.5 s/step).
     *  Guaranteed to find address if device uses standard NEC. */
    public static final int TOTAL_NEC_ADDRESSES = 256;

    // ── Timing constants (standard NEC) ──────────────────────────────────
    private static final int HEADER_MARK  = 9000;
    private static final int HEADER_SPACE = 4500;
    private static final int BIT_MARK     = 560;
    private static final int ZERO_SPACE   = 560;
    private static final int ONE_SPACE    = 1690;
    private static final int FRAME_GAP   = 40000;

    // ── Encode / transmit ─────────────────────────────────────────────────

    public static int[] encode(int address, int command) {
        if (address < 0 || address > 255)
            throw new IllegalArgumentException("NEC address must be 0..255");
        if (command < 0 || command > 255)
            throw new IllegalArgumentException("NEC command must be 0..255");

        List<Integer> d = new ArrayList<>();
        d.add(HEADER_MARK);
        d.add(HEADER_SPACE);
        appendByte(d, address);
        appendByte(d, address ^ 0xFF);   // inverted address
        appendByte(d, command);
        appendByte(d, command ^ 0xFF);   // inverted command
        d.add(BIT_MARK);                 // stop bit
        return d.stream().mapToInt(x -> x).toArray();
    }

    public static int[] encodeRepeated(int address, int command, int repeatCount) {
        if (repeatCount <= 1) return encode(address, command);
        int[] frame = encode(address, command);
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < repeatCount; i++) {
            for (int v : frame) r.add(v);
            if (i < repeatCount - 1) r.add(FRAME_GAP);
        }
        return r.stream().mapToInt(x -> x).toArray();
    }

    private static void appendByte(List<Integer> d, int value) {
        for (int i = 0; i < 8; i++) {
            d.add(BIT_MARK);
            d.add(((value >> i) & 1) == 1 ? ONE_SPACE : ZERO_SPACE);
        }
    }
}
