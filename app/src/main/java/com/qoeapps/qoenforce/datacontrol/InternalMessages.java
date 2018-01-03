package com.qoeapps.qoenforce.datacontrol;

import android.util.LruCache;

import java.util.HashMap;

/**
 * Created by mohoque on 10/02/2017.
 */

public final class InternalMessages {

    // Message ids 0 and above 1000 is allocated to hold the application Name and such messages
    // should not be popped out, rather use get API. So do not use such constants.


    public static final int SLEEP_PERIOD = 00001;
    public static final int DSCP_CODES = 00002;
    /*
    public static final int SCREEN_STATUS = 00002;
    public static final int AUDIO_STATUS = 00003;
    */

    //public static final int READ_TCP_FLOW = 00002;
    /*
    public static  HashMap<String, String> clientVpnTcpFlows = new HashMap<>();
    public static  HashMap<String, String> clientVpnUdpFlows = new HashMap<>();
    public static  HashMap<String, String> vpnRemoteFlows = new HashMap<>();


    private static volatile LruCache<Long, String> tcpFlowToProcnameCache = new LruCache(4096);
    private static volatile LruCache<Long, String> udpFlowToProcnameCache = new LruCache(4096);
    private static volatile LruCache<Integer, String> uidToPackageNameCache = new LruCache(1024);

    */
    public static final String srcAddress="src";
    public static final String srcPort ="spt";
    public static final String dstAddress = "dst";
    public static final String dstPort = "dpt";
    public static final String transport = "transport";
    public static final String appUID = "uid";
    public static final String appName ="appname";
    public static final String flowContent="contentType"; //
    public static final String wireSignal ="signal";
    public static final String wireNetwork="network";
    public static final String goodput="throughput";
    public static final String brightness="display"; // o when display is off
    public static final String context="usercontext";
    public static final String duration="flowDuration";
    public static final String QoS="quality"; // smooth or rebuffering
    public static final String bytesReceived="flowBytes";

    /*
    private static final String audioconfer="audioconf";
    private static final String audiomusic ="audiomusic";
    private static final String cameraone = "cameraone";
    private static final String cameratwo="cameratwo";
    private static final String totalbytes="trafficstat";
    private static final String begintimestamp="begintime";
    private static final String ownerApp="ownerapp";
    private static final String endtimestamp="endtime";
    private static final String screenstatus="display";


    public static final String AudioStream = "audiostream";
    public static final String VideoStream = "videostream";
    public static final String AudioConference = "audioconference";
    public static final String VideoConference = "videoconference";
    public static final String DownlinkVideoConference = "downvideoconference";
    */



    public static  final String SensorContext = "SensorContext";
    public static  final String FlowOwnerApp = "FlowOwnerApp";
    public static  final String QoeEnforce = "SensorContext";


}