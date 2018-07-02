package com.qoeapps.qoenforce.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.common.collect.Sets;

import com.qoeapps.qoenforce.datacontrol.AudioStat;
import com.qoeapps.qoenforce.datacontrol.FlowAppQueue;
import com.qoeapps.qoenforce.datacontrol.FlowMetaData;
import com.qoeapps.qoenforce.datacontrol.FlowTable;
import com.qoeapps.qoenforce.datacontrol.MetaMineConstants;
import com.qoeapps.qoenforce.datacontrol.SensorQueue;
import com.qoeapps.qoenforce.datacontrol.VpnFlowCache;
import com.qoeapps.qoenforce.utils.DeviceState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static android.os.Environment.getExternalStorageDirectory;
import static com.qoeapps.qoenforce.datacontrol.InternalMessages.SensorContext;

/**
 * Created by mohoque on 11/02/2017.
 */

public class DeviceMetaReader extends Service{

    public static String tcpLocation = "/proc/net/tcp";
    public static String udpLocation = "/proc/net/udp";
    public static String tcp6Location = "/proc/net/tcp6";
    public static String udp6Location = "/proc/net/udp6";

    private Boolean isCamoneinUse = null;
    private Boolean isCamtwoinUse = null;
    Iterator<FlowMetaData>  candidateApps = null;

    public  String CLASS_NAME = DeviceMetaReader.this.getClass().getSimpleName();
    public static boolean FILE_SAMPLER_STATUS = false;
    CameraManager cameraManager = null;
    AudioManager audioManager = null;
    PowerManager powerManager = null;

    TelephonyManager tm = null;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        Log.d(CLASS_NAME,"Device Profiler is also started");
        //socInterrupted.set(0,100,false);
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        tm = (TelephonyManager)  getSystemService(TELEPHONY_SERVICE);
        if (!FILE_SAMPLER_STATUS) {
            FILE_SAMPLER_STATUS = true;
            this.registerCameraService();
            updateFlowTables();
        }

