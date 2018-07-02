package com.qoeapps.qoenforce.datacontrol;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by mohoque on 07/03/2018.
 */

public class AudioStat {
    private String calltype;
    private int countTerrupt;
    private long lastInterruptTime;
    private int intSum;
    private long sessionBegin;
    private Map<String, String> flowCandidates;

    public AudioStat(String callType, long sessionStart){
        this.calltype = callType;
        this.sessionBegin = sessionStart;
        this.flowCandidates = null;
    }

    public void setCallInterrupt(long timeStamp){

        intSum += 1;
        // constructing interrupted bins. The minimum size of the bin is one second/ 1000ms

        if (timeStamp-lastInterruptTime > 1000){
            countTerrupt += 1;
        }
        lastInterruptTime = timeStamp;
    }

    public int getSessionDuration (long endTime){
        int sessionDuration = (int)(endTime-sessionBegin)/1000;
        return sessionDuration;
    }

    public int getTotalaudioMiss(){
        return intSum;
    }

    public int getIntEvents(){
        return countTerrupt;
    }


    public String getAudioType(){
        return this.calltype;
    }

    public void setCandidateFlows(Map<String, String> candidates, long timeStamp){
        flowCandidates = new HashMap<>();
        for(Map.Entry<String, String> entry: candidates.entrySet()){
            Long flowTime = Long.parseLong(entry.getValue().split(":")[2]);
            if (Math.abs(timeStamp-flowTime)<=1000){
                flowCandidates.put(entry.getKey(),entry.getValue());
                Log.d("Flowinformation ", "Candidate found " +entry.getKey());
            }

        }
    }

    public boolean checkFlowStatus(){

        if((flowCandidates!=null)&&(flowCandidates.size()>0)){

        }
        return false;
    }
}
