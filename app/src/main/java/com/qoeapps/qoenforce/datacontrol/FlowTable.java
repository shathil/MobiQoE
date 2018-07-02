package com.qoeapps.qoenforce.datacontrol;

import android.util.Log;

import com.google.common.collect.Sets;
import com.google.common.net.*;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;


import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by mohoque on 07/05/2017.
 */

public class FlowTable {
    private String path = "";
    private Set<String> recentFlows;

    private Map<String, String> flowTable;
    private Map<String, String> newestFlows;
    private Map<String, String> deletedFlows;

    private static int maxFlows = 200;
    private String protocol = "";
    private String[] newFlows = null;

    public FlowTable(String filePath){
        this.path = filePath;
        this.recentFlows = Sets.newHashSet();
        this.recentFlows.addAll(Arrays.asList(readRandomLine(this.path)));
        this.flowTable = new HashMap<>(maxFlows);
    }

    public Map<String, String> getNewFlows(){
        return this.newestFlows;
    }

    public Map<String, String> getDeletedFlows(){
        return this.deletedFlows;
    }

    public Map<String, String>  updateAllFlows(){

        long timeStamp = System.currentTimeMillis();
        String [] readList = readRandomLine(this.path);
        Set<String> s2 = Sets.newHashSet();
        s2.addAll(Arrays.asList(readList));


        Map<String, String> latestFlows = new HashMap<>();
        deletedFlows = new HashMap<>();

        // the following approach is fine but has some issues for example adding timestamp.
        Set<String> temp = this.recentFlows;
        Set<String> diff = Sets.difference(s2, temp);
        String [] intersectFlows = diff.toArray(new String[diff.size()]);



        // this one gives the to be insereted flows

        for (String flow : intersectFlows
                ) {

            if (!flow.contains("null")) {
                String[] pairs = flow.split("::");
                String key = pairs[0];
                String value = timeStamp+":"+pairs[1];
                //Log.d("Newflow", key+"::"+value);
                latestFlows.put(key,value);
                this.recentFlows.add(flow);
                //this.flowTable.add(key,value);
                MetaMineConstants.globalFlowTable.put(key, value);
            }


        }



        Set<String> ndiff = Sets.difference(temp, s2);
        //this.deletedFlows = diff.toArray(new String[diff.size()]);
        String [] tobeDeletedFlows = ndiff.toArray(new String[ndiff.size()]);


        for (String flow : tobeDeletedFlows) {
            if (!flow.contains("null")) {
                String[] pairs = flow.split("::");
                String key = pairs[0];
                String value = timeStamp+":"+pairs[1];
                this.recentFlows.remove(flow);
                //Log.d("Deleted flow", key+"::"+value);
                this.deletedFlows.put(key, value);
                MetaMineConstants.globalFlowTable.remove(key);
            }

        }
        return latestFlows;
        //return intersectFlows;
    }

    public void  readFlows(){

        long timeStamp = System.currentTimeMillis();
        String [] readList = readRandomLine(this.path);
        Set<String> s2 = Sets.newHashSet();
        s2.addAll(Arrays.asList(readList));


        newestFlows = new HashMap<>();


        Set<String> temp = this.recentFlows;
        Set<String> diff = Sets.difference(s2, temp);
        String [] intersectFlows = diff.toArray(new String[diff.size()]);


        // this one gives the to be insereted flows
        for (String flow : intersectFlows
                ) {

            if (!flow.contains("null")) {
                String[] pairs = flow.split("::");
                String key = pairs[0];
                String value = pairs[1] + ":" + timeStamp;
                this.recentFlows.add(flow);
                newestFlows.put(key,value);
            }


        }



        // this one gives the to be deleted flows flows
        diff = Sets.difference(temp, s2);
        //deletedFlows = new String[diff.size()];
        String [] tobeDeletedFlows = diff.toArray(new String[diff.size()]);
        for (String flow : tobeDeletedFlows
                ) {
            String [] pairs = flow.split("::");
            String key = pairs[0];
            String value = pairs[1] + ":" + timeStamp;
            this.deletedFlows.put(key, value);
            this.recentFlows.remove(flow);
        }
        return;
    }


