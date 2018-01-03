package com.qoeapps.qoenforce.datacontrol;

/**
 * Created by mohoque on 20/07/2017.
 */

import java.util.LinkedList;
import java.util.Queue;


/**
 * Singleton data structure for storing packet data in queue. Data is pushed into this queue from
 * VpnService as well as background worker that pull data from remote socket.
 * @author Borey Sao
 * Date: May 12, 2014
 */
public class FlowAppQueue {
    private static Object syncData = new Object();
    private static Object syncObj = new Object();
    private volatile static FlowAppQueue instance = null;
    private Queue<String> data;
    public static FlowAppQueue getInstance(){
        if(instance == null){
            synchronized(syncObj){
                if(instance == null){
                    instance = new FlowAppQueue();
                }
            }
        }
        return instance;
    }
    private FlowAppQueue(){
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
}//end class
