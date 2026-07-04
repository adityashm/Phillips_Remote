package com.aditya.philipsremote;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores all IR codes discovered during setup in SharedPreferences.
 *
 * Default protocol = NEC @ 38 kHz — most likely for India-market MMS8085B.
 * Default address  = 0x00 (most common OEM chip starting point).
 * Fall back to RC-6 or RC-5 via the Setup scanner if NEC doesn't work.
 */
public class Prefs {
    private static final String PREF_FILE = "philips_remote_prefs";

    // Keys
    private static final String KEY_ADDR        = "addr";
    private static final String KEY_POWER       = "cmd_power";
    private static final String KEY_MUTE        = "cmd_mute";
    private static final String KEY_VOL_UP      = "cmd_vol_up";
    private static final String KEY_VOL_DOWN    = "cmd_vol_down";
    private static final String KEY_BASS_UP     = "cmd_bass_up";
    private static final String KEY_BASS_DOWN   = "cmd_bass_down";
    private static final String KEY_BT          = "cmd_bt";
    private static final String KEY_AUX         = "cmd_aux";
    private static final String KEY_FM          = "cmd_fm";
    private static final String KEY_USB         = "cmd_usb";
    // Phase 1: new playback keys
    private static final String KEY_PLAY_PAUSE  = "cmd_play_pause";
    private static final String KEY_NEXT        = "cmd_next";
    private static final String KEY_PREV        = "cmd_prev";
    // Numeric 0–9
    private static final String KEY_NUM_0       = "cmd_num_0";
    private static final String KEY_NUM_1       = "cmd_num_1";
    private static final String KEY_NUM_2       = "cmd_num_2";
    private static final String KEY_NUM_3       = "cmd_num_3";
    private static final String KEY_NUM_4       = "cmd_num_4";
    private static final String KEY_NUM_5       = "cmd_num_5";
    private static final String KEY_NUM_6       = "cmd_num_6";
    private static final String KEY_NUM_7       = "cmd_num_7";
    private static final String KEY_NUM_8       = "cmd_num_8";
    private static final String KEY_NUM_9       = "cmd_num_9";

    private static final String KEY_SETUP_DONE  = "setup_done";
    private static final String KEY_PROTOCOL    = "protocol";

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    // ── Protocol ──────────────────────────────────────────────────────────
    /** NEC @ 38 kHz is the most likely protocol for India-market MMS8085B.
     *  Use Setup scanner to confirm; fall back to RC-6 or RC-5 if needed. */
    public String getProtocol() { return prefs.getString(KEY_PROTOCOL, IrProtocol.NEC); }
    public void   setProtocol(String v) { prefs.edit().putString(KEY_PROTOCOL, v).apply(); }

    // ── Address ───────────────────────────────────────────────────────────
    public int  getAddr() { return prefs.getInt(KEY_ADDR, defaultAddr()); }
    public void setAddr(int v) { prefs.edit().putInt(KEY_ADDR, v).apply(); }

    // ── Audio controls ────────────────────────────────────────────────────
    public int  getPower()     { return prefs.getInt(KEY_POWER,     def(NECEncoder.CMD_POWER,     RC6Encoder.CMD_POWER,     RC5Encoder.CMD_POWER)); }
    public int  getMute()      { return prefs.getInt(KEY_MUTE,      def(NECEncoder.CMD_MUTE,      RC6Encoder.CMD_MUTE,      RC5Encoder.CMD_MUTE)); }
    public int  getVolUp()     { return prefs.getInt(KEY_VOL_UP,    def(NECEncoder.CMD_VOL_UP,    RC6Encoder.CMD_VOL_UP,    RC5Encoder.CMD_VOL_UP)); }
    public int  getVolDown()   { return prefs.getInt(KEY_VOL_DOWN,  def(NECEncoder.CMD_VOL_DOWN,  RC6Encoder.CMD_VOL_DOWN,  RC5Encoder.CMD_VOL_DOWN)); }
    public int  getBassUp()    { return prefs.getInt(KEY_BASS_UP,   def(NECEncoder.CMD_BASS_UP,   RC6Encoder.CMD_BASS_UP,   RC5Encoder.CMD_BASS_UP)); }
    public int  getBassDown()  { return prefs.getInt(KEY_BASS_DOWN, def(NECEncoder.CMD_BASS_DOWN, RC6Encoder.CMD_BASS_DOWN, RC5Encoder.CMD_BASS_DOWN)); }

    public void setPower(int v)     { prefs.edit().putInt(KEY_POWER,     v).apply(); }
    public void setMute(int v)      { prefs.edit().putInt(KEY_MUTE,      v).apply(); }
    public void setVolUp(int v)     { prefs.edit().putInt(KEY_VOL_UP,    v).apply(); }
    public void setVolDown(int v)   { prefs.edit().putInt(KEY_VOL_DOWN,  v).apply(); }
    public void setBassUp(int v)    { prefs.edit().putInt(KEY_BASS_UP,   v).apply(); }
    public void setBassDown(int v)  { prefs.edit().putInt(KEY_BASS_DOWN, v).apply(); }

