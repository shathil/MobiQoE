package com.qoeapps.qoenforce.protocols.socket;


import android.util.Log;

import com.qoeapps.qoenforce.datacontrol.BroadcastMessageQueue;
import com.qoeapps.qoenforce.protocols.IClientPacketWriter;
import com.qoeapps.qoenforce.protocols.Session;
import com.qoeapps.qoenforce.protocols.SessionManager;
import com.qoeapps.qoenforce.protocols.tcp.TCPPacketFactory;
import com.qoeapps.qoenforce.protocols.udp.UDPPacketFactory;
import com.qoeapps.qoenforce.protocols.util.PacketUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.qoeapps.qoenforce.datacontrol.InternalMessages.DSCP_CODES;
import static com.qoeapps.qoenforce.datacontrol.InternalMessages.SLEEP_PERIOD;


public class SocketNIODataService implements Runnable {
	//public static final String TAG = "AROCollector";
	public static Object syncSelector = new Object();
	public static Object syncSelector2 = new Object();

	private String CLASS_NAME = SocketNIODataService.this.getClass().getSimpleName();

	SessionManager sessionmg;
	private IClientPacketWriter writer;
	private TCPPacketFactory factory;
	private UDPPacketFactory udpfactory;
	private volatile boolean shutdown = false;
	private Selector selector = null;
	//create thread pool for reading/writing data to socket
	private BlockingQueue<Runnable> taskqueue;
	private ThreadPoolExecutor workerpool;
	
