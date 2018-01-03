package com.qoeapps.qoenforce.utils;

import android.content.Context;
import android.hardware.Camera;
import android.media.AudioManager;
import android.net.TrafficStats;
import android.os.Build;
import android.os.PowerManager;
import android.telephony.TelephonyManager;

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

    public static boolean audioMode(AudioManager audioManager){


        int audioMode = audioManager.getMode();
        //Log.d("Audio Mode", "Audio modes "+audioManager.MODE_CURRENT +audioManager.MODE_IN_CALL+ audioManager.MODE_IN_COMMUNICATION+ audioManager.MODE_NORMAL+audioManager.MODE_RINGTONE);
        if (audioMode == AudioManager.MODE_IN_CALL|| audioMode == AudioManager.MODE_IN_COMMUNICATION || audioMode == audioManager.MODE_RINGTONE){

            return true;
        }
        else if (audioManager.isMusicActive())
            return true;
        else
            return false;
    }

    public static boolean isMusicOn(AudioManager audioManager){
        return audioManager.isMusicActive();
    }

    public static boolean isUserInCall(TelephonyManager tm) {

        return tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
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
