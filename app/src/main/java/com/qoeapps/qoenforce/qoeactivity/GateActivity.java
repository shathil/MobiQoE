/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.qoeapps.qoenforce.qoeactivity;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.mobiqoe.enforce.R;
import com.qoeapps.qoenforce.datacontrol.BroadcastMessageQueue;
import com.qoeapps.qoenforce.datacontrol.InternalMessages;
import com.qoeapps.qoenforce.datacontrol.MetaMineConstants;
//import com.metamine.miner.R;
import com.qoeapps.qoenforce.services.VpnPacketReader;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GateActivity extends AppCompatActivity
{
    static {
        System.loadLibrary("native-lib");
    }
    private static final int VPN_REQUEST_CODE = 0x0F;

    private String CLASS_NAME = GateActivity.this.getClass().getSimpleName();
    private boolean waitingForVPNStart;

    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (VpnPacketReader.BROADCAST_GATE_STATE.equals(intent.getAction()))
            {
                if (intent.getBooleanExtra("running", false))
                    waitingForVPNStart = false;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gate_keeper);


        final RadioGroup radioGroup = (RadioGroup) findViewById(R.id.qoesel);

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int selectedId) {

                // find which radio button is selected
                if(selectedId == R.id.zero_be)
                    BroadcastMessageQueue.getQueueInstance().push(InternalMessages.DSCP_CODES,0);
                else if(selectedId == R.id.ef46)
                    BroadcastMessageQueue.getQueueInstance().push(InternalMessages.DSCP_CODES,184);
                else if(selectedId == R.id.af31)
                    BroadcastMessageQueue.getQueueInstance().push(InternalMessages.DSCP_CODES,26);
                else if(selectedId == R.id.af33)
                    BroadcastMessageQueue.getQueueInstance().push(InternalMessages.DSCP_CODES,30);
                else if(selectedId == R.id.af41)
                    BroadcastMessageQueue.getQueueInstance().push(InternalMessages.DSCP_CODES,34);
                else if(selectedId == R.id.af43)
                    BroadcastMessageQueue.getQueueInstance().push(InternalMessages.DSCP_CODES,38);


            }

        });

        final Button vpnButton = (Button)findViewById(R.id.vpn);
        vpnButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                // this is to fall back to the basic sampling service if the VPN connetion cannot be established
                if (!isVirtualNetworkActive()) {
                    startVPN();
                    Log.d(CLASS_NAME,"VPN service and the device profiler started started ...");
                }
                else{

                }

                /*
                Intent procIntent = new Intent(GateActivity.this, DeviceMetaReader.class);
                startService(procIntent);*/
                enableButton(false);

            }
        });
        waitingForVPNStart = false;
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStateReceiver,
                new IntentFilter(VpnPacketReader.BROADCAST_GATE_STATE));


        new InstalledApplication().execute("");

    }

    private boolean isVirtualNetworkActive(){

        List<String> networkList = new ArrayList<>();
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isUp())
                    networkList.add(networkInterface.getName());
            }
        } catch (Exception ex) {
            Log.d("Hithere","isVpnUsing Network List didn't received");
        }
        if (networkList.contains("tun0")|| networkList.contains("ppp0"))
            return true;
        else
            return false;

    }
    private void startVPN()
    {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK)
        {
            waitingForVPNStart = true;
            startService(new Intent(this, VpnPacketReader.class));
            enableButton(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        enableButton(!waitingForVPNStart && !VpnPacketReader.isRunning );
    }

    private void enableButton(boolean enable)
    {
        final Button vpnButton = (Button) findViewById(R.id.vpn);
        if (enable)
        {
            vpnButton.setEnabled(true);
            vpnButton.setText(R.string.start_vpn);
        }
        else
        {
            vpnButton.setEnabled(false);
            vpnButton.setText(R.string.stop_vpn);
        }
    }


    private class InstalledApplication extends AsyncTask<String, Void, String> {

        final PackageManager mmpkgManager = getApplicationContext().getPackageManager();
        final ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        HashMap<String,Integer> idPackages= new HashMap<String,Integer>();
        HashMap<Integer, String> idNames= new HashMap<Integer, String>();


        @Override
        protected String doInBackground(String... params) {
            List<ApplicationInfo> installedApps = mmpkgManager.getInstalledApplications(0);
            for (ApplicationInfo ai : installedApps) {
                idPackages.put(ai.packageName, ai.uid);
                String apnamr = ai.loadLabel(mmpkgManager).toString();
                idNames.put(ai.uid, apnamr);

            }
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
            if(result.equals("Executed")) {
                //BroadcastMessageQueue.getQueueInstance().push(Integer.valueOf(0), "root");
                MetaMineConstants.IdAppNameMaps.put(Integer.valueOf(0), "root");
                for (Map.Entry<Integer, String> entry : idNames.entrySet()) {
                    Integer id = entry.getKey();
                    String appName = entry.getValue();
                    Log.d("ApplicationId","id "+id+" appname "+appName);
                    //BroadcastMessageQueue.getQueueInstance().push(id, appName);
                    MetaMineConstants.IdAppNameMaps.put(id, appName);
                }
                // this is for the root user.

            }
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }



}
