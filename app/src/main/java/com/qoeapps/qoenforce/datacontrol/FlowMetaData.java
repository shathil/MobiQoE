package com.qoeapps.qoenforce.datacontrol;

/**
 * Created by mohoque on 12/02/2017.
 *
 * TODO: We can send an initial skeleton when the flow begins as soon as we have the application name and then
 * TODO: we can send a complete struct when the session manager is aborting the flow. We need to convert this ti
 * GJSON format.
 */

import org.json.JSONException;
import org.json.JSONObject;


public class FlowMetaData {
    private String src="";
    private int spt=0;
    private String dst="";
    private int dpt=0;
    private String transport="";
    private String appname="";
    private String contentType=""; //
    private int signal=0;
    private String network="";
    private double throughput=0.0;
    private int display=0;
    private String usercontext="";
    private long flowDuration=0;
    private long flowBytes=0;
    private String quality=""; // smooth or rebuffering

    public FlowMetaData() {
    }

    public void updateFlowMetaData(String param, String value) {
        switch (param) {
            case InternalMessages.appName:
                this.appname = value;
                break;
            case InternalMessages.srcAddress:
                this.src = value;
                break;
            case InternalMessages.srcPort:
                this.spt = Integer.parseInt(value);
                break;
            case InternalMessages.dstAddress:
                this.dst = value;
                break;
            case InternalMessages.dstPort:
                this.dpt = Integer.parseInt(value);
                break;
            case InternalMessages.transport:
                this.transport = value;
                break;
            case InternalMessages.brightness:
                this.display = Integer.parseInt(value);
                break;
            case InternalMessages.context:
                this.usercontext = value;
                break;
            case InternalMessages.duration:
                this.flowDuration = Long.parseLong(value);
                break;
            case InternalMessages.goodput:
                if ((value == null)&&(this.flowDuration>0)) {
                    this.throughput = (this.flowBytes * 8) / (this.flowDuration);
                }
                break;
            case InternalMessages.flowContent:
                this.contentType = value;
                break;
            case InternalMessages.QoS:
                this.quality = value;
                break;
            case InternalMessages.wireNetwork:
                this.network = value;
                break;
            case InternalMessages.wireSignal:
                this.signal = Integer.parseInt(value);
                break;

            case InternalMessages.bytesReceived:
                this.flowBytes = Long.parseLong(value);
                break;
        }
    }

    public String toJSON() {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(InternalMessages.srcAddress, src);
            jsonObject.put(InternalMessages.srcPort, spt);
            jsonObject.put(InternalMessages.dstAddress, dst);
            jsonObject.put(InternalMessages.dstPort, dpt);
            jsonObject.put(InternalMessages.transport, transport);
            jsonObject.put(InternalMessages.appName, appname);
            jsonObject.put(InternalMessages.brightness, display);
            jsonObject.put(InternalMessages.context, usercontext);
            jsonObject.put(InternalMessages.duration, flowDuration);
            jsonObject.put(InternalMessages.bytesReceived, flowBytes);
            jsonObject.put(InternalMessages.flowContent, contentType);
            jsonObject.put(InternalMessages.goodput, throughput);
            jsonObject.put(InternalMessages.wireNetwork, network);
            jsonObject.put(InternalMessages.wireSignal, signal);
            jsonObject.put(InternalMessages.QoS, quality);

            return jsonObject.toString();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }
}
