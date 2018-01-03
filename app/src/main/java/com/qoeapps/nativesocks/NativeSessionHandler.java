/*
 *  Copyright 2014 AT&T
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.qoeapps.nativesocks;

import android.util.Log;

import com.qoeapps.qoenforce.datacontrol.FlowMetaData;
import com.qoeapps.qoenforce.datacontrol.FlowQoeMetadata;
import com.qoeapps.qoenforce.datacontrol.InternalMessages;
import com.qoeapps.qoenforce.datacontrol.SensorQueue;
import com.qoeapps.qoenforce.datacontrol.TransportFlowCache;
import com.qoeapps.qoenforce.datacontrol.VpnFlowCache;
import com.qoeapps.qoenforce.protocols.IClientPacketWriter;
import com.qoeapps.qoenforce.protocols.Packet;
import com.qoeapps.qoenforce.protocols.Session;
import com.qoeapps.qoenforce.protocols.SessionManager;
import com.qoeapps.qoenforce.protocols.ip.IPPacketFactory;
import com.qoeapps.qoenforce.protocols.ip.IPv4Header;
import com.qoeapps.qoenforce.protocols.tcp.PacketHeaderException;
import com.qoeapps.qoenforce.protocols.tcp.TCPHeader;
import com.qoeapps.qoenforce.protocols.tcp.TCPPacketFactory;
import com.qoeapps.qoenforce.protocols.udp.UDPHeader;
import com.qoeapps.qoenforce.protocols.udp.UDPPacketFactory;
import com.qoeapps.qoenforce.protocols.util.PacketUtil;

import java.io.IOException;
import java.util.Date;

import static com.qoeapps.qoenforce.datacontrol.InternalMessages.SensorContext;

//import com.metamine.protocols.socket.SocketData;

/**
 * handle VPN client request and response. it create a new session for each VPN client.
 * @author Borey Sao
 * Date: May 22, 2014
 */
public class NativeSessionHandler {
	public static final String TAG = "Session Handler";

	private static Object synObject = new Object();
	private static volatile NativeSessionHandler handler = null;
	private SessionManager sdata;
	private IClientPacketWriter writer;
	private TCPPacketFactory factory;
	private UDPPacketFactory udpfactory;
	private FlowQoeMetadata pdata;
	//private SocketData packetdata = null;
	private String[] whitelist;


