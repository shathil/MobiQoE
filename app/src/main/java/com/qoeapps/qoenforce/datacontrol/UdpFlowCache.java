package com.qoeapps.qoenforce.datacontrol;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by mohoque on 02/02/2017.
 * For UDP flows
 */

public class UdpFlowCache {

    private static UdpFlowCache queueInstance;

    public static UdpFlowCache getCacheIntance(){
        if(queueInstance==null){

            synchronized (UdpFlowCache.class){
                if(queueInstance==null)
                    queueInstance = new UdpFlowCache();
            }
        }
        return queueInstance;
    }


    private Map<Class<?>, Object> maps = new HashMap<>();
    private Map<String,Object> listMap = new HashMap<>();

    public <T> void push(T data) {
        maps.put(data.getClass(),data);
    }

    public <T> T pop(Class classType) {
        T value = (T) maps.get(classType);
        maps.remove(classType);

        return value;
    }
    public <T> void push(String message, T data) {

        listMap.put(message,data);

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
    public void clearCache(){
        listMap.clear();
    }
    public int cacheSize(){return listMap.size();}

}
