package com.qoeapps.qoenforce.datacontrol;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Created by mohoque on 20/07/2017.
 */

public class FlowQoeMetadata {

    private static Object syncData = new Object();
    private static Object syncObj = new Object();
    private volatile static FlowQoeMetadata instance = null;
    private Queue<String> data;
    public static FlowQoeMetadata getInstance(){
        if(instance == null){
            synchronized(syncObj){
                if(instance == null){
                    instance = new FlowQoeMetadata();
                }
            }
        }
        return instance;
    }
    private FlowQoeMetadata(){
        data = new LinkedList<String>();
    }
    public void addData(String flowApp){
        synchronized(syncData){
            try{
                data.add(flowApp);
            }catch(IllegalStateException ex){

            }catch(NullPointerException ex1){

            }catch(Exception ex2){}
        }
    }
    public String getData(){
        String flowApp;
        synchronized(syncData){
            flowApp = data.poll();
        }
        return flowApp;
    }


}
