package com.qoeapps.qoenforce.datacontrol;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by mohoque on 02/02/2017.
 * The candidate flowqueue should have the timestamp and the flow strings this should implement the
 * circular in
 */

public class CandidateFlowQueue {

    private static CandidateFlowQueue queueInstance;

    public static CandidateFlowQueue getQueueInstance(){
        if(queueInstance==null){

            synchronized (CandidateFlowQueue.class){
                if(queueInstance==null)
                    queueInstance = new CandidateFlowQueue();
            }
        }
        return queueInstance;
    }

    private Map<Class<?>, Object> maps = new HashMap<>();
    private Map<String,String> listMap = new HashMap<>();


    public <T> void push(T data) {

        maps.put(data.getClass(),data);

    }

    public <T> T pop(Class classType) {

        T value = (T) maps.get(classType);
        maps.remove(classType);

        return value;
    }
    public <T> void push(String message, String data) {

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


    public boolean anyCandidate(){
        if (listMap.isEmpty())
            return false;
        else
            return true;
    }

    public Map<String, String> getCandidates(){
        return listMap;
    }
}
