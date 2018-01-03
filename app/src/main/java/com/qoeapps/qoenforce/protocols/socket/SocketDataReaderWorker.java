package com.qoeapps.qoenforce.protocols.socket;

import android.util.Log;

import com.qoeapps.qoenforce.datacontrol.CandidateFlowQueue;
import com.qoeapps.qoenforce.datacontrol.FlowQoeMetadata;
import com.qoeapps.qoenforce.datacontrol.SensorQueue;
import com.qoeapps.qoenforce.protocols.IClientPacketWriter;
import com.qoeapps.qoenforce.protocols.Session;
import com.qoeapps.qoenforce.protocols.SessionManager;
import com.qoeapps.qoenforce.protocols.ip.IPv4Header;
import com.qoeapps.qoenforce.protocols.tcp.TCPHeader;
import com.qoeapps.qoenforce.protocols.tcp.TCPPacketFactory;
import com.qoeapps.qoenforce.protocols.udp.UDPPacketFactory;
import com.qoeapps.qoenforce.protocols.util.PacketUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;

import static com.qoeapps.qoenforce.datacontrol.InternalMessages.SensorContext;

/**
 * background task for reading data from remote server and write data to vpn client
 * @author Borey Sao
 * Date: July 30, 2014
 */
public class SocketDataReaderWorker implements Runnable {
	public static final String TAG = "AROCollector";
	private String CLASS_NAME = SocketDataReaderWorker.this.getClass().getSimpleName();
	private IClientPacketWriter writer;
	private TCPPacketFactory factory;
	private UDPPacketFactory udpfactory;
	private SessionManager sessionmg;
	private String sessionKey = "";
	private FlowQoeMetadata pdata = FlowQoeMetadata.getInstance();
	//private SocketData pdata;
	public SocketDataReaderWorker(){
		sessionmg = SessionManager.getInstance();
	//	pdata = SocketData.getInstance();
	}
	public SocketDataReaderWorker(TCPPacketFactory tcpfactory, UDPPacketFactory udpfactory, IClientPacketWriter writer){
		sessionmg = SessionManager.getInstance();
	//	pdata = SocketData.getInstance();
		this.factory = tcpfactory;
		this.udpfactory = udpfactory;
		this.writer = writer;
	}
	@Override
	public void run() {
		Session sess = sessionmg.getSessionByKey(sessionKey);
		if(sess == null){
			return;
		}
		if(sess.getSocketchannel() != null){
			try{
				readTCP(sess);
			}catch(Exception ex){

				//Log.e(TAG,"error processRead: "+ex.getMessage());
				// Hoque, this may create loop
				// this also runns in busy loop
				//sess.getSelectionkey().cancel();

			}
		}else if(sess.getUdpchannel() != null){
			readUDP(sess);
		}
		if(sess != null){
			
			if(sess.isAbortingConnection()){
				Log.d(CLASS_NAME,"removing aborted connection -> "+
						PacketUtil.intToIPAddress(sess.getDestAddress())+":"+sess.getDestPort()
						+"::"+PacketUtil.intToIPAddress(sess.getSourceIp())+":"+sess.getSourcePort()+"::"+sess.getTransport());
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
			}else{
				sess.setBusyread(false);
			}
		}
	}
	
	void readTCP(Session sess){
		SocketChannel channel = sess.getSocketchannel();
		ByteBuffer buffer = ByteBuffer.allocate(DataConst.MAX_RECEIVE_BUFFER_SIZE);
		int len = 0;
		String name = PacketUtil.intToIPAddress(sess.getDestAddress())+":"+sess.getDestPort()+"::"+
				PacketUtil.intToIPAddress(sess.getSourceIp())+":"+sess.getSourcePort()+"::"+sess.getTransport();
		try {
			
			do{
				if(sess.isAbortingConnection()){
					//return;//break;
					break;
				}

                //TODO: Here we get the data received from the remote server. Total bytes counter should be here.
				if(!sess.isClientWindowFull()){
					len = channel.read(buffer);
					if(len > 0){//-1 mean it reach the end of stream

						// TODO  : Hoque Update total bytes received from the client.
                        sess.setFlowReceivedBytes(len);
						sendToRequester(buffer,channel, len, sess);
						buffer.clear();
						long sessionDur = System.currentTimeMillis() - sess.getFlowBeginTime();
						//Log.d(CLASS_NAME,sess.getOwnerApplicationName()+" "+sess.getFlowReceivedBytes());
						if((sessionDur>=5000)&&(sess.getFlowReceivedBytes()>=20000)&&(!sess.isCandidateFlow())){

                            //Log.d(CLASS_NAME,sess.getOwnerApplicationName()+" "+sess.getFlowReceivedBytes()+" "+"exceeded threshold");
							sess.setCandidateFlow(true);
                            if(sess.getOwnerApplicationName().length()!=0) {
                                String sessDetail = sess.getFlowBeginTime()+":"+sess.getOwnerApplicationName()+":"+sess.getFlowReceivedBytes()+":"+sess.getFLowUplinkBytes();
                                CandidateFlowQueue.getQueueInstance().push(sess.getSessionKey(), sessDetail);

                            }
							//Log.d(CLASS_NAME,sess.getOwnerApplicationName()+" "+sess.getFlowReceivedBytes()+" "+"exceeded threshold");
						}
					}else if(len == -1){

                        sendFin(sess);
						sess.setAbortingConnection(true);
					}
				}else{

					Log.e(CLASS_NAME,"*** client window is full, now pause for "+name);
					break;
				}
			}while(len > 0);
		}catch(NotYetConnectedException ex2){
			Log.e(CLASS_NAME,"socket not connected");
		}catch(ClosedByInterruptException cex){
			Log.e(CLASS_NAME,"ClosedByInterruptException reading socketchannel: "+cex.getMessage());
			//sess.setAbortingConnection(true);
		}catch(ClosedChannelException clex){
			Log.e(CLASS_NAME,"ClosedChannelException reading socketchannel: "+clex.getMessage());
			//sess.setAbortingConnection(true);
		} catch (IOException e) {
			Log.e(CLASS_NAME,"Error reading data from socketchannel: "+e.getMessage());
			sess.setAbortingConnection(true);
		}
	}
	
