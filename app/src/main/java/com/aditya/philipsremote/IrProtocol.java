package com.aditya.philipsremote;

public class IrProtocol {
    public static final String RC5 = "RC5";
    public static final String RC6 = "RC6";
    public static final String NEC = "NEC";

    public static int maxAddress(String protocol) {
        if (NEC.equals(protocol)) return 255;
        if (RC6.equals(protocol)) return 255;
        return 31;
    }

    public static int maxCommand(String protocol) {
        return 255;
    }
}
