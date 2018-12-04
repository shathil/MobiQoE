package com.qoeapps.utilityqos;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.qoeapps.qoenforce.datacontrol.AppFlowCache;
import com.qoeapps.qoenforce.datacontrol.FlowTable;
import com.qoeapps.qoenforce.datacontrol.LocalDNSCache;
import com.qoeapps.qoenforce.datacontrol.RessourceStatCache;
import com.qoeapps.qoenforce.datacontrol.TrafficStatCache;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.os.Environment.getExternalStorageDirectory;

/**
 * Created by mohoque on 08/11/2018.
 */

public class AppUsage {
    private Context locontext = null;

    public AppUsage(Context context){
        this.locontext = context;
    }



    public void MobileAppUsageHandler(){

        final Context handlercontext = locontext;


        /*
        *
        * This is very unreliable to read this from power_profile. Galaxy S3 capacity is 3200mAh. WTF
        * We need to have a service that has the capacity of different Smartphone models.
        *
        * */
        final Handler mainHandler = new Handler();
        mainHandler.post(new Runnable() {

            private int delayed = 2000;
            private HashMap<String, ApplicationUsage> firstAllPackagesInfo = new HashMap<>();
            private String usaePath = getExternalStorageDirectory()+"/UsageStats.txt";


            @Override
            public void run() {

                /* Get the average charging current*/

                mainHandler.postDelayed(this,delayed);
                Long curtime = System.currentTimeMillis();
                Long key = curtime/1000;
                sampleAppUsageBuild21(key);


            }









            private void sampleAppUsageBuild21(long start) {

                Calendar calendar = Calendar.getInstance();
                long endTime = System.currentTimeMillis();

                calendar.add(Calendar.SECOND,-30);
                long startTime = calendar.getTimeInMillis();

                final UsageStatsManager usageStatsManager = (UsageStatsManager) handlercontext.getSystemService(Context.USAGE_STATS_SERVICE);
                List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime);

                //String totalExBytes = TrafficStats.getTotalTxBytes()+":"+TrafficStats.getTotalRxBytes();
                //bytesTxRx.put(start,totalExBytes);

                for (UsageStats u : usageStatsList) {
                    String packname = u.getPackageName();

                    if ((firstAllPackagesInfo.get(packname) != null))// && (!localPackageId.isEmpty()))//
                    {
                        ApplicationUsage lastInfo = firstAllPackagesInfo.get(packname);
                        long fgDelta = u.getTotalTimeInForeground() - lastInfo.getForegroundTime();


                        if ((fgDelta > 0)) {

                            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                            Intent batteryStatus = handlercontext.registerReceiver(null, ifilter);
                            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                    status == BatteryManager.BATTERY_STATUS_FULL;


                            int currLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                            int maxLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                            double percentage =  Math.round((currLevel * 100.0) / maxLevel);

                            long binStart = (u.getLastTimeUsed() - fgDelta) / 1000;
                            long binEnd = (u.getLastTimeUsed() / 1000)+3;
                            long upTime = SystemClock.uptimeMillis();

                            // packagename, foreground duration, begin, end, txBytes, rxBytes, totalFlows, flows from others.
                            String appStatus =(start*1000)+","+upTime+","+packname + "," + fgDelta+","+binStart+","+binEnd +","+isCharging+","+percentage;
                            logQoSRelatedEvents(appStatus,usaePath);
                            Log.d("AppStat", appStatus);

                        }

                    }
                    ApplicationUsage latest = new ApplicationUsage(start, u.getTotalTimeInForeground(), u.getLastTimeUsed(), 0, 0, 0);
                    firstAllPackagesInfo.remove(packname);
                    firstAllPackagesInfo.put(packname, latest);
                    //firstAllPackagesInfo.put(u.getPackageName(),applicationUsage);
                }
            }




            private void logQoSRelatedEvents(String toWrite, String appaStatPath){
                //Log.d("logEvents",toWrite);
                String state = Environment.getExternalStorageState();
                FileWriter writer = null;
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    try{

                        writer = new FileWriter(appaStatPath, true);
                    }catch (IOException ie){Log.d("FileWriter", ie.toString());}
                }
                if(writer !=null){
                    BufferedWriter bw = new BufferedWriter(writer);
                    PrintWriter out = new PrintWriter(bw);
                    out.println(toWrite);
                    out.close();
                    try{
                        bw.close();

                    }catch (IOException ie){Log.d("BufferedWriter", ie.toString());}
                    try{
                        writer.close();
                    }catch (IOException we){Log.d("FileWriter", we.toString());}

                }
            }




            private void sampleTrafficBuild19(){

            }
            private final void sendBroadcast (String param, Long value){
                Intent intent = new Intent("Battery"); //put the same message as in the filter you used in the activity when registering the receiver
                intent.putExtra(param, value);
                LocalBroadcastManager.getInstance(handlercontext).sendBroadcast(intent);
            }


            public String getCurrentTimeStamp() {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            }

        });




    }




}

