package com.qoeapps.qoenforce.datacontrol;

import android.util.Log;

import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by mohoque on 22/02/2017.
 */

public class MetaMineConstants {

    public static final Map<Integer, String> IdAppNameMaps = new HashMap<>();

    private static final Map<String, Integer> DIFF_SERV_NAMES
            = new HashMap<String, Integer>();
    /** Common names used for Differentiated Services values. */


    public static String MetaMineVoIP = "VoIP";
    public static String MetaMineGSM = "GSM";
    public static String MetaMineMusic = "Music";
    public static final Map<String, String> globalFlowTable = new HashMap<String,String>();
    public static final Map<String, String> deletedFlows = new HashMap<String,String>();

    static {

        DIFF_SERV_NAMES.put("CS0", 0);
        DIFF_SERV_NAMES.put("CS1", 8);
        DIFF_SERV_NAMES.put("CS2", 16);
        DIFF_SERV_NAMES.put("CS3", 24);
        DIFF_SERV_NAMES.put("CS4", 32);
        DIFF_SERV_NAMES.put("CS5", 40);
        DIFF_SERV_NAMES.put("CS6", 48);
        DIFF_SERV_NAMES.put("CS7", 56);
        DIFF_SERV_NAMES.put("AF11", 10);
        DIFF_SERV_NAMES.put("AF12", 12);
        DIFF_SERV_NAMES.put("AF13", 14);
        DIFF_SERV_NAMES.put("AF21", 18);
        DIFF_SERV_NAMES.put("AF22", 20);
        DIFF_SERV_NAMES.put("AF23", 22);
        DIFF_SERV_NAMES.put("AF31", 26);
        DIFF_SERV_NAMES.put("AF32", 28);
        DIFF_SERV_NAMES.put("AF33", 30);
        DIFF_SERV_NAMES.put("AF41", 34);
        DIFF_SERV_NAMES.put("AF42", 36);
        DIFF_SERV_NAMES.put("AF43", 38);
        DIFF_SERV_NAMES.put("EF", 46);
    }

    public static long getFlowTime(String flow){

        long flowBegin = 0;
        String val = globalFlowTable.get(flow);
        if (val !=null)
            flowBegin = Long.parseLong(val.split(":")[2]);
        return flowBegin;

    }

    public static boolean flowExist(String flow){
        return globalFlowTable.containsKey(flow);

    }
    public static Map<String,String> getCandidateFlow (long time, int delta){

        boolean cadFlag = false;
        Map<String,String> candidateFlowSet = new HashMap<>();
        for (Map.Entry<String, String> entry : globalFlowTable.entrySet())
        {
            String key = entry.getKey();
            long value = Long.parseLong(entry.getValue().split(":")[0]);
            //String protocol = entry.getValue().split(":")[2];
            //Log.d("All Flows ",key+"::"+entry.getValue()+"time delta "+Math.abs(time-value)+"ms.");
            if (Math.abs(value-time)<=10000){

                Log.d("Live table", "checking the live table: "+key);
                candidateFlowSet.put(key,entry.getValue());
                cadFlag = true;
            }

            if(!cadFlag){

            }

            //Log.d("All Flows ",key+"::"+value);
        }
        return candidateFlowSet;
    }
}
