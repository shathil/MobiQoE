package com.qoeapps.qoenforce.datacontrol;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by mohoque on 02/02/2017.
 * For All Flows. This class can be used to query the flows according to time. I think that
 * it would better to keep a snapshot of the new flows according to time. Then we can query
 * according to time.
 *
 */

public class AppFlowCache {

    private static AppFlowCache queueInstance;

    public static AppFlowCache getCacheIntance(){
        if(queueInstance==null){

            synchronized (AppFlowCache.class){
                if(queueInstance==null)
                    queueInstance = new AppFlowCache();
            }
        }
        return queueInstance;
    }



    private Map<Long,Object> listMap = new HashMap<>();

    /*
    private Map<Class<?>, Object> maps = new HashMap<>();
    public <T> void push(T data) {

        maps.put(data.getClass(),data);

    }

    public <T> T pop(Class classType) {

        T value = (T) maps.get(classType);
        maps.remove(classType);

        return value;
    }*/
    public <T> void push(Long msg, Object data) {

        listMap.put(msg,data);

    }


    public <T> T pop(Long message) {

        T value = (T) listMap.get(message);
        listMap.remove(message);
        return value;
    }

    public <T> T get(Long message) {

        T value = (T) listMap.get(message);
        return value;
    }


    public void clearCache(){
        listMap.clear();
    }

    public int cacheSize(){
        return listMap.size();
    }

    public Map<Long, Object> getAllCandidates(){
        return listMap;
    }

}