    /**
     * In this section, we can create an independent thread.
     * The thread will create a native socket.
     * Protect the native socket.
     * Write data to the socket. From VPN to the client. Each client maintain a queue.
     * Read the socket
     * Construct Packet
     * Write the data to the VPN socket
     *
     *
     *
     * */
	private String CLASS_NAME = NativeSessionHandler.this.getClass().getSimpleName();
	public static NativeSessionHandler getInstance() throws IOException{
		if(handler == null){
			synchronized (synObject){
				if(handler == null){
					handler = new NativeSessionHandler();
				}
			}
		}
		return handler;
	}
	private NativeSessionHandler() throws IOException{
		sdata = SessionManager.getInstance();
		factory = new TCPPacketFactory();
		udpfactory = new UDPPacketFactory();
		pdata = FlowQoeMetadata.getInstance();
		
		//TODO: remove this after debugging
		//whitelist = new String[] {"74.125.129.102","208.109.186.6","206.188.33.238",
		//		"216.186.48.6","66.225.14.170"};
	}
	public void setWriter(IClientPacketWriter writer){
		this.writer = writer;
	}
	private void handleUDPPacket(byte[] clientpacketdata, IPv4Header ipheader, UDPHeader udpheader){

        String flowKey = PacketUtil.intToIPAddress(ipheader.getSourceIP())+":"+udpheader.getSourcePort()
                +"::"+PacketUtil.intToIPAddress(ipheader.getDestinationIP())+":"+udpheader.getDestinationPort()+"::"+"udp";


		// if the session hedaer does not
		Session session = sdata.getSession(ipheader.getDestinationIP(), udpheader.getDestinationPort(),
				ipheader.getSourceIP(), udpheader.getSourcePort(),"udp");
		if(session == null){
			session = sdata.createNewUDPSession(ipheader.getDestinationIP(), udpheader.getDestinationPort(), 
					ipheader.getSourceIP(), udpheader.getSourcePort(),"udp", ipheader.getDscpOrTypeOfService());
		}
		if(session == null){
			return;
		}else{



		}


        if(session.getOwnerApplicationName().length()==0) {
            String value = TransportFlowCache.getCacheIntance().get(flowKey);

            String sesKey = session.getSessionKey();
            if(value!=null) {
				String appName = value.split(":")[1];
                FlowMetaData flow = VpnFlowCache.getQueueInstance().get(sesKey);
                if (flow != null) {
                    flow.updateFlowMetaData(InternalMessages.appName, appName.toString());
                    VpnFlowCache.getQueueInstance().push(sesKey, flow);
                    session.setOwnerApplicationName(appName);
                    //Log.d(CLASS_NAME,"UDP Flow Owner "+ flow.toJSON());
                }else{
					//updateUDPFlows(udpLocation,flowKey);
					//updateUDPFlows(udpLocation);
				}

            }else{
                //Log.d(CLASS_NAME, "Owner of UDP flow not found "+flowKey);
            }


        }

        session.setLastIPheader(ipheader);
		session.setLastUDPheader(udpheader);
		int len = sdata.addClientUDPData(ipheader, udpheader, clientpacketdata, session);
		session.setDataForSendingReady(true);
		//Log.d(CLASS_NAME,"added UDP data for bg worker to send: "+len);
		sdata.keepSessionAlive(session);
	}
	private void handleTCPPacket(byte[] clientpacketdata, IPv4Header ipheader, TCPHeader tcpheader){
		int length = clientpacketdata.length;
        int datalength = length - ipheader.getIPHeaderLength() - tcpheader.getTCPHeaderLength();

        String sesKey = PacketUtil.intToIPAddress(ipheader.getSourceIP())+":"+tcpheader.getSourcePort()
                +":"+PacketUtil.intToIPAddress(ipheader.getDestinationIP())+":"+tcpheader.getDestinationPort()+":"+"tcp";

		String flowMeta = System.currentTimeMillis()+":"+sesKey+ ":"+length;


		if(tcpheader.isSYN()){

			//updateTCPFlows(tcpLocation);
        	replySynAck(ipheader,tcpheader);

        }else if(tcpheader.isACK()){
        	Session session = sdata.getSession(ipheader.getDestinationIP(), tcpheader.getDestinationPort(),
    				ipheader.getSourceIP(), tcpheader.getSourcePort(),"tcp");
        	if(session == null){
        		if(!tcpheader.isRST() && !tcpheader.isFIN()){
        			this.sendRstPacket(ipheader, tcpheader, datalength);
        		}
    			return;
        	}
            // TODO: Hoque update the application Name for the TCP flows


            if(session.getOwnerApplicationName().length()==0) {
                String value = TransportFlowCache.getCacheIntance().get(sesKey);
                String flowKey = session.getSessionKey();
                if(value!=null) {
					String appName = value.split(":")[1];
                    FlowMetaData flow = VpnFlowCache.getQueueInstance().get(flowKey);
                    if (flow != null) {
                        flow.updateFlowMetaData(InternalMessages.appName, appName.toString());
                        VpnFlowCache.getQueueInstance().push(flowKey, flow);
                        session.setOwnerApplicationName(appName);
                        //Log.d(CLASS_NAME,"TCP Flow Owner "+ flow.toJSON());
                    }else{

						//updateTCPFlows(tcpLocation);
					}

                }else{
                    //Log.d(CLASS_NAME, "Owner of TCP flow not found "+sesKey);
                }

            }
            //any data from client?
        	if(datalength > 0){
        		//accumulate data from client
        		int totalAdded = sdata.addClientData(ipheader, tcpheader, clientpacketdata);
        		if(totalAdded > 0){
	        		byte[] clientdata = new byte[totalAdded];
	        		int offset = ipheader.getIPHeaderLength() + tcpheader.getTCPHeaderLength();
	        		System.arraycopy(clientpacketdata, offset, clientdata, 0, totalAdded);
	    			//send ack to client only if new data was added
        			sendAck(ipheader,tcpheader,totalAdded, session);
        		}
        	}else{
        		//an ack from client for previously sent data
        		acceptAck(ipheader,tcpheader, session);
        		
        		if(session.isClosingConnection()){
        			sendFinAck(ipheader, tcpheader, session);
        		}else if(session.isAckedToFin() && !tcpheader.isFIN()){
        			//the last ACK from client after FIN-ACK flag was sent
        			sdata.closeSession(ipheader.getDestinationIP(), tcpheader.getDestinationPort(), 
        					ipheader.getSourceIP(), tcpheader.getSourcePort(),"tcp");
        			//Log.d(CLASS_NAME,"got last ACK after FIN, session is now closed.");
        		}
        	}
        	//received the last segment of data from vpn client
        	if(tcpheader.isPSH()){
        		//push data to destination here. Background thread will receive data and fill session's buffer.
        		//Background thread will send packet to client
        		pushDataToDestination(session, ipheader, tcpheader);
        	}else if(tcpheader.isFIN()){
        		ackFinAck(ipheader,tcpheader,session);
        	}else if(tcpheader.isRST()){
        		resetConnection(ipheader, tcpheader);
        	}
        	if(session != null && !session.isClientWindowFull() && !session.isAbortingConnection()){
        		sdata.keepSessionAlive(session);
        	}
        }else if(tcpheader.isFIN()){
        	Session session = sdata.getSession(ipheader.getDestinationIP(), tcpheader.getDestinationPort(),
    				ipheader.getSourceIP(), tcpheader.getSourcePort(),"tcp");
        	if(session == null){
        		ackFinAck(ipheader,tcpheader,session);
        	}else{
        		sdata.keepSessionAlive(session);
        	}
        }else if(tcpheader.isRST()){

        	resetConnection(ipheader, tcpheader);
        }else{
        }
	}
	/**
	 * handle each packet from each vpn client
	 * @param data
	 * @param length
	 * @throws PacketHeaderException
	 */
	public void handlePacket(byte[] data, int length) throws PacketHeaderException{
		byte[] clientpacketdata = new byte[length];
		System.arraycopy(data, 0, clientpacketdata, 0, length);
		//TODO: Store packet information here
		//packetdata.addData(clientpacketdata);

		IPv4Header ipheader = IPPacketFactory.createIPv4Header(clientpacketdata, 0);
		if(ipheader.getIpVersion() != 4 || (ipheader.getProtocol() != 6 && ipheader.getProtocol() != 17)){
			return;
		}

		UDPHeader udpheader = null;
		TCPHeader tcpheader = null;

		if(ipheader.getProtocol() == 6){
			tcpheader = factory.createTCPHeader(clientpacketdata, ipheader.getIPHeaderLength());

		}else{
			udpheader = udpfactory.createUDPHeader(clientpacketdata, ipheader.getIPHeaderLength());
		}
		
        if(tcpheader != null){
        	handleTCPPacket(clientpacketdata, ipheader, tcpheader);
			String sesKey = PacketUtil.intToIPAddress(ipheader.getSourceIP())+":"+tcpheader.getSourcePort()
					+":"+PacketUtil.intToIPAddress(ipheader.getDestinationIP())+":"+tcpheader.getDestinationPort()+":"+"tcp"+":"+length;

			String finalString = System.currentTimeMillis()+":"+sesKey+":"+SensorQueue.getQueueInstance().get(SensorContext);
			pdata.addData(finalString);

		}else if(udpheader != null) {
			handleUDPPacket(clientpacketdata, ipheader, udpheader);
			String sesKey = PacketUtil.intToIPAddress(ipheader.getSourceIP()) + ":" + udpheader.getSourcePort()
					+ ":" + PacketUtil.intToIPAddress(ipheader.getDestinationIP()) + ":" + udpheader.getDestinationPort() + ":" + "udp" + ":" + length;

			String finalString = System.currentTimeMillis() + ":" + sesKey + ":" + SensorQueue.getQueueInstance().get(SensorContext);
			pdata.addData(finalString);
		}


	}






