package com.qoeapps.qoenforce.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.common.collect.Sets;
import com.qoeapps.qoenforce.datacontrol.FlowAppQueue;
import com.qoeapps.qoenforce.datacontrol.FlowMetaData;
import com.qoeapps.qoenforce.datacontrol.FlowTable;
import com.qoeapps.qoenforce.datacontrol.SensorQueue;
import com.qoeapps.qoenforce.datacontrol.VpnFlowCache;
import com.qoeapps.qoenforce.utils.DeviceState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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

    public  void updateFlowTables() {

        final Handler handler = new Handler(Looper.getMainLooper());


        handler.post(new Runnable() {

            private int delayed = 1000;
            private boolean lastAudioStatus = false;
            private boolean lastFrontCamera = false;
            private boolean lastBackCamera = false;
            private boolean lastScreenStatus = false;
            private FlowTable tcpFlows=null, udpFlows=null,tcp6Flows=null, udp6Flows=null;

            private FlowAppQueue appQueue = FlowAppQueue.getInstance();

            @Override
            public void run() {

                handler.postDelayed(this,delayed);

                if((tcpFlows==null)||(udpFlows==null)|| (tcp6Flows == null) || (udp6Flows == null)) {
                    tcpFlows = new FlowTable(tcpLocation);
                    udpFlows = new FlowTable(udpLocation);
                    tcp6Flows = new FlowTable(tcp6Location);
                    udp6Flows = new FlowTable(udp6Location);
                }

                readProcFile();

                boolean audioStatus = DeviceState.audioMode(audioManager);
                boolean frontCamStatus = frontCamerainUse();
                boolean backCamStatus = backCamerainUse();
                boolean screenStatus = DeviceState.isScreenOn(powerManager);

                if((audioStatus != lastAudioStatus)|| (frontCamStatus != lastFrontCamera)||(backCamStatus != lastBackCamera)|| (screenStatus != lastScreenStatus)){
                    String allsenses = audioStatus+":"+frontCamStatus+":"+backCamStatus+":"+screenStatus;
                    SensorQueue.getQueueInstance().push(SensorContext,allsenses);
                    lastAudioStatus = audioStatus;
                    lastFrontCamera = frontCamStatus;
                    lastBackCamera = backCamStatus;
                    lastScreenStatus = screenStatus;
                }
            }




            private void insertFlows (String [] newFlows){
               // Set<String> newFlowSet = Sets.newHashSet();

                Long beginTime = System.currentTimeMillis();
                for (String flow: newFlows) {
                    if (flow != null) {
                        flow = beginTime+":"+flow.trim();
                        appQueue.addData(flow);
                    }
                }

               // return newFlowSet;
            }

            public  void readProcFile(){


                if (tcp6Flows != null){
                    String [] newFlows = tcp6Flows.getNewFlows();
                    if(newFlows.length>0) {
                        insertFlows(newFlows);
                    }
                }
                if (tcpFlows != null){
                    String [] newFlows = tcpFlows.getNewFlows();
                    if(newFlows.length>0)
                        insertFlows(newFlows);
                }
                if (udpFlows != null){
                    String [] newFlows = udpFlows.getNewFlows();
                    if(newFlows.length>0)
                        insertFlows(newFlows);
                }
                if (udp6Flows != null){
                    String [] newFlows = udp6Flows.getNewFlows();
                    if(newFlows.length>0)
                        insertFlows(newFlows);
                }
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
