package com.qoeapps.utilityqos;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.qoeapps.qoenforce.datacontrol.FlowTable;
import com.qoeapps.qoenforce.datacontrol.MetaMineConstants;
import com.qoeapps.qoenforce.datacontrol.TransportFlowCache;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.net.TrafficStats;


import static android.content.Context.ACTIVITY_SERVICE;
import static android.os.Environment.getExternalStorageDirectory;

/**
 * Created by mohoque on 13/01/2017.
 */

public class AppUsageSampling {
    private int buildVersion = 0;
    public static String tcpLocation = "/proc/net/tcp";
    public static String udpLocation = "/proc/net/udp";
    public static String tcp6Location = "/proc/net/tcp6";
    public static String udp6Location = "/proc/net/udp6";

    public AppUsageSampling(int buildVersion){
        this.buildVersion = buildVersion;
    }


    public void MobileAppUsageHandler(Context context){

        final Context handlercontext = context;


        /*
        *
        * This is very unreliable to read this from power_profile. Galaxy S3 capacity is 3200mAh. WTF
        * We need to have a service that has the capacity of different Smartphone models.
        *
        * */
        final Handler mainHandler = new Handler();
        mainHandler.post(new Runnable() {

            private int delayed = 1000;
            private FlowTable tcpFlows=null, udpFlows=null,tcp6Flows=null, udp6Flows=null;
            private HashMap<String, ApplicationUsage> firstAllPackagesInfo = new HashMap<>();
            private HashMap<String, Integer> localPackageId = new HashMap();
            private HashMap<Long,Object> bytesTxRx = new HashMap<>();
            private HashMap<Long,Object> flowString = new HashMap<>();

            private String filePath = getExternalStorageDirectory()+"/FlowStats.txt";
            private String usaePath = getExternalStorageDirectory()+"/UsageStats.txt";


            @Override
            public void run() {

                /* Get the average charging current*/

                mainHandler.postDelayed(this,delayed);
                Long curtime = System.currentTimeMillis();
                Long key = curtime/1000;

                sampleAppUsageBuild21(curtime);

                if((udp6Flows==null)||(udpFlows==null)|| (tcp6Flows == null) || (tcpFlows == null))
                {
                    tcpFlows = new FlowTable(tcpLocation,"tcp");
                    udpFlows = new FlowTable(udpLocation,"udp");
                    tcp6Flows = new FlowTable(tcp6Location,"tcp6");
                    udp6Flows = new FlowTable(udp6Location,"udp6");
                }

                // voip State is activated for both the VoIP and GSM calls


                flowString.put(key,readProcFile());
                Map.Entry<Long,Long> entry = new AbstractMap.SimpleEntry<>(getTXBytes(), getRXBytes());
                bytesTxRx.put(key,entry);


            }



            public  Map<String, String> readProcFile(){


                //List<String> finalFlows = new ArrayList<>();
                Map<String, String> allNewFlows = new HashMap<>();




                if (tcp6Flows != null){
                    Map<String, String> newFlows = tcp6Flows.updateAllFlows();
                    if(newFlows.size() >0) {
                        //Collections.addAll(finalFlows,newFlows);
                        allNewFlows.putAll(newFlows);
                        //TransportFlowCache.getCacheIntance().pushFlows(timeStamp,newFlows);
                    }
                }
                if (tcpFlows != null){
                    Map<String, String> newFlows =  tcpFlows.updateAllFlows();
                    if(newFlows.size() >0) {
                        allNewFlows.putAll(newFlows);
                    }
                }
                if (udpFlows != null){
                    Map<String, String> newFlows = udpFlows.updateAllFlows();
                    if(newFlows.size() >0) {
                        //Collections.addAll(finalFlows,newFlows);
                        allNewFlows.putAll(newFlows);
                    }
                }
                if (udp6Flows != null){
                    Map<String, String> newFlows = udp6Flows.updateAllFlows();
                    if(newFlows.size() >0) {
                        //Collections.addAll(finalFlows,newFlows);
                        allNewFlows.putAll(newFlows);
                    }
                }

                Log.d("AppUsage", "Read flows "+MetaMineConstants.globalFlowTable.size());

                return allNewFlows;
            }

            public long getRXBytes(){
                return TrafficStats.getTotalRxBytes();
            }

            public long getTXBytes(){
                return TrafficStats.getTotalTxBytes();
            }



            public String getRangeFlows(Long begin, Long end) {

                String flows = "";
                for (long j = begin; j<=end; ++j){
                    String []value = listMap.get(j);
                    if(value!=null){

                        for (int i = 0;i< value.length;++i) {
                            //String[] stringArray = {"a","b","c"};
                            StringUtils.join(value, "\n");
                        }
                    }
                }
                return flows;
            }


            private void sampleAppUsageBuild21(long start) {

                Calendar calendar = Calendar.getInstance();
                long endTime = System.currentTimeMillis();

                calendar.add(Calendar.SECOND,-30);
                long startTime = calendar.getTimeInMillis();

                final UsageStatsManager usageStatsManager = (UsageStatsManager) handlercontext.getSystemService(Context.USAGE_STATS_SERVICE);
                List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime);
                //Log.d("AppUsageSanple","size "+usageStatsList.size());
                for (UsageStats u : usageStatsList) {
                    String packname = u.getPackageName();
                    //Log.d("sampleAppUsage",packname + " "+u.getLastTimeStamp()+ " "+u.getLastTimeUsed());
                    if ((firstAllPackagesInfo.get(packname) != null) && (!localPackageId.isEmpty())) {
                        ApplicationUsage lastInfo = firstAllPackagesInfo.get(packname);
                        long fgDelta = u.getTotalTimeInForeground() - lastInfo.getForegroundTime();

                        long timeDelta = start - lastInfo.getPollingTime();
                        if ((fgDelta > 0)) {

                            long binStart = (u.getLastTimeUsed() - fgDelta) / 1000;
                            long binEnd = (u.getLastTimeUsed() / 1000);

                            // if the audio is active and then its the first application to change the status, then it is the owner of the
                            // audio signal. We need to split this audio to other applications as well if the
                            /*
                            * OwnerX (Audio+display ON) + (audio+displayOff)OwnerX + (audio+displayON)OtherY
                            *
                            *
                            * We also need to get the traffic stat for the user applications when they are in foreground//
                            * */
                            //generateBin(binStart,binEnd,packname);
                            /*
                            if ((chargeBins.get(binStart) != null) & (chargeBins.get(binEnd) != null)) {
                                long charge2 = chargeBins.get(binStart);
                                long charge1 = chargeBins.get(binEnd);
                                ResourseBin newbin = new ResourseBin();
                                newbin.setforgroundTime(binEnd - binStart);
                                newbin.setBinepochTime(binStart);
                                newbin.setCharge(charge2 - charge1);
                                newbin.setTxBytes(deltaTx);
                                newbin.setRxBytes(deltaRx);
                                newbin.setAppName(packname);
                                this.nbins.append(binIndex, newbin);
                                this.binIndex += 1;
                                Log.d(APP_NAME, "The charge values are  found" + " " + (charge2 - charge1));
                                Log.d(APP_NAME, "The Traffic Volumes TX found" + " " + deltaRx + " RX " + deltaTx);
                                //firstAllTrafficInfo.remove(packname);
                                //firstAllTrafficInfo.put(packname, newStat);

                            }*/

                            Log.d("AppStat", "" + packname + " " + fgDelta );

                        }




                        /*
                        if((audioFlag)){

                            if (isMusic)
                                Log.d(APP_NAME,"Music "+nbins.get(binIndex-1).getAppName());
                            if (audioMode == AudioManager.MODE_IN_COMMUNICATION)
                                Log.d(APP_NAME,"VoiP "+ nbins.get(binIndex-1).getAppName());
                            if (audioMode == AudioManager.MODE_IN_CALL)
                                Log.d(APP_NAME,"Voice Call "+ nbins.get(binIndex-1).getAppName());

                        }*/


                            /* while in the background it is possible to have two options
                            * (1) An application uses the speaker/microphone for VOIP communication
                            * (2) An application uses the speaker only for listening music
                            * (3) Or the device is completely idle
                            * */

                            /*
                            * An application statistics is not updated until it is removed from the foreground. So we cannot capture the
                            *
                            * present status of an application. We can only update the
                            *
                            * */


                    }
                    ApplicationUsage latest = new ApplicationUsage(start, u.getTotalTimeInForeground(), u.getLastTimeUsed(), 0, 0, 0);
                    firstAllPackagesInfo.remove(packname);
                    firstAllPackagesInfo.put(packname, latest);
                    //firstAllPackagesInfo.put(u.getPackageName(),applicationUsage);
                }



                long end = System.currentTimeMillis();
                //Log.d(APP_NAME, "Time took "+ (end-start));

            }


            private void logAudioEvents(String toWrite){
                Log.d("logAudioEvents",toWrite);
                String state = Environment.getExternalStorageState();
                FileWriter writer = null;
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    try{

                        writer = new FileWriter(filePath, true);
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



        });




    }




}

