package com.qoeapps.qoenforce.datacontrol;

import android.util.Log;

import com.google.common.collect.Sets;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Set;

/**
 * Created by mohoque on 07/05/2017.
 */

public class FlowTable {
    private String path = "";
    private Set<String> recentFlows;
    private static int maxFlows = 200;
    private String protocol = "";

    public FlowTable(String filePath){
        this.path = filePath;
        this.recentFlows = Sets.newHashSet();
        this.recentFlows.addAll(Arrays.asList(readRandomLine(this.path)));
    }

    //public Set<String> getNewFlows(){
    public String [] getNewFlows(){

        String [] readList = readRandomLine(this.path);
        Set<String> s2 = Sets.newHashSet();
        s2.addAll(Arrays.asList(readList));

        Set<String> diff = Sets.difference(s2, this.recentFlows);
        String [] intersectFlows = diff.toArray(new String[diff.size()]);
        //Log.d("S1 ",path+" "+this.recentFlows.size()+" "+s2.size()+" "+intersectFlows.length);

        if (intersectFlows.length >0) {
            this.recentFlows = Sets.newHashSet();
            this.recentFlows.addAll(s2);
        }
        return intersectFlows;
        //return diff;
    }

    public String [] getAllFlows(){

        String [] readList = readRandomLine(this.path);
        Set<String> s2 = Sets.newHashSet();
        s2.addAll(Arrays.asList(readList));

        Set<String> diff = Sets.difference(s2, this.recentFlows);
        String [] intersectFlows = diff.toArray(new String[diff.size()]);
        //Log.d("S1 ",path+" "+this.recentFlows.size()+" "+s2.size()+" "+intersectFlows.length);

        if (intersectFlows.length >0) {
            this.recentFlows = Sets.newHashSet();
            this.recentFlows.addAll(s2);
        }
        return intersectFlows;
        //return diff;
    }


    private String [] readRandomLine (String location){
        String [] entries = new String[maxFlows];//null;
        boolean flag6 = false;
        if(path.contains("tcp6")||path.equals("udp6"))
            flag6 = true;
        int count = 0;
        try {
            RandomAccessFile reader = new RandomAccessFile(location, "r");
            //long timeStamp = System.currentTimeMillis()/1000;

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
                    //if (path.contains("tcp6"))
                    //    flowKey = srcAddress + ":" + srcPort + "::" + dstAddress + ":" + dstPort + "::" + "tcp6";
                    //if (path.contains("udp6"))
                    //    flowKey = srcAddress + ":" + srcPort + "::" + dstAddress + ":" + dstPort + "::" + "udp6";

                    //String AppName = BroadcastMessageQueue.getQueueInstance().get(Integer.parseInt(appId));
                    String AppName = MetaMineConstants.IdAppNameMaps.get(Integer.parseInt(appId));
                    if ((AppName != null) && (!AppName.contains("MetaMine"))) {
                        String value = appId + ":" + AppName;//+":"+txBytes+":"+rxBytes;
                        entries[count] =flowKey+":"+value;
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
