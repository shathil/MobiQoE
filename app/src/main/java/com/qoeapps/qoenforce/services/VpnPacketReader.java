package com.qoeapps.qoenforce.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.qoeapps.qoenforce.datacontrol.BroadcastMessageQueue;
import com.qoeapps.qoenforce.protocols.ClientPacketWriterImpl;
import com.qoeapps.qoenforce.protocols.IClientPacketWriter;
import com.qoeapps.qoenforce.protocols.SessionHandler;
import com.qoeapps.qoenforce.protocols.socket.DataConst;
import com.qoeapps.qoenforce.protocols.socket.IProtectSocket;
import com.qoeapps.qoenforce.protocols.socket.SocketNIODataService;
import com.qoeapps.qoenforce.protocols.socket.SocketProtector;
import com.qoeapps.qoenforce.protocols.tcp.PacketHeaderException;
import com.qoeapps.qoenforce.utils.DeviceState;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static com.qoeapps.qoenforce.datacontrol.InternalMessages.SLEEP_PERIOD;

/**
 * Created by mohoque on 02/02/2017.
 */
public class VpnPacketReader extends VpnService implements Handler.Callback, IProtectSocket,Runnable {


    public static final String BROADCAST_GATE_STATE = "ektara.com.services.GATE_STATE";

    public static boolean isRunning = false;
    public static final String TAG="Gatekeeper";
    private PendingIntent mConfigureIntent;

    private boolean serviceValid;

    private Thread mThread;
    private ParcelFileDescriptor mInterface = null;
    private AudioManager audioManager = null;

    Builder builder = new Builder();
    private SocketNIODataService dataservice;

    private Thread dataServiceThread;