    // ── Source inputs ─────────────────────────────────────────────────────
    public int  getBt()   { return prefs.getInt(KEY_BT,  def(NECEncoder.CMD_BT,  RC6Encoder.CMD_BT,  RC5Encoder.CMD_BT)); }
    public int  getAux()  { return prefs.getInt(KEY_AUX, def(NECEncoder.CMD_AUX, RC6Encoder.CMD_AUX, RC5Encoder.CMD_AUX)); }
    public int  getFm()   { return prefs.getInt(KEY_FM,  def(NECEncoder.CMD_FM,  RC6Encoder.CMD_FM,  RC5Encoder.CMD_FM)); }
    public int  getUsb()  { return prefs.getInt(KEY_USB, def(NECEncoder.CMD_USB, RC6Encoder.CMD_USB, RC5Encoder.CMD_USB)); }

    public void setBt(int v)   { prefs.edit().putInt(KEY_BT,  v).apply(); }
    public void setAux(int v)  { prefs.edit().putInt(KEY_AUX, v).apply(); }
    public void setFm(int v)   { prefs.edit().putInt(KEY_FM,  v).apply(); }
    public void setUsb(int v)  { prefs.edit().putInt(KEY_USB, v).apply(); }

    // ── Playback controls (Phase 1) ───────────────────────────────────────
    public int  getPlayPause() { return prefs.getInt(KEY_PLAY_PAUSE, def(NECEncoder.CMD_PLAY_PAUSE, 0x44, 0x44)); }
    public int  getNext()      { return prefs.getInt(KEY_NEXT,       def(NECEncoder.CMD_NEXT,       0x40, 0x40)); }
    public int  getPrev()      { return prefs.getInt(KEY_PREV,       def(NECEncoder.CMD_PREV,       0x41, 0x41)); }

    public void setPlayPause(int v) { prefs.edit().putInt(KEY_PLAY_PAUSE, v).apply(); }
    public void setNext(int v)      { prefs.edit().putInt(KEY_NEXT,       v).apply(); }
    public void setPrev(int v)      { prefs.edit().putInt(KEY_PREV,       v).apply(); }

    // ── Numeric keys 0–9 (Phase 1) ───────────────────────────────────────
    public int getNum(int digit) {
        String key = numKey(digit);
        return prefs.getInt(key, defaultNum(digit));
    }
    public void setNum(int digit, int cmd) {
        prefs.edit().putInt(numKey(digit), cmd).apply();
    }

    // ── Setup state ───────────────────────────────────────────────────────
    public boolean isSetupDone()          { return prefs.getBoolean(KEY_SETUP_DONE, false); }
    public void    setSetupDone(boolean v){ prefs.edit().putBoolean(KEY_SETUP_DONE, v).apply(); }

    public void resetToDefaults() { prefs.edit().clear().apply(); }

    // ── Helpers ───────────────────────────────────────────────────────────
    private int defaultAddr() {
        String p = getProtocol();
        if (IrProtocol.NEC.equals(p)) return NECEncoder.ADDR_DEFAULT;
        if (IrProtocol.RC6.equals(p)) return RC6Encoder.ADDR_AUDIO;
        return RC5Encoder.ADDR_AUDIO;
    }

    /** Returns the protocol-appropriate default command. */
    private int def(int nec, int rc6, int rc5) {
        String p = getProtocol();
        if (IrProtocol.NEC.equals(p)) return nec;
        if (IrProtocol.RC6.equals(p)) return rc6;
        return rc5;
    }

    private static String numKey(int digit) {
        switch (digit) {
            case 0: return KEY_NUM_0; case 1: return KEY_NUM_1;
            case 2: return KEY_NUM_2; case 3: return KEY_NUM_3;
            case 4: return KEY_NUM_4; case 5: return KEY_NUM_5;
            case 6: return KEY_NUM_6; case 7: return KEY_NUM_7;
            case 8: return KEY_NUM_8; default: return KEY_NUM_9;
        }
    }

    private int defaultNum(int digit) {
        switch (digit) {
            case 0: return NECEncoder.CMD_NUM_0; case 1: return NECEncoder.CMD_NUM_1;
            case 2: return NECEncoder.CMD_NUM_2; case 3: return NECEncoder.CMD_NUM_3;
            case 4: return NECEncoder.CMD_NUM_4; case 5: return NECEncoder.CMD_NUM_5;
            case 6: return NECEncoder.CMD_NUM_6; case 7: return NECEncoder.CMD_NUM_7;
            case 8: return NECEncoder.CMD_NUM_8; default: return NECEncoder.CMD_NUM_9;
        }
    }
}
