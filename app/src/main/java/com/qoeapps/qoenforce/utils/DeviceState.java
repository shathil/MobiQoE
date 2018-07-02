package com.qoeapps.qoenforce.utils;

import android.content.Context;
import android.hardware.Camera;
import android.media.AudioManager;
import android.net.TrafficStats;
import android.os.Build;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Created by mohoque on 18/02/2017.
 */

public class DeviceState {

    private AudioManager locoaudioManager;
    public DeviceState(AudioManager audioManager){
        locoaudioManager = audioManager;
    }
    public static boolean isScreenOn(PowerManager pm){
        if(Build.VERSION.SDK_INT<21)
            return pm.isScreenOn();
        else
            return pm.isInteractive();
    }

    public static boolean audioStart(AudioManager audioManager){


        long startTime = System.currentTimeMillis();
        int audioMode = audioManager.getMode();




        if (audioMode == AudioManager.MODE_IN_CALL|| audioMode == AudioManager.MODE_IN_COMMUNICATION || audioMode == audioManager.MODE_RINGTONE){

            //Log.d("Audio Mode", "Audio modes "+audioMode);
            return true;

        }
        else
            return false;
    }


    public static long audioProperties(AudioManager audioManager){


        long startTime = System.currentTimeMillis();
        int audioMode = audioManager.getMode();


        if (audioMode == AudioManager.MODE_IN_CALL|| audioMode == AudioManager.MODE_IN_COMMUNICATION || audioMode == audioManager.MODE_RINGTONE){

            //Log.d("Audio Mode", "Audio modes "+audioMode);
            return startTime;

        }
        else
            return -1;
    }

    public static long isMusicOn(AudioManager audioManager){
        //return framesPerBuffer == null ? DEFAULT_FRAME_PER_BUFFER : Integer.parseInt(framesPerBuffer);
        if (audioManager.isMusicActive())
            return System.currentTimeMillis();
        else
            return -1;
    }

    public static long getStremingStuff(AudioManager audioManager){

        //return framesPerBuffer == null ? DEFAULT_FRAME_PER_BUFFER : Integer.parseInt(framesPerBuffer);
        if (audioManager.isMusicActive())
            return System.currentTimeMillis();
        else
            return -1;
    }


    public static long isUserInCall(TelephonyManager tm) {

        if(tm.getCallState() != TelephonyManager.CALL_STATE_IDLE)
            return System.currentTimeMillis();
        else
            return -1;
    }

    public static boolean isUserOffHook(TelephonyManager tm) {

        return tm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK;
    }

    public static boolean isUserin(TelephonyManager tm) {

        return tm.getCallState() != TelephonyManager.CALL_STATE_RINGING;
    }

    public static boolean wasInForegound(){

        return false;
    }

    public static boolean camOneinUse(){
        Camera camera = null;
        try {
            camera = Camera.open(0);
        } catch (RuntimeException e) {
            return true;
        } finally {
            if (camera != null) camera.release();
        }
        return false;
    }

    public static boolean camTwoinUse(){
        Camera camera = null;
        try {
            camera = Camera.open(1);
        } catch (RuntimeException e) {
            return true;
        } finally {
            if (camera != null) camera.release();
        }
        return false;
    }

    public static long getRxBytes(int uid){

        return TrafficStats.getUidRxBytes(uid);
    }

    public static long getTxBytes(int uid){

        return TrafficStats.getUidTxBytes(uid);
    }

}
