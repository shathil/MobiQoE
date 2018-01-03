package com.qoeapps.qoenforce.sysmeta;

/**
 * Created by mohoque on 11/02/2017.
 */

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.telephony.TelephonyManager;

public class SystemInformaiton {


    TelephonyManager tmg = null;
    public SystemInformaiton (Context context){

        tmg = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE));//.getSubscriberId().trim();
    }
    public static String getBoardInfo()
    {
        try
        {
            String str = Build.BOARD.trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public static String getDeviceBrand()
    {
        try
        {
            String str = Build.MANUFACTURER.trim().toLowerCase();
            if (str == "google") {
                str = null;
            }
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public static String getDeviceFingerprint()
    {
        try
        {
            String str = Build.FINGERPRINT.trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public static String getDeviceManufacturer()
    {
        try
        {
            String str = Build.MANUFACTURER.trim().toLowerCase();
            if (str == "google") {
                str = null;
            }
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public static String getDeviceModel()
    {
        try
        {
            String str = Build.MODEL.trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    @TargetApi(18)
    public  String getGroupID(Context paramContext)
    {
        try
        {
            String str = tmg.getGroupIdLevel1().trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public  String getHardwareInfo()
    {
        try
        {
            String str = Build.HARDWARE.trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public  String getIMSI(Context paramContext)
    {
        try
        {
            String str = tmg.getSubscriberId().trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public  String getImei(Context paramContext)
    {
        try
        {
            String str = tmg.getDeviceId().trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    @TargetApi(19)
    public  String getMMSUserAgent(Context paramContext)
    {
        try
        {
            String str = tmg.getMmsUserAgent().trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public  String getOperatorName(Context paramContext)
    {
        try
        {
            String str = tmg.getNetworkOperatorName().trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public  String getOperatorNameNumeric(Context paramContext)
    {
        try
        {
            String str = tmg.getNetworkOperator().trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public  String getPhoneNumber(Context paramContext)
    {
        try
        {
            String str = tmg.getLine1Number().trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public  String getRadioFirmwareInfo()
    {
        try
        {
            String str = Build.getRadioVersion().trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public  String getSIMSerialNumber(Context paramContext)
    {
        try
        {
            String str = tmg.getSimSerialNumber().trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public static String getSdkVersion()
    {
        try
        {
            String str = Integer.toString(VERSION.SDK_INT);
            return str;
        }
        catch (Exception localException) {}
        return null;
    }

    public static String getSoftwareVersion()
    {
        try
        {
            String str = VERSION.RELEASE.trim();
            return str;
        }
        catch (Exception localException) {}
        return null;
    }
}
