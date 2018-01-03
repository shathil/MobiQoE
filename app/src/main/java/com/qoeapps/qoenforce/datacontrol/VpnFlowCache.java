package com.qoeapps.qoenforce.datacontrol;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This queue contians the flows generated from the VPN client against the flow generated from the applications.
 * Created by mohoque on 02/02/2017.
 */

public class VpnFlowCache {

    private static VpnFlowCache queueInstance;

    public static VpnFlowCache getQueueInstance(){
        if(queueInstance==null){

            synchronized (VpnFlowCache.class){
                if(queueInstance==null)
                    queueInstance = new VpnFlowCache();
            }
        }
        return queueInstance;
    }


    private Map<Class<?>, Object> maps = new HashMap<>();
    private Map<String,FlowMetaData> listMap = new HashMap<>();

    public <T> void push(T data) {

        maps.put(data.getClass(),data);

    }

    public <T> T pop(Class classType) {

        T value = (T) maps.get(classType);
        maps.remove(classType);
        return value;
    }

    public <T> T get(Class classType) {

        T value = (T) maps.get(classType);
        return value;
    }


    public <T> void push(String message, FlowMetaData flow) {
        listMap.put(message,flow);
    }

    public <T> T pop(String message) {

        T value = (T) listMap.get(message);
        listMap.remove(message);
        return value;
    }

    public <T> T get(String message) {

        T value = (T) listMap.get(message);
        return value;
    }

    public Iterator<FlowMetaData> getAllFlows(){

        return listMap.values().iterator();
    }

    public void clearQueue(){
        listMap.clear();
    }
    public int queueSize(){
        return  listMap.size();
    }
}