    private static String tcpLocation = "/proc/net/tcp";
    private static String udpLocation = "/proc/net/udp";
    private static String tcp6Location = "/proc/net/tcp6";
    private static String udp6Location = "/proc/net/udp6";



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start a new session by creating a new thread.
        isRunning = true;
        audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);


        // Stop the previous session by interrupting the thread.
        if (mThread != null) {
            mThread.interrupt();
            int reps = 0;
            while(mThread.isAlive()){
                Log.i(TAG, "Waiting to exit " + ++reps);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
            mThread = null;
        }

        // Start a new session by creating a new thread.
        mThread = new Thread(this, "CaptureVpnThread");
        mThread.start();
        return START_STICKY;
    }


    boolean startVpnService() throws IOException{
        // If the old interface has exactly the same parameters, use it!
        if (mInterface != null) {
            Log.i(TAG, "Using the previous interface");
            return false;
        }

        Log.i(TAG, "startVpnService=> create builder");
        // Configure a builder while parsing the parameters.
        Builder builder = new Builder()
                .addAddress(DataConst.vpnSourceIPAddress, 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setSession("MetaMine")
                .setConfigureIntent(mConfigureIntent);
        if (mInterface != null) {
            try {
                mInterface.close();
            } catch (Exception e) {
                Log.e(TAG, "Exception when closing mInterface:" + e.getMessage());
            }
        }
        Log.i(TAG, "startVpnService=> builder.establish()");
        mInterface = builder.establish();
        if(mInterface != null){
            Log.i(TAG, "\n\\\n  VPN Established:interface = " + mInterface.getFd() + "\n/\n");
            return true;
        }else{
            Log.d(TAG,"mInterface is null");
            return false;
        }
    }

    @Override
    public void run() {
        Log.i(TAG, "running vpnService");
        boolean success = false;
        SocketProtector protector = SocketProtector.getInstance();
        protector.setProtector(this);

        try {
            success = startVpnService();
        } catch (IOException e) {
            Log.e(TAG,e.getMessage());
        }

        if(success){
            try {
                startCapture();
                Log.i(TAG, "Capture completed");
            } catch (IOException e) {
                Log.e(TAG,e.getMessage());
            }
        }else{
            Log.e(TAG,"Failed to start VPN Service!");
        }

        Log.i(TAG, "Closing Capture files");
    }



    /**
     * start background thread to handle client's socket, handle incoming and outgoing packet from VPN interface
     * @throws IOException
     */
    void startCapture() throws IOException{

        Log.i(TAG, "startCapture() :capture starting");


        // Probably we need to think about the java nio channels. as they are faster than file input/output streamings.
        // Packets to be sent are queued in this input stream.
        FileInputStream clientreader = new FileInputStream(mInterface.getFileDescriptor());

        // Packets received need to be written to this output stream.
        FileOutputStream clientwriter = new FileOutputStream(mInterface.getFileDescriptor());
        FileChannel channelwriter = new FileOutputStream(mInterface.getFileDescriptor()).getChannel();

        // Allocate the buffer for a single packet.
        ByteBuffer packet = ByteBuffer.allocate(4096);

        // Probably we could implement the channel inside the PacketWriters
        IClientPacketWriter clientpacketwriter = new ClientPacketWriterImpl(clientwriter);

        //IClientPacketWriter channelpacketwriter = new ClientPacketWriterImpl(channelwriter);
        SessionHandler handler = SessionHandler.getInstance();
        handler.setWriter(clientpacketwriter);

        //background task for non-blocking socket
        dataservice = new SocketNIODataService();
        dataservice.setWriter(clientpacketwriter);
        dataServiceThread = new Thread(dataservice);
        dataServiceThread.start();

        //background task for writing packet data to pcap file
        /*
        packetbgWriter = new SocketDataPublisher();
        packetbgWriter.subscribe(this);
        packetqueueThread = new Thread(packetbgWriter);
        packetqueueThread.start();
        */
        byte[] data;
        ByteBuffer deviceNetwork = null;
        int length;
        serviceValid = true;
        int idle = 0;
        int idleIncr = 50;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);



        while (serviceValid) {
            idle=0;
            //read packet from vpn client
            data = packet.array();
            length = clientreader.read(data);
            if(length > 0){

                // Hoque: This idle period is a problem for the VoIP  applications. It should be adaptive.
                // we could spawn a new handler thread here which checks the packet type, checks local flowtable
                // Probably we should include this in the packer handler class, i.e, SESSIONHANDLER.



                try {

                    handler.handlePacket(data, length);
                    try{
                        int r = (int) (Math.random() * (150 - 50)) + 50;
                        Thread.sleep(r);
                        BroadcastMessageQueue.getQueueInstance().push(SLEEP_PERIOD,r);
                        Log.d(TAG,"Slept: "+r+"ms.");

                    } catch (InterruptedException e) {
                        Log.d(TAG,"Failed to sleep: "+ e.getMessage());
                    }

                } catch (PacketHeaderException e) {
                    Log.e(TAG,e.getMessage());
                }

                packet.clear();
            }


            else{
                try {
                    Thread.sleep(100);
                    BroadcastMessageQueue.getQueueInstance().push(SLEEP_PERIOD,100);

                } catch (InterruptedException e) {
                    Log.d(TAG,"Failed to sleep: "+ e.getMessage());
                }
            }


        }
        Log.i(TAG, "capture finished: serviceValid = "+serviceValid);
    }


    // This is place where the threads should to read



    @Override
    public boolean handleMessage(Message message) {
        if (message != null) {
            Log.d(TAG, "handleMessage:" + getString(message.what));
            Toast.makeText(this.getApplicationContext(), message.what, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    public void protectSocket(DatagramSocket socket) {
        this.protect(socket);
    }


    @Override
    public boolean stopService(Intent name) {
        Log.i(TAG, "stopService(...)");

        serviceValid = false;
        //	closeTraceFiles();
        return super.stopService(name);
    }

    @Override
    public void protectSocket(Socket socket) {
        this.protect(socket);
    }
    /**
     * called back from background thread when new packet arrived
     */


    @Override
    public void protectSocket(int socket) {
        this.protect(socket);
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        if (mThread != null) {
            mThread.interrupt();
        }
        super.onDestroy();
    }
}