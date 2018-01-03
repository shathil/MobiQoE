package com.qoeapps.qoenforce.protocols.socket;

import android.util.Log;

import com.qoeapps.qoenforce.datacontrol.FlowQoeMetadata;
import com.qoeapps.qoenforce.datacontrol.SensorQueue;
import com.qoeapps.qoenforce.protocols.IClientPacketWriter;
import com.qoeapps.qoenforce.protocols.Session;
import com.qoeapps.qoenforce.protocols.SessionManager;
import com.qoeapps.qoenforce.protocols.tcp.TCPPacketFactory;
import com.qoeapps.qoenforce.protocols.util.PacketUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;

import static com.qoeapps.qoenforce.datacontrol.InternalMessages.SensorContext;

public class SocketDataWriterWorker implements Runnable{
	public static final String TAG = "AROCollector";
	private String CLASS_NAME = SocketDataWriterWorker.this.getClass().getSimpleName();
	private IClientPacketWriter writer;
	private TCPPacketFactory factory;
	private SessionManager sessionmg;
	private String sessionKey = "";
	private FlowQoeMetadata pdata;
	public SocketDataWriterWorker(TCPPacketFactory tcpfactory, IClientPacketWriter writer){
		sessionmg = SessionManager.getInstance();
		pdata = FlowQoeMetadata.getInstance();
		this.factory = tcpfactory;
		this.writer = writer;
	}
	/*
	public String getSessionKey() {
		return sessionKey;
	}*/

	public void setSessionKey(String sessionKey) {
		this.sessionKey = sessionKey;
	}
	@Override
	public void run() {
		Session sess = sessionmg.getSessionByKey(sessionKey);
		if(sess == null){
			return;
		}
		sess.setBusywrite(true);
		if(sess.getSocketchannel() != null){
			writeTCP(sess);
		}else if(sess.getUdpchannel() != null){
			writeUDP(sess);
		}
		if(sess != null){
			sess.setBusywrite(false);
			if(sess.isAbortingConnection()){
				Log.d(CLASS_NAME,"Hoque removing aborted connection -> "+
						PacketUtil.intToIPAddress(sess.getDestAddress())+":"+sess.getDestPort()
						+"-"+PacketUtil.intToIPAddress(sess.getSourceIp())+":"+sess.getSourcePort()+"::"+sess.getTransport());
				sess.getSelectionkey().cancel();
				if(sess.getSocketchannel() != null && sess.getSocketchannel().isConnected()){
					try {
						sess.getSocketchannel().close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}else if(sess.getUdpchannel() != null && sess.getUdpchannel().isConnected()){
					try {
						sess.getUdpchannel().close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				sessionmg.closeSession(sess);
			}
		}
	}
	void writeUDP(Session sess){
		if(!sess.hasDataToSend()){
			return;
		}
		DatagramChannel channel = sess.getUdpchannel();
		String name = PacketUtil.intToIPAddress(sess.getDestAddress())+":"+sess.getDestPort()+
				"-"+PacketUtil.intToIPAddress(sess.getSourceIp())+":"+sess.getSourcePort();
		byte[] data = sess.getSendingData();
		ByteBuffer buffer = ByteBuffer.allocate(data.length);
		buffer.put(data);
		buffer.flip();
		try {
			channel.write(buffer);
			sess.setFLowUplinkBytes(data.length);
			//Log.d(CLASS_NAME,"UPlink bytes "+data.length);
			sess.connectionStartTime = System.currentTimeMillis();  //dt.getTime();
		}catch(NotYetConnectedException ex2){
			sess.setAbortingConnection(true);
			Log.e(CLASS_NAME,"Error writing to unconnected-UDP server, will abort current connection: "+ex2.getMessage());
		} catch (IOException e) {
			sess.setAbortingConnection(true);
			e.printStackTrace();
			Log.e(CLASS_NAME,"Error writing to UDP server, will abort connection: "+e.getMessage());
		}
	}
	
	void writeTCP(Session sess){
		SocketChannel channel = sess.getSocketchannel();


		byte[] data = sess.getSendingData();
		ByteBuffer buffer = ByteBuffer.allocate(data.length);
		buffer.put(data);
		buffer.flip();
		
		try {
			//Log.d(CLASS_NAME,"writing TCP data to: "+name);
			channel.write(buffer);
			sess.setFLowUplinkBytes(data.length);
			//Log.d(CLASS_NAME,"UPlink TCP bytes "+data.length);

			//Log.d(TAG,"finished writing data to: "+name);
		}catch(NotYetConnectedException ex){
			Log.e(CLASS_NAME,"failed to write to unconnected socket: "+ex.getMessage());
		} catch (IOException e) {
			Log.e(CLASS_NAME,"Error writing to server: "+e.getMessage());
			
			//close connection with vpn client
			byte[] rstdata = factory.createRstData(sess.getLastIPheader(), sess.getLastTCPheader(), 0);
			try {
				writer.write(rstdata);

				//TODO: Here we need to add the Json objects and later we dump them.
				//pdata.addData(rstdata);
				String sesKey = PacketUtil.intToIPAddress(sess.getDestAddress())+":"+sess.getDestPort()+
						":"+PacketUtil.intToIPAddress(sess.getSourceIp())+":"+sess.getSourcePort()+":"+"tcp"+":"+data.length;


				String finalString = System.currentTimeMillis()+":"+sesKey+":"+ SensorQueue.getQueueInstance().get(SensorContext);
				pdata.addData(finalString);

			} catch (IOException e1) {
			}
			//remove session
			Log.e(CLASS_NAME,"failed to write to remote socket, aborting connection");
			sess.setAbortingConnection(true);
		}
		
	}

}
