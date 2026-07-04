package com.aditya.philipsremote;

public class IrProtocol {
    public static final String RC5 = "RC5";
    public static final String NEC = "NEC";

    public static int maxAddress(String protocol) {
        return NEC.equals(protocol) ? 255 : 31;
    }

    public static int maxCommand(String protocol) {
        return NEC.equals(protocol) ? 255 : 127;
    }
}