    public long getFlowtime(String flow){

        long flowBegin = 0;
        String val = this.flowTable.get(flow);
        if (val !=null)
            flowBegin = Long.parseLong(val.split(":")[2]);
        return flowBegin;
    }



    private String [] readRandomLine (String location){
        String [] entries = new String[maxFlows];//null;
        boolean flag6 = false;
        if(path.contains("tcp6")||path.contains("udp6"))
            flag6 = true;
        int count = 0;
        try {
            RandomAccessFile reader = new RandomAccessFile(location, "r");
            long timeStamp = System.currentTimeMillis()/1000;

            for(;;){
                // This line is to avoid the heading of the files.
                String line = reader.readLine();
                line = reader.readLine();
                if(line == null)
                    break;
                else{
                    String temp = line.trim();
                    String[] toks = temp.split("\\s");
                    String[] srcPair = toks[1].split(":");
                    String[] dstPair = toks[2].split(":");
                    String appId = toks[7].trim();
                    if (appId.length() == 0) {
                        for (int i = 8; i < toks.length; ++i) {
                            if (toks[i].length() != 0) {
                                appId = toks[i].trim();
                                break;
                            }

                        }
                    }


                    String srcAddress = "", dstAddress = "";
                    if (flag6) {
                        srcAddress = getAddress6(srcPair[0]);
                        dstAddress = getAddress6(dstPair[0]);

                    } else {
                        srcAddress = getAddress(srcPair[0]);
                        dstAddress = getAddress(dstPair[0]);
                    }
                    int srcPort = getIntPort(srcPair[1]);
                    int dstPort = getIntPort(dstPair[1]);


                    String flowKey = "";
                    if (path.contains("tcp"))
                        flowKey = srcAddress + ":" + srcPort + ":" + dstAddress + ":" + dstPort + ":" + "tcp";
                    if (path.contains("udp"))
                        flowKey = srcAddress + ":" + srcPort + ":" + dstAddress + ":" + dstPort + ":" + "udp";

                    String AppName = MetaMineConstants.IdAppNameMaps.get(Integer.parseInt(appId));
                    //if ((AppName != null) && (!AppName.contains("root")||!AppName.contains("MobieQoE"))) {
                    if ((AppName != null) && !AppName.contains("root")) {
                        String value = appId + ":" + AppName;
                        entries[count] =flowKey+"::"+value;
                        ++count;
                    }

                }
            }
            reader.close();

        }catch(IOException ie){}

        return entries;
    }
    // get IPV4 address.

    private  String getAddress(final String hexa) {

        try {
            final long v = Long.parseLong(hexa, 16);
            final long adr = (v >>> 24) | (v << 24) | ((v << 8) & 0x00FF0000) | ((v >> 8) & 0x0000FF00);
            String address = ((adr >> 24) & 0xff) + "." + ((adr >> 16) & 0xff) + "." + ((adr >> 8) & 0xff) + "." + (adr & 0xff);
            return address;
        } catch(Exception e) {
            //Log.w("NetworkLog", e.toString(), e);
            return "-1.-1.-1.-1";
        }
    }


    // This is also working fine. Converting IPV6 to IPV4
    private  String getAddress6(final String hexa) {
        try {
            final String ip4[] = hexa.split("0000000000000000FFFF0000");
            if(ip4.length == 2) {
                final long v = Long.parseLong(ip4[1], 16);
                final long adr = (v >>> 24) | (v << 24) | ((v << 8) & 0x00FF0000) | ((v >> 8) & 0x0000FF00);
                String addr = ((adr >> 24) & 0xff) + "." + ((adr >> 16) & 0xff) + "." + ((adr >> 8) & 0xff) + "." + (adr & 0xff);

                return addr;
            } else {
                if (hexa.split("000000000000000000000000").length==2)
                //Log.d("IPV6 address",hexa+" "+ hexa.length());
                    return "0.0.0.0";
                else
                    return "-2.-2.-2.-2";
            }
        } catch(Exception e) {
            //Log.w("NetworkLog", e.toString(), e);
            return "-1.-1.-1.-1";
        }
    }

    private  int getIntPort(final String hexa) {
        try {
            return Integer.parseInt(hexa, 16);
        } catch(Exception e) {
            Log.w("NetworkLog", e.toString(), e);
            return -1;
        }
    }


}