	boolean inWhitelist(String ips){
		boolean yes = false;
		for(String str : whitelist){
			if(str.equals(ips)){
				yes = true;
				break;
			}
		}
		return yes;
	}
	void sendRstPacket(IPv4Header ip, TCPHeader tcp, int datalength){
		byte[] data = factory.createRstData(ip, tcp, datalength);
		try {
			writer.write(data);
			//TODO: Hoque store packet information here
			//packetdata.addData(data);
			int pktSize = data.length;
			String sesKey = PacketUtil.intToIPAddress(ip.getSourceIP())+":"+tcp.getSourcePort()
					+":"+PacketUtil.intToIPAddress(ip.getDestinationIP())+":"+tcp.getDestinationPort()+":"+"tcp"+":"+pktSize;
			String finalString = System.currentTimeMillis()+":"+sesKey+":"+SensorQueue.getQueueInstance().get(SensorContext);
			pdata.addData(finalString);

			//Log.d(CLASS_NAME,"Sent RST Packet to client with dest => "+PacketUtil.intToIPAddress(ip.getDestinationIP())+":"+tcp.getDestinationPort());
		} catch (IOException e) {
			Log.e(CLASS_NAME,"failed to send RST packet: "+e.getMessage());
		}
	}


	void ackFinAck(IPv4Header ip, TCPHeader tcp, Session session){
		//TODO: check if client only sent FIN without ACK
		int ack = tcp.getSequenceNumber() + 1;
		int seq = tcp.getAckNumber();
		byte[] data = factory.createFinAckData(ip, tcp, ack, seq, true, true);
		try {
			writer.write(data);
			//TODO: Store metadata informaiton here
			int pktSize = data.length;
			String sesKey = PacketUtil.intToIPAddress(ip.getSourceIP())+":"+tcp.getSourcePort()
					+":"+PacketUtil.intToIPAddress(ip.getDestinationIP())+":"+tcp.getDestinationPort()+":"+"tcp"+":"+pktSize;
			String finalString = System.currentTimeMillis()+":"+sesKey+":"+SensorQueue.getQueueInstance().get(SensorContext);
			pdata.addData(finalString);

			//packetdata.addData(data);
			if(session != null){
				session.getSelectionkey().cancel();
				sdata.closeSession(session);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	void sendFinAck(IPv4Header ip, TCPHeader tcp, Session session){
		int ack = tcp.getSequenceNumber();
		int seq = tcp.getAckNumber();
		byte[] data = factory.createFinAckData(ip, tcp, ack, seq,true,false);
		try {
			writer.write(data);

			int pktSize = data.length;
			String sesKey = PacketUtil.intToIPAddress(ip.getSourceIP())+":"+tcp.getSourcePort()
					+":"+PacketUtil.intToIPAddress(ip.getDestinationIP())+":"+tcp.getDestinationPort()+":"+"tcp"+":"+pktSize;
			String finalString = System.currentTimeMillis()+":"+sesKey+":"+SensorQueue.getQueueInstance().get(SensorContext);
			pdata.addData(finalString);

			//packetdata.addData(packet.getBuffer());


		} catch (IOException e) {
			Log.e(CLASS_NAME,"Failed to send ACK packet: "+e.getMessage());
		}
		session.setSendNext(seq + 1);
		session.setClosingConnection(false);
	}
	void pushDataToDestination(Session session, IPv4Header ip, TCPHeader tcp){
		
		session.setDataForSendingReady(true);
		session.setLastIPheader(ip);
		session.setLastTCPheader(tcp);
		session.setTimestampReplyto(tcp.getTimeStampSender());
		Date dt = new Date();
		int timestampSender = (int)dt.getTime();
		session.setTimestampSender(timestampSender);
	}
	
	/**
	 * send acknowledgment packet to VPN client
	 * @param ipheader
	 * @param tcpheader
	 * @param acceptedDataLength
	 * @param session
	 */
	void sendAck(IPv4Header ipheader, TCPHeader tcpheader, int acceptedDataLength, Session session){
		int acknumber = session.getRecSequence() + acceptedDataLength;
		//Log.d(CLASS_NAME,"sent ack, ack# "+session.getRecSequence()+" + "+acceptedDataLength+" = "+acknumber);
		session.setRecSequence(acknumber);
		byte[] data = factory.createResponseAckData(ipheader, tcpheader, acknumber);
		try {
			writer.write(data);
			//TODO: Hoque store packet information here
			//packetdata.addData(packet.getBuffer());
			int pktSize = data.length;

			String sesKey = PacketUtil.intToIPAddress(ipheader.getSourceIP())+":"+tcpheader.getSourcePort()
					+":"+PacketUtil.intToIPAddress(ipheader.getDestinationIP())+":"+tcpheader.getDestinationPort()+":"+"tcp"+":"+pktSize;

			String finalString = System.currentTimeMillis()+":"+sesKey+":"+SensorQueue.getQueueInstance().get(SensorContext);
			pdata.addData(finalString);

		} catch (IOException e) {
			Log.e(CLASS_NAME,"Failed to send ACK packet: "+e.getMessage());
		}
	}
	/**
	 * acknowledge a packet and adjust the receiving window to avoid congestion.
	 * @param ipheader
	 * @param tcpheader
	 * @param session
	 */
	void acceptAck(IPv4Header ipheader, TCPHeader tcpheader, Session session){
		boolean iscorrupted = PacketUtil.isPacketCorrupted(tcpheader);
		session.setPacketCorrupted(iscorrupted);
		if(iscorrupted){
			Log.e(CLASS_NAME,"prev packet was corrupted, last ack# "+tcpheader.getAckNumber());
		}
		if((tcpheader.getAckNumber() > session.getSendUnack()) || (tcpheader.getAckNumber() == session.getSendNext())){
			session.setAcked(true);
			//Log.d(TAG,"Accepted ack from client, ack# "+tcpheader.getAckNumber());
			
			if(tcpheader.getWindowSize() > 0){
				session.setSendWindowSizeAndScale(tcpheader.getWindowSize(), session.getSendWindowScale());
			}
			int byteReceived = tcpheader.getAckNumber() - session.getSendUnack();
			if(byteReceived > 0){
				session.decreaseAmountSentSinceLastAck(byteReceived);
			}
			if(session.isClientWindowFull()){
			}
			session.setSendUnack(tcpheader.getAckNumber());
			session.setRecSequence(tcpheader.getSequenceNumber());
			session.setTimestampReplyto(tcpheader.getTimeStampSender());
			Date dt = new Date();
			int timestampSender = (int)dt.getTime();
			session.setTimestampSender(timestampSender);
		}else{
			session.setAcked(false);
		}
	}
	/**
	 * set connection as aborting so that background worker will close it.
	 * @param ip
	 * @param tcp
     * TODO: Hoque update this with transport protocol
	 */
	void resetConnection(IPv4Header ip, TCPHeader tcp){
		Session sess = sdata.getSession(ip.getDestinationIP(), tcp.getDestinationPort(), ip.getSourceIP(), tcp.getSourcePort(),"tcp");
		if(sess != null){
			sess.setAbortingConnection(true);
		}
	}

	/**
	 * create a new client's session and SYN-ACK packet data to respond to client
	 * @param ip
	 * @param tcp
	 */
	void replySynAck(IPv4Header ip, TCPHeader tcp){

		ip.setIdenfication(0);
		Packet packet = factory.createSynAckPacketData(ip, tcp);
		
		TCPHeader tcpheader = packet.getTcpheader();
		
		Session session = sdata.createNewSession(ip.getDestinationIP(), tcp.getDestinationPort(), 
													ip.getSourceIP(), tcp.getSourcePort(),"tcp",ip.getDscpOrTypeOfService());
		if(session == null){
			return;
		}
		
    	int windowScaleFactor = (int) Math.pow(2,tcpheader.getWindowScale());
    	//Log.d(TAG,"window scale: Math.power(2,"+tcpheader.getWindowScale()+") is "+windowScaleFactor);
    	session.setSendWindowSizeAndScale(tcpheader.getWindowSize(), windowScaleFactor);
    	//Log.d(CLASS_NAME,"send-window size: "+session.getSendWindow());
    	session.setMaxSegmentSize(tcpheader.getMaxSegmentSize());
    	session.setSendUnack(tcpheader.getSequenceNumber());
    	session.setSendNext(tcpheader.getSequenceNumber() + 1);

        //TODO:Hoque, set the transport protocol here
        session.setTransport("tcp");



        //client initial sequence has been incremented by 1 and set to ack
    	session.setRecSequence(tcpheader.getAckNumber());
    	
    	try {
			writer.write(packet.getBuffer());
			//TODO: Hoque add traffic flow information
			int pktSize = ip.getIPHeaderLength()+ tcp.getTCPHeaderLength()+ 0;
			String sesKey = PacketUtil.intToIPAddress(ip.getSourceIP())+":"+tcp.getSourcePort()
					+":"+PacketUtil.intToIPAddress(ip.getDestinationIP())+":"+tcp.getDestinationPort()+":"+"tcp"+":"+pktSize;
			String finalString = System.currentTimeMillis()+":"+sesKey+":"+SensorQueue.getQueueInstance().get(SensorContext);
			pdata.addData(finalString);

			//packetdata.addData(packet.getBuffer());

		} catch (IOException e) {
			Log.e(CLASS_NAME,"Error sending data to client: "+e.getMessage());
		}
    	
	}
	
	
}//end class