        return super.onStartCommand(intent, flags, startId);

    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }


    public  void updateFlowTables() {

        final Handler handler = new Handler(Looper.getMainLooper());


        handler.post(new Runnable() {

            private String callType = null;
            private int delayed = 1000;
            private boolean candidateFound = false;
            private boolean sampling = true;
            private String mstoWrite = null;
            private long audioSession = 0;
            private long audioTerminated = 0;
            private boolean voipState = false;
            private boolean musicState = false;
            private boolean gsmCallState = false;
            private int inactivityCounter = 0;
            private String mainFlow;
            private boolean lastFrontCamera = false;
            private boolean lastBackCamera = false;
            private boolean lastScreenStatus = false;
            private FlowTable tcpFlows=null, udpFlows=null,tcp6Flows=null, udp6Flows=null;
            private AudioStat audioStat = null;
            private FileWriter writer = null;
            private String filePath = getExternalStorageDirectory()+"/AudioStats.txt";


            //private FlowAppQueue appQueue = FlowAppQueue.getInstance();

            AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {

                }
            };


            @Override
            public void run() {


                handler.postDelayed(this,delayed);





                if((udp6Flows==null)||(udpFlows==null))//|| (tcp6Flows == null) || (udp6Flows == null))
                {
                    //tcpFlows = new FlowTable(tcpLocation);
                    udpFlows = new FlowTable(udpLocation);
                    //tcp6Flows = new FlowTable(tcp6Location);
                    udp6Flows = new FlowTable(udp6Location);
                }

                // voip State is activated for both the VoIP and GSM calls
                voipState = DeviceState.audioStart(audioManager);
                musicState = audioManager.isMusicActive();

                if((audioStat != null) && (!voipState)){
                    Log.d("Voice QoE", "Voice missed "+musicState+" "+voipState);
                }

                if (audioStat == null){


                    if (tm.getCallState() != TelephonyManager.CALL_STATE_IDLE){
                        long lastAudioTime = System.currentTimeMillis();
                        audioStat = new AudioStat(MetaMineConstants.MetaMineGSM,lastAudioTime);
                        int sysVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                        boolean volFixed = audioManager.isVolumeFixed();
                        Log.d("GSMVoiceCall", "user in call "+"Settings Vol "+sysVolume+" maxvol "+maxVolume + "voilumeFixed "+volFixed);
                        logAudioEvents("user in call Settings Vol "+sysVolume+" maxvol "+maxVolume + "voilumeFixed "+volFixed);
                    }
                    if (audioStat == null && voipState){
                        if (this.audioSession == 0)
                            this.audioSession = System.currentTimeMillis();



                        /* In the case of outgoing call there could be some delay in updating the proc files.*/
                        Map<String, String> candidates = MetaMineConstants.getCandidateFlow(audioSession,1000);
                        if (candidates.size()>0){
                            audioStat = new AudioStat(MetaMineConstants.MetaMineVoIP,audioSession);
                            String msgForWrite ="";
                            for(Map.Entry<String, String> entry: candidates.entrySet()){
                                msgForWrite += audioSession+"::"+entry.getValue() + "::"+entry.getKey()+"\n";
                            }
                            msgForWrite += "\n";
                            logAudioEvents(msgForWrite);
                            //this.delayed = 1000;

                        }
                    }
                    if (musicState){
                        long lastAudioTime = DeviceState.isMusicOn(audioManager);

                        audioStat = new AudioStat(MetaMineConstants.MetaMineMusic,lastAudioTime);
                        Log.d("AudioManagerVoiceCall", "user in call");
                        Map<String, String> candidates = readProcFile();
                        if (candidates.size()>0){

                            String msgForWrite ="";
                            for(Map.Entry<String, String> entry: candidates.entrySet()){
                                msgForWrite += entry.getValue() + "::"+entry.getKey()+"\n";
                            }
                            msgForWrite += "\n";
                            logAudioEvents(msgForWrite);

                        }

                    }

                    if (audioStat!=null){
                        sampling = false;
                    }

                }





                // terminating the Voice.
                if (audioStat!=null) {
                    if ((audioStat.getAudioType() == MetaMineConstants.MetaMineGSM) && (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE)) {
                        long lastAudioTime = System.currentTimeMillis();
                        String msgForWrite = lastAudioTime + ":" + "voice call terminated";
                        logAudioEvents(msgForWrite);
                        audioStat = null;
                        sampling = true;

                    }

                }

                if (audioStat!=null){// dummp the deleted flows
                    if(((audioStat.getAudioType()==MetaMineConstants.MetaMineVoIP) && (!DeviceState.audioStart(audioManager))) ||
                            ((audioStat.getAudioType()==MetaMineConstants.MetaMineMusic)&&(!audioManager.isMusicActive())))
                    {

                        if(audioTerminated == 0)
                            audioTerminated = System.currentTimeMillis();

                        Map<String, String > deletedFlows = getLastDeletedFlows();
                        if (deletedFlows.size()>0){

                            Log.d("Session termination", "Session duration "+audioStat.getSessionDuration(System.currentTimeMillis()));
                            String msgForWrite ="";
                            for(Map.Entry<String, String> entry: deletedFlows.entrySet()){
                                msgForWrite += audioTerminated+"::"+entry.getValue() + "::"+entry.getKey()+"\n";
                            }
                            msgForWrite += "\n";
                            logAudioEvents(msgForWrite);
                            this.audioSession = 0;
                            this.audioTerminated = 0;
                            sampling = true;
                            audioStat = null;


                        }else{
                            inactivityCounter +=1;
                            Log.d("Session termination", "Waiting for session termination "+voipState);
                            if(inactivityCounter>10){
                                this.audioSession = 0;
                                this.audioTerminated = 0;
                                sampling = true;
                                audioStat = null;

                            }
                        }


                    }
                    if (inactivityCounter>0){
                        inactivityCounter -=1;
                    }
                }
                readProcFile();
                if (sampling){

                }



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

            private boolean sessionEnds(AudioStat stat){

                if ((stat.getAudioType()==MetaMineConstants.MetaMineGSM) &&
                    (tm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK))
                        return true;

                if (stat.getAudioType()==MetaMineConstants.MetaMineVoIP){
                    // Here we check for VoIP call termination
                    /* Very typical solution would be that we maintian a list of the candidate flows and check only the reccently deleted
                     * list of flows */

                }

                if (stat.getAudioType()==MetaMineConstants.MetaMineMusic){
                    //  // Here we check for Streaming  termination
                    /*Should be similar to the above*/
                }

                return false;
            }

            private void insertFlows (String [] newFlows){
               // Set<String> newFlowSet = Sets.newHashSet();

                Long beginTime = System.currentTimeMillis();
                for (String flow: newFlows) {
                    if (flow != null) {
                        flow = beginTime+":"+flow.trim();
                        //appQueue.addData(flow);
                    }
                }

               // return newFlowSet;
            }


            public  Map<String, String> readProcFile(){


                //List<String> finalFlows = new ArrayList<>();
                Map<String, String> allNewFlows = new HashMap<>();

                Log.d("read proc", "Reading flows");

                /*
                if (tcp6Flows != null){
                    Map<String, String> newFlows = tcp6Flows.updateAllFlows();
                    if(newFlows.size() >0) {
                        //Collections.addAll(finalFlows,newFlows);
                        allNewFlows.putAll(newFlows);
                    }
                }
                if (tcpFlows != null){
                    Map<String, String> newFlows =  tcpFlows.updateAllFlows();
                    if(newFlows.size() >0) {
                        //Collections.addAll(finalFlows,newFlows);
                        allNewFlows.putAll(newFlows);
                    }
                }*/
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
                //String[] finalArray = new String[allNewFlows.size()];
                //for(Map.Entry<String, String> entry: allNewFlows.entrySet()){}

                return allNewFlows;
            }

            public  Map<String, String> getLastDeletedFlows(){


                Map<String, String> allDeletedFlows = new HashMap<>();

                Log.d("read proc", "Reading flows");

                /*
                if (tcp6Flows != null){
                    Map<String, String> newFlows = tcp6Flows.getDeletedFlows();
                    if(newFlows.size() >0) {
                        //Collections.addAll(finalFlows,newFlows);
                        allDeletedFlows.putAll(newFlows);
                    }
                }
                if (tcpFlows != null){
                    Map<String, String> newFlows = tcpFlows.getDeletedFlows();
                    if(newFlows.size() >0) {
                        //Collections.addAll(finalFlows,newFlows);
                        allDeletedFlows.putAll(newFlows);
                    }
                }*/
                if (udpFlows != null){
                    Map<String, String> newFlows = udpFlows.getDeletedFlows();
                    if(newFlows.size() >0) {
                        //Collections.addAll(finalFlows,newFlows);
                        allDeletedFlows.putAll(newFlows);
                    }
                }
                if (udp6Flows != null){
                    Map<String, String> newFlows = udp6Flows.getDeletedFlows();
                    if(newFlows.size() >0) {
                        //Collections.addAll(finalFlows,newFlows);
                        allDeletedFlows.putAll(newFlows);
                    }
                }


                // Return only deleted UDP flows
                return allDeletedFlows;
            }

            public  Set<String> getCandidateFlows (Set <String> candidates, long time){

                Set<String> candidateFlowSet = Sets.newHashSet();

                for (String flow:candidates
                     ) {

                    if(!flow.contains("null"))
                    {


                        String fiveTuple= flow.split("::")[0];

                        String timeInfo = flow.split("::")[1];

                        Log.d("Flowtime ",timeInfo);

                        long value = Long.parseLong(timeInfo.split(":")[2]);

                        if (Math.abs(value-time)<=1000){


                            candidateFlowSet.add(fiveTuple+"::"+timeInfo);


                        }
                    }
                }
                return candidateFlowSet;
            }


        });



    }




    private void registerCameraService(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cameraManager.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {

                //private Bundle bundle = new Bundle();// cameraZeroAvailable = true;

                @Override
                public void onCameraAvailable(String cameraId) {
                    super.onCameraAvailable(cameraId);
                    if(Integer.parseInt(cameraId) == 0)
                        isCamoneinUse = false;

                    else
                        isCamtwoinUse = false;
                }

                @Override
                public void onCameraUnavailable(String cameraId) {

                    super.onCameraUnavailable(cameraId);
                    if(Integer.parseInt(cameraId) == 0)
                        isCamoneinUse = true;

                    else
                        isCamoneinUse = true;

                }
            }, null);
        }

    }

    private String[] getCandidateApps(){

        if(candidateApps == null){
            candidateApps = VpnFlowCache.getQueueInstance().getAllFlows();
        }else{

            while(candidateApps.hasNext()){
            }
        }


        return null;
    }
    private Boolean backCamerainUse(){
        if (Build.VERSION.SDK_INT>21)
            return isCamoneinUse;
        else
            return DeviceState.camOneinUse();
    }

    private Boolean frontCamerainUse(){
        if (Build.VERSION.SDK_INT>21)
            return isCamtwoinUse;
        else
            return DeviceState.camTwoinUse();
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
    }



    @Override
    public IBinder onBind(Intent intent) {
        // There are Bound an Unbound Services - you should read something about
        // that. This one is an Unbounded Service.
        return null;


    }



}