	public SocketNIODataService(){
		factory = new TCPPacketFactory();
		udpfactory = new UDPPacketFactory();
		taskqueue = new LinkedBlockingQueue<Runnable>();
		workerpool = new ThreadPoolExecutor(8, 100, 10, TimeUnit.SECONDS, taskqueue);
	}
	public void setWriter(IClientPacketWriter writer){
		this.writer = writer;
	}
	@Override
	public void run() {
		Log.d(CLASS_NAME,"SocketDataService starting in background...");
		sessionmg = SessionManager.getInstance();
		selector = sessionmg.getSelector();
		runTask();
	}
	/**
	 * notify long running task to shutdown
	 * @param isshutdown
	 */
	public void setShutdown(boolean isshutdown){
		this.shutdown = isshutdown;
		this.sessionmg.getSelector().wakeup();
	}
	void runTask(){
		Log.d(CLASS_NAME, "Selector is running...");
		
		while(!shutdown){
			//Log.d("Hoque", "SocketNiodataService runtaskSocket selectionr ");

			try {
				Integer sleepInt = BroadcastMessageQueue.getQueueInstance().get(SLEEP_PERIOD);
				if(sleepInt!=null)
					Thread.sleep(sleepInt.intValue());
				else
					Thread.sleep(10);
			} catch (InterruptedException e1) {
			}
			try {
				synchronized(syncSelector){
					selector.select();
					//selector.notifyAll();
				}
			} catch (IOException e) {
				//Log.e(TAG,"Error in Selector.select(): "+e.getMessage());
				/* Hoque: this is creating busy loop
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}*/
				continue;
			}
			if(shutdown){
				break;
			}
			synchronized(syncSelector2){
				Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
				while(iter.hasNext()){
					SelectionKey key = (SelectionKey)iter.next();
					if(key.attachment() == null){
						try {
							processTCPSelectionKey(key);
						} catch (IOException e) {
							key.cancel();
						}
					}else{
						processUDPSelectionKey(key);
					}
					iter.remove();
					if(shutdown){
						break;
					}
				}
			}
		}
	}
	void processUDPSelectionKey(SelectionKey key){
		Integer dscp = BroadcastMessageQueue.getQueueInstance().get(DSCP_CODES);
		if(!key.isValid()){
			//Log.d(CLASS_NAME,"Invalid SelectionKey for UDP");
			return;
		}
		DatagramChannel channel = (DatagramChannel)key.channel();
		Session sess = sessionmg.getSessionByDatagramChannel(channel);
		if(sess == null){
			return;
		}
		
		if(!sess.isConnected() && key.isConnectable()){
			String ips = PacketUtil.intToIPAddress(sess.getDestAddress());
			int port = sess.getDestPort();
			SocketAddress addr = new InetSocketAddress(ips,port);
			try {
				//Log.d(CLASS_NAME,"selector: connecting to remote UDP server: "+ips+":"+port);
				try{
					channel = channel.connect(addr);
					channel.socket().setTrafficClass(dscp.intValue());

					sess.setUdpchannel(channel);
					sess.setConnected(channel.isConnected());

				}catch(ClosedChannelException ex){
					sess.setAbortingConnection(true);
				}catch(UnresolvedAddressException ex2){
					sess.setAbortingConnection(true);
				}catch(UnsupportedAddressTypeException ex3){
					sess.setAbortingConnection(true);
				}catch(SecurityException ex4){
					sess.setAbortingConnection(true);
				}
				
			}catch(ClosedChannelException ex1){
				//Log.e(CLASS_NAME,"failed to connect to closed udp: "+ex1.getMessage());
				sess.setAbortingConnection(true);
			} catch (IOException e) {
				//Log.e(CLASS_NAME,"failed to connect to udp: "+e.getMessage());
				e.printStackTrace();
				sess.setAbortingConnection(true);
			}
		}
		if(channel.isConnected()){
			processSelector(key, sess);
		}
	}
	void processTCPSelectionKey(SelectionKey key) throws IOException{
		//Integer dscp = BroadcastMessageQueue.getQueueInstance().get(DSCP_CODES);
		if(!key.isValid()){
			Log.d(CLASS_NAME,"Invalid SelectionKey for TCP");
			return;
		}
		SocketChannel channel = (SocketChannel)key.channel();
		Session sess = sessionmg.getSessionByChannel(channel);
		if(sess == null){
			return;
		}
		
		if(!sess.isConnected() && key.isConnectable()){
			String ips = PacketUtil.intToIPAddress(sess.getDestAddress());
			int port = sess.getDestPort();
			SocketAddress addr = new InetSocketAddress(ips,port);
			//Log.d(CLASS_NAME,"connecting to remote tcp server: "+ips+":"+port);
			boolean connected = false;
			if(!channel.isConnected() && !channel.isConnectionPending()){
				try{
					connected = channel.connect(addr);
					//channel.socket().setTrafficClass(dscp.intValue());

				}catch(ClosedChannelException ex){
					sess.setAbortingConnection(true);
				}catch(UnresolvedAddressException ex2){
					sess.setAbortingConnection(true);
				}catch(UnsupportedAddressTypeException ex3){
					sess.setAbortingConnection(true);
				}catch(SecurityException ex4){
					sess.setAbortingConnection(true);
				}catch(IOException ex5){
					sess.setAbortingConnection(true);
				}
			}
			
			if(connected){
				sess.setConnected(connected);
				//Log.d(CLASS_NAME,"connected immediately to remote tcp server: "+ips+":"+port);
			}else{
				if(channel.isConnectionPending()){
					connected = channel.finishConnect();
					sess.setConnected(connected);
					//Log.d(CLASS_NAME,"connected to remote tcp server: "+ips+":"+port);
				}
			}
		}
		if(channel.isConnected()){
			processSelector(key, sess);
		}
	}
	private void processSelector(SelectionKey key, Session sess){
		String sessionkey = sessionmg.createKey(sess.getDestAddress(), sess.getDestPort(), sess.getSourceIp(), sess.getSourcePort(),sess.getTransport());
		//tcp has PSH flag when data is ready for sending, UDP does not have this
		if(key.isValid() && key.isWritable() && !sess.isBusywrite()){
			if(sess.hasDataToSend() && sess.isDataForSendingReady()){
				sess.setBusywrite(true);
				//SocketDataWriterWorker worker = new SocketDataWriterWorker(factory, udpfactory, writer);
				SocketDataWriterWorker worker = new SocketDataWriterWorker(factory,writer);
				worker.setSessionKey(sessionkey);
				workerpool.execute(worker);
			}
		}
		if(key.isValid() && key.isReadable() && !sess.isBusyread()){
			sess.setBusyread(true);
			SocketDataReaderWorker worker = new SocketDataReaderWorker(factory, udpfactory, writer);
			worker.setSessionKey(sessionkey);
			workerpool.execute(worker);
		}
	}
	

}//end

