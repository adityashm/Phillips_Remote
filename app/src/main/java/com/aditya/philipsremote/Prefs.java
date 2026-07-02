package com.aditya.philipsremote;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores IR address and command codes discovered during setup.
 * Default values are Philips RC-5 audio amplifier (address 16).
 */
public class Prefs {
    private static final String PREF_FILE = "philips_remote_prefs";

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
    private static final String KEY_SETUP_DONE  = "setup_done";

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    // ---- Getters (defaults = Philips RC-5 amplifier address 16) ----
    public int getAddr()      { return prefs.getInt(KEY_ADDR,      RC5Encoder.ADDR_AUDIO); }
    public int getPower()     { return prefs.getInt(KEY_POWER,     RC5Encoder.CMD_POWER); }
    public int getMute()      { return prefs.getInt(KEY_MUTE,      RC5Encoder.CMD_MUTE); }
    public int getVolUp()     { return prefs.getInt(KEY_VOL_UP,    RC5Encoder.CMD_VOL_UP); }
    public int getVolDown()   { return prefs.getInt(KEY_VOL_DOWN,  RC5Encoder.CMD_VOL_DOWN); }
    public int getBassUp()    { return prefs.getInt(KEY_BASS_UP,   RC5Encoder.CMD_BASS_UP); }
    public int getBassDown()  { return prefs.getInt(KEY_BASS_DOWN, RC5Encoder.CMD_BASS_DOWN); }
    public int getBt()        { return prefs.getInt(KEY_BT,        RC5Encoder.CMD_BT); }
    public int getAux()       { return prefs.getInt(KEY_AUX,       RC5Encoder.CMD_AUX); }
    public int getFm()        { return prefs.getInt(KEY_FM,        RC5Encoder.CMD_FM); }
    public int getUsb()       { return prefs.getInt(KEY_USB,       RC5Encoder.CMD_USB); }
    public boolean isSetupDone() { return prefs.getBoolean(KEY_SETUP_DONE, false); }

    // ---- Setters ----
    public void setAddr(int v)      { prefs.edit().putInt(KEY_ADDR, v).apply(); }
    public void setPower(int v)     { prefs.edit().putInt(KEY_POWER, v).apply(); }
    public void setMute(int v)      { prefs.edit().putInt(KEY_MUTE, v).apply(); }
    public void setVolUp(int v)     { prefs.edit().putInt(KEY_VOL_UP, v).apply(); }
    public void setVolDown(int v)   { prefs.edit().putInt(KEY_VOL_DOWN, v).apply(); }
    public void setBassUp(int v)    { prefs.edit().putInt(KEY_BASS_UP, v).apply(); }
    public void setBassDown(int v)  { prefs.edit().putInt(KEY_BASS_DOWN, v).apply(); }
    public void setBt(int v)        { prefs.edit().putInt(KEY_BT, v).apply(); }
    public void setAux(int v)       { prefs.edit().putInt(KEY_AUX, v).apply(); }
    public void setFm(int v)        { prefs.edit().putInt(KEY_FM, v).apply(); }
    public void setUsb(int v)       { prefs.edit().putInt(KEY_USB, v).apply(); }
    public void setSetupDone(boolean v) { prefs.edit().putBoolean(KEY_SETUP_DONE, v).apply(); }

    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }
}
