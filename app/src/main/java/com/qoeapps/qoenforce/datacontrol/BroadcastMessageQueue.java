package com.qoeapps.qoenforce.datacontrol;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by mohoque on 02/02/2017.
 */

public class BroadcastMessageQueue {

    private static BroadcastMessageQueue queueInstance;

    public static BroadcastMessageQueue getQueueInstance(){
        if(queueInstance==null){

            synchronized (BroadcastMessageQueue.class){
                if(queueInstance==null)
                    queueInstance = new BroadcastMessageQueue();
            }
        }
        return queueInstance;
    }


    private Map<Class<?>, Object> maps = new HashMap<>();
    private Map<Integer,Object> listMap = new HashMap<>();

    public <T> void push(T data) {

        maps.put(data.getClass(),data);

    }

    public <T> T pop(Class classType) {

        T value = (T) maps.get(classType);
        maps.remove(classType);

        return value;
    }
    public <T> void push(int message, T data) {

        listMap.put(message,data);

    }

    public <T> T pop(int message) {

        T value = (T) listMap.get(message);
        listMap.remove(message);
        return value;
    }

    public <T> T get(int message) {

        T value = (T) listMap.get(message);
        return value;
    }

}