	void sendToRequester(ByteBuffer buffer, SocketChannel channel, int datasize, Session sess){
		
		if(sess == null){
			Log.e(CLASS_NAME,"Session not found for dest. server: "+channel.socket().getInetAddress().getHostAddress());
			return;
		}
		
		//last piece of data is usually smaller than MAX_RECEIVE_BUFFER_SIZE
		if(datasize < DataConst.MAX_RECEIVE_BUFFER_SIZE){
			sess.setHasReceivedLastSegment(true);
		}else{
			sess.setHasReceivedLastSegment(false);
			
		}
		buffer.limit(datasize);
		buffer.flip();
		byte[] data = new byte[datasize];
		System.arraycopy(buffer.array(), 0, data, 0, datasize);
		sess.addReceivedData(data);
		//Log.d(TAG,"DataSerice added "+data.length+" to session. session.getReceivedDataSize(): "+session.getReceivedDataSize());
		//pushing all data to vpn client
		while(sess.hasReceivedData()){
			pushDataToClient(sess);
		}
	}
	/**
	 * create packet data and send it to VPN client
	 * @param session
	 * @return
	 */
	boolean pushDataToClient(Session session){
		if(!session.hasReceivedData()){
			//no data to send
			Log.d(CLASS_NAME,"no data for vpn client");
			return false;
		}
		
		IPv4Header ipheader = session.getLastIPheader();
		TCPHeader tcpheader = session.getLastTCPheader();
		int max = session.getMaxSegmentSize() - 60;
		
		if(max < 1){
			// Hoque change the segment size
			//max = 1024;
			max = 1440;
		}
		byte[] packetbody = session.getReceivedData(max);
		if(packetbody != null && packetbody.length > 0){
			int unack = session.getSendNext();
			int nextUnack = session.getSendNext() + packetbody.length;
			//Log.d(TAG,"sending vpn client body len: "+packetbody.length+", current seq: "+unack+", next seq: "+nextUnack);
			session.setSendNext(nextUnack);
			//we need this data later on for retransmission
			session.setUnackData(packetbody);
			session.setResendPacketCounter(0);
			
			byte[] data = factory.createResponsePacketData(ipheader, tcpheader, packetbody, session.hasReceivedLastSegment(), 
					session.getRecSequence(), unack, session.getTimestampSender(), session.getTimestampReplyto());
			try {
				writer.write(data);

				// TODO:Hoque the following line is for the packet capture
				//pdata.addData(data);

				String sesKey = PacketUtil.intToIPAddress(ipheader.getSourceIP())+":"+tcpheader.getSourcePort()
						+":"+PacketUtil.intToIPAddress(ipheader.getDestinationIP())+":"+tcpheader.getDestinationPort()+":"+"tcp"+":"+packetbody.length;

				String finalString = sesKey+":"+ SensorQueue.getQueueInstance().get(SensorContext);
				pdata.addData(finalString);




			} catch (IOException e) {
				Log.e(CLASS_NAME,"Failed to send ACK+Data packet: "+e.getMessage());
				return false;
			}
			return true;
		}
		return false;
	}
	private void sendFin(Session session){
		IPv4Header ipheader = session.getLastIPheader();
		TCPHeader tcpheader = session.getLastTCPheader();
		byte[] data = factory.createFinData(ipheader, tcpheader, session.getSendNext(), session.getRecSequence(), session.getTimestampSender(), session.getTimestampReplyto());
		try {
			writer.write(data);

			// TODO: Hoque: The following line is to

			//pdata.addData(data);

			String sesKey = PacketUtil.intToIPAddress(ipheader.getSourceIP())+":"+tcpheader.getSourcePort()
					+":"+PacketUtil.intToIPAddress(ipheader.getDestinationIP())+":"+tcpheader.getDestinationPort()+":"+"tcp"+":"+data.length;

			String finalString = System.currentTimeMillis()+":"+sesKey+":"+SensorQueue.getQueueInstance().get(SensorContext);
			pdata.addData(finalString);


			/* for debugging purpose 
			Log.d(TAG,"========> BG: FIN packet data to vpn client++++++++");
			IPv4Header vpnip = null;
			try {
				vpnip = factory.createIPv4Header(data, 0);
			} catch (PacketHeaderException e) {
				e.printStackTrace();
			}
			TCPHeader vpntcp = null;
			try {
				vpntcp = factory.createTCPHeader(data, vpnip.getIPHeaderLength());
			} catch (PacketHeaderException e) {
				e.printStackTrace();
			}
			if(vpnip != null && vpntcp != null){
				String sout = PacketUtil.getOutput(vpnip, vpntcp, data);
				Log.d(TAG,sout);
			}
			
			Log.d(TAG,"=======> BG: finished sending FIN packet to vpn client ========");
			*/
			
		} catch (IOException e) {
			Log.e(CLASS_NAME,"Failed to send FIN packet: "+e.getMessage());
			
		}
	}
	private void readUDP(Session sess){
		DatagramChannel channel = sess.getUdpchannel();
		ByteBuffer buffer = ByteBuffer.allocate(DataConst.MAX_RECEIVE_BUFFER_SIZE);
		String name = PacketUtil.intToIPAddress(sess.getDestAddress())+":"+sess.getDestPort()+":"+
				PacketUtil.intToIPAddress(sess.getSourceIp())+":"+sess.getSourcePort()+"::"+sess.getTransport();
		int len = 0;
		try {
			do{
				if(sess.isAbortingConnection()){
                    //Log.d("Hqoue","udp session is aborting "+sess.getSessionKey());
                    sess.setFlowEndTime(System.currentTimeMillis());
					break;
				}
				len = channel.read(buffer);
				if(len > 0){
					/*
					FlowMetaData flow = VpnFlowCache.getQueueInstance().get(flowKey);
                    if(flow!=null){
						flow.updateFlowMetaData(InternalMessages.bytesReceived,Integer.toString(len));

						String ownerApp = sess.getOwnerApplicationName();
						if(ownerApp!=null){
							sess.setOwnerApplicationName(ownerApp);
							flow.updateFlowMetaData(InternalMessages.appName,ownerApp);
                            VpnFlowCache.getQueueInstance().push(flowKey,flow);
						}

					}*/




					buffer.limit(len);
					buffer.flip();
					//create UDP packet
					byte[] data = new byte[len];
					System.arraycopy(buffer.array(),0, data, 0, len);
					byte[] packetdata = udpfactory.createResponsePacket(sess.getLastIPheader(), sess.getLastUDPheader(), data);
					//write to client
					writer.write(packetdata);

					//TODO: Hoque do not publish data to the queue
					String finalString = System.currentTimeMillis()+":"+sess.getSessionKey()+":"+packetdata.length+":"+SensorQueue.getQueueInstance().get(SensorContext);
					pdata.addData(finalString);

					buffer.clear();

					// TODO: Hoque update received bytes from the remote server and check the throughput to check for an audio/candidency.
					sess.setFlowReceivedBytes(len);

					//Log.d(CLASS_NAME,flowKey+" "+sess.getFlowReceivedBytes());
					Long sessionDur = System.currentTimeMillis()- sess.getFlowBeginTime();
					if((sessionDur>=5000)&&(sess.getFlowReceivedBytes()>=50000)){
                        //Log.d(CLASS_NAME,sess.getOwnerApplicationName()+" "+sess.getFlowReceivedBytes()+" "+"exceeded threshold");
						sess.setCandidateFlow(true);
                        if(sess.getOwnerApplicationName().length()!=0){
                            String sessDetail = sess.getFlowBeginTime()+":"+sess.getOwnerApplicationName()+":"+sess.getFlowReceivedBytes()+":"+sess.getFLowUplinkBytes();
                            CandidateFlowQueue.getQueueInstance().push(sess.getSessionKey(),sessDetail);
                        }
						//
					}

				}
			}while(len > 0);

		}catch(NotYetConnectedException ex){
			Log.e(CLASS_NAME,"failed to read from unconnected UDP socket");
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(CLASS_NAME,"Faild to read from UDP socket, aborting connection");
            sess.setAbortingConnection(true);
		}
	}
	public String getSessionKey() {
		return sessionKey;
	}
	public void setSessionKey(String sessionKey) {
		this.sessionKey = sessionKey;
	}
}
