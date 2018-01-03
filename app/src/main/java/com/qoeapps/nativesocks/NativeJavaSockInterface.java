package com.qoeapps.nativesocks;

/**
 * Created by mohoque on 22/07/2017.
 */

public class NativeJavaSockInterface {
    static {
        System.loadLibrary("native-lib");
    }



    public native int initializeTCPSocket(String jobj);
    public native int initializeUdpSocket(String jobj);

}
