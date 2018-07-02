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

package com.qoeapps.qoenforce.protocols;

import android.util.Log;

import com.qoeapps.qoenforce.datacontrol.BroadcastMessageQueue;
import com.qoeapps.qoenforce.datacontrol.CandidateFlowQueue;
import com.qoeapps.qoenforce.datacontrol.FlowMetaData;
import com.qoeapps.qoenforce.datacontrol.InternalMessages;
import com.qoeapps.qoenforce.datacontrol.TransportFlowCache;
import com.qoeapps.qoenforce.datacontrol.UdpFlowCache;
import com.qoeapps.qoenforce.datacontrol.VpnFlowCache;
import com.qoeapps.qoenforce.protocols.ip.IPv4Header;
import com.qoeapps.qoenforce.protocols.socket.DataConst;
import com.qoeapps.qoenforce.protocols.socket.SocketNIODataService;
import com.qoeapps.qoenforce.protocols.socket.SocketProtector;
import com.qoeapps.qoenforce.protocols.tcp.TCPHeader;
import com.qoeapps.qoenforce.protocols.udp.UDPHeader;
import com.qoeapps.qoenforce.protocols.util.PacketUtil;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import static com.qoeapps.qoenforce.datacontrol.InternalMessages.DSCP_CODES;

/**
 * Manage in-memory storage for VPN client session.
 * @author Borey Sao
 * Date: May 20, 2014
 */
public class SessionManager {
	//public static final String TAG = "AROCollector";
	private String CLASS_NAME = SessionManager.this.getClass().getSimpleName();
	private static Object syncObj = new Object();
	private static volatile SessionManager instance = null;
	private Hashtable<String, Session> table = null;
	public static Object syncTable = new Object();
	private SocketProtector protector = null;
	Selector selector;
	private SessionManager(){
		table = new Hashtable<String,Session>(10);
		protector = SocketProtector.getInstance();
		try {
			selector = Selector.open();
		} catch (IOException e) {
			Log.e(CLASS_NAME,"Failed to create Socket Selector");
		}
	}
	public static SessionManager getInstance(){
		if(instance == null){
			synchronized(syncObj){
				if(instance == null){
					instance = new SessionManager();
				}
			}
		}
		return instance;
	}
	public Selector getSelector(){
		return selector;
	}
	/**
	 * keep java garbage collector from collecting a session
	 * @param sess
     * TODO:Hoque new session key
	 */
	public void keepSessionAlive(Session sess){
		if(sess != null){
            String    key = this.createKey(sess.getDestAddress(), sess.getDestPort(), sess.getSourceIp(), sess.getSourcePort(), sess.getTransport());
			synchronized(syncTable){
				table.put(key, sess);
			}
		}
	}

    // TODO: Hoque We can take flow metadata from here and use this table
	public Iterator<Session> getAllSession(){
		return table.values().iterator();
	}

    public Set<String> getAllSessionKeys(){
        return table.keySet();
    }



    public int addClientUDPData(IPv4Header ip, UDPHeader udp, byte[] buffer, Session session){
		int start = ip.getIPHeaderLength() + 8;
		int len = udp.getLength() - 8;//exclude header size
		if(len < 1){
			return 0;
		}
		if((buffer.length - start) < len){
			len = buffer.length - start;
		}
		byte[] data = new byte[len];
		System.arraycopy(buffer, start, data, 0, len);
		session.setSendingData(data);
		return len;
	}
	/**
	 * add data from client which will be sending to the destination server later one when receiving PSH flag.
	 * @param ip
	 * @param tcp
	 * @param buffer
     * TODO: Hoque, here transport protocol TCP is added for the key.
	 */
	public int addClientData(IPv4Header ip, TCPHeader tcp, byte[] buffer){
		Session session = getSession(ip.getDestinationIP(), tcp.getDestinationPort(), ip.getSourceIP(), tcp.getSourcePort(),"tcp");
		if(session == null){
			return 0;
		}
		int len = 0;
		//check for duplicate data
		if(session.getRecSequence() != 0 && tcp.getSequenceNumber() < session.getRecSequence()){
			return len;
		}
		int start = ip.getIPHeaderLength() + tcp.getTCPHeaderLength();
		len = buffer.length - start;
		byte[] data = new byte[len];
		System.arraycopy(buffer, start, data, 0, len);
		//appending data to buffer
		session.setSendingData(data);
		return len;
	}

    //TODO: Hoque, added the transport protocol for the key
	public boolean hasSession(int ipaddress, int port, int srcIp, int srcPort, String transport){
		String key = createKey(ipaddress, port, srcIp, srcPort,transport);
		return table.containsKey(key);
	}
	public Session getSession(int ipaddress, int port, int srcIp, int srcPort,String transport){
		String key = createKey(ipaddress, port, srcIp, srcPort, transport);
		Session session = null;
		synchronized(syncTable){
			if(table.containsKey(key)){
				session = table.get(key);
			}
		}
		return session;
	}
	public Session getSessionByKey(String key){
		Session session = null;
		synchronized(syncTable){
			if(table.containsKey(key)){
				session = table.get(key);
			}
		}
		return session;
	}
	public Session getSessionByDatagramChannel(DatagramChannel channel){
		Session session = null;
		synchronized(syncTable){
			Iterator<Session> it = table.values().iterator();
			while(it.hasNext()){
				Session sess = it.next();
				if(sess.getUdpchannel() == channel){
					session = sess;
					break;
				}
			}
		}
		return session;
	}
	public Session getSessionByChannel(SocketChannel channel){
		Session session = null;
		synchronized(syncTable){
			Iterator<Session> it = table.values().iterator();
			while(it.hasNext()){
				Session sess = it.next();
				if(sess.getSocketchannel() == channel){
					session = sess;
					break;
				}
			}
		}
		return session;
	}
	public void removeSessionByChannel(SocketChannel channel){
		String key = null;
		String tmp = null;
		Session session = null;
		synchronized(syncTable){
			Iterator<String> it = table.keySet().iterator();
			while(it.hasNext()){
				tmp = it.next();
				Session sess = table.get(tmp);
				if(sess != null && sess.getSocketchannel() == channel){
					key = tmp;
					session = sess;
					break;
				}
				
			}
		}
		if(key != null){
			synchronized(syncTable){
				table.remove(key);
			}
		}
		if(session != null){
			Log.d(CLASS_NAME,"closed session -> "+PacketUtil.intToIPAddress(session.getDestAddress())+":"+session.getDestPort()
					+"-"+PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort());
			session = null;
		}
	}
	/**
	 * remove session from memory, then close socket connection.
	 * @param ip
	 * @param port
	 * @param srcIp
	 * @param srcPort
     * TODO: Hoque, edited the key so that transport prootcol is also part of the key
	 */
	public void closeSession(int ip, int port, int srcIp, int srcPort, String transport){
		String keys = createKey(ip,port, srcIp, srcPort, transport);

        //sess.setFlowEndTime(System.currentTimeMillis());

        Session session = null; //getSession(ip, port, srcIp, srcPort);
		synchronized(syncTable){

			//Log.d(CLASS_NAME,"Transport "+transport);
			session = table.remove(keys);

			//Log.d(CLASS_NAME, "Remove Alias Ley "+session.getSessionKey());
			CandidateFlowQueue.getQueueInstance().pop(session.getSessionKey());
			if(transport.equals("tcp")){
				TransportFlowCache.getCacheIntance().pop(keys);
				//Log.d(CLASS_NAME, "Removed TCP flow "+keys+" remaninging ");
			}
			if(transport.equals("udp")){
				UdpFlowCache.getCacheIntance().pop(keys);
				//Log.d(CLASS_NAME, "Removed  UDP flow "+keys);

			}
        }

		if(session != null){
            session.setFlowEndTime(System.currentTimeMillis());
            String flowKey = session.getSessionKey();
            FlowMetaData flow = VpnFlowCache.getQueueInstance().get(flowKey);
            if(flow!=null){

                flow.updateFlowMetaData(InternalMessages.duration,Long.toString(session.getFlowDuration()));
                flow.updateFlowMetaData(InternalMessages.appName,session.getOwnerApplicationName());
				//Log.d(CLASS_NAME,"Received Bytes "+session.getFlowReceivedBytes());
				flow.updateFlowMetaData(InternalMessages.bytesReceived,Long.toString(session.getFlowReceivedBytes()));
				flow.updateFlowMetaData(InternalMessages.goodput,null);
				//Log.d(CLASS_NAME,"SessionEnds here"+flow.toJSON());
                VpnFlowCache.getQueueInstance().push(flowKey,flow);
			}

            try {
				SocketChannel chan = session.getSocketchannel();
				if(chan != null){
					chan.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			//Log.d(CLASS_NAME,"closed session -> "+PacketUtil.intToIPAddress(session.getDestAddress())+":"+session.getDestPort()
			//		+"-"+PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort());
			session = null;
		}
	}



	public void  closeSession(Session session){
		if(session == null){
			return;
		}
		String keys = createKey(session.getDestAddress(), session.getDestPort(), session.getSourceIp(), session.getSourcePort(), session.getTransport());


		synchronized(syncTable){
			table.remove(keys);


			//Log.d(CLASS_NAME, "Remove Alias Ley "+session.getSessionKey());
			CandidateFlowQueue.getQueueInstance().pop(session.getSessionKey());

			if(session.getTransport().equals("tcp")){
				TransportFlowCache.getCacheIntance().pop(keys);
				//Log.d(CLASS_NAME, "Removed TCP flow "+keys);

			}
			if(session.getTransport().equals("udp")){
				UdpFlowCache.getCacheIntance().pop(keys);
				//Log.d(CLASS_NAME, "Removed UDP flow "+keys);
			}

		}
		if(session != null){
			try {
                String flowKey = session.getSessionKey();
                Long endTime = System.currentTimeMillis();
                session.setFlowEndTime(endTime);
                Long startTime = session.getFlowBeginTime();
                //Log.d(CLASS_NAME," Flow Duration "+(endTime-startTime)+"ms");
                FlowMetaData flow = VpnFlowCache.getQueueInstance().get(flowKey);
                if(flow!=null){

                    flow.updateFlowMetaData(InternalMessages.duration,Long.toString(endTime-startTime));
					flow.updateFlowMetaData(InternalMessages.bytesReceived,Long.toString(session.getFlowReceivedBytes()));
                    flow.updateFlowMetaData(InternalMessages.goodput,null);
                    flow.updateFlowMetaData(InternalMessages.appName,session.getOwnerApplicationName());
                    //Log.d(CLASS_NAME,"SessionEnds here "+flow.toJSON());
                    VpnFlowCache.getQueueInstance().push(flowKey,flow);

                }
				/*
				if(session.getTransport().equals("tcp")){
					TransportFlowCache.getCacheIntance().pop(keys);
					Log.d(CLASS_NAME, "Removed flow "+keys);

				}
				if(session.getTransport().equals("udp")){
					UdpFlowCache.getCacheIntance().pop(keys);
					Log.d(CLASS_NAME, "Removed flow "+keys);
				}*/

				SocketChannel chan = session.getSocketchannel();
				if(chan != null){
					chan.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			//Log.d(CLASS_NAME,"closed session -> "+PacketUtil.intToIPAddress(session.getDestAddress())+":"+session.getDestPort()
			//		+"-"+PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort());
			session = null;
		}
	}
	public Session createNewUDPSession(int ip, int port, int srcIp, int srcPort, String transport, int tos){
		String keys = createKey(ip,port, srcIp, srcPort, transport);
		boolean found = false;
		synchronized(syncTable){
			found = table.containsKey(keys);
		}
		if(found){
			return null;
		}
		Session ses = new Session();
		//updateFlowOwners();
		DatagramChannel channel = null;

//		Integer dscp = BroadcastMessageQueue.getQueueInstance().get(DSCP_CODES);
//		Log.d(CLASS_NAME, "DSCP "+dscp.intValue());


		try {
			channel = DatagramChannel.open();
			channel.socket().setSoTimeout(0);
			channel.configureBlocking(false);
			//channel.socket().setTrafficClass(0x40);

			// one of the fundamentmental problem obseved with voice UDP traffic is the remote end does not hear voice properly. Probably
			// the timeout was set to 0. Probably setting higher value would do better performance.

		}catch(SocketException ex){
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		protector.protect(channel.socket());
		
		//initiate connection to redude latency
		String ips = PacketUtil.intToIPAddress(ip);
		String srcips = PacketUtil.intToIPAddress(srcIp);
		SocketAddress addr = new InetSocketAddress(ips,port);

		//Log.d(CLASS_NAME,"initialized connection to remote UDP server: "+ips+":"+port+" from "+srcips+":"+srcPort+" TOS "+tos);
		
		
		try{
			channel.connect(addr);
			//tos = channel.socket().getTrafficClass();
			//Log.d(CLASS_NAME, "DSCP tos"+tos);

			ses.setConnected(channel.isConnected());
			ses.setDestAddress(ip);
			ses.setDestPort(port);
			ses.setSourceIp(srcIp);
			ses.setSourcePort(srcPort);
			ses.setTransport(transport);
			ses.setConnected(false);


            //TODO: Hoque this is new session key for tracking the real session from VPNClient to remote address
            ses.setFlowBeginTime(System.currentTimeMillis());
            String localAddress = channel.socket().getLocalAddress().getHostAddress();
            int localPort = channel.socket().getLocalPort();
            String aliasKey = localAddress+":"+localPort+"::"+ips+":"+port+"::"+transport;
            ses.setSessionKey(aliasKey);

            // TODO Hoque: Add newly created flow to flow queue.
            if(VpnFlowCache.getQueueInstance().get(aliasKey)==null){
                FlowMetaData flow = new FlowMetaData();
                flow.updateFlowMetaData(InternalMessages.srcAddress,localAddress);
                flow.updateFlowMetaData(InternalMessages.srcPort,Integer.toString(localPort));
                flow.updateFlowMetaData(InternalMessages.dstAddress,ips);
                flow.updateFlowMetaData(InternalMessages.dstPort,Integer.toString(port));
                flow.updateFlowMetaData(InternalMessages.transport,transport);
                String appName = UdpFlowCache.getCacheIntance().get(keys);
                if(appName!=null) {
					flow.updateFlowMetaData(InternalMessages.appName, appName);
					ses.setOwnerApplicationName(appName);
				}
               // Log.d(CLASS_NAME,"UDP SessionCreated "+flow.toJSON());
                VpnFlowCache.getQueueInstance().push(aliasKey,flow);

            }

            Iterator<FlowMetaData> allFlows = VpnFlowCache.getQueueInstance().getAllFlows();


        }catch(ClosedChannelException ex){
		}catch(UnresolvedAddressException ex2){
		}catch(UnsupportedAddressTypeException ex3){
		}catch(SecurityException ex4){
		}catch(IOException ex5){
		}
		
				
		Object isudp = new Object();
		try {
			
			synchronized(SocketNIODataService.syncSelector2){
				selector.wakeup();
				synchronized(SocketNIODataService.syncSelector){
					SelectionKey selectkey = null;
					if(!channel.isConnected()){
						selectkey = channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE, isudp);
					}else{
						selectkey = channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, isudp);
					}
					ses.setSelectionkey(selectkey);
					//Log.d(CLASS_NAME,"Registered udp selector successfully");
				}
			}
		} catch (ClosedChannelException e1) {
			e1.printStackTrace();
			Log.e(CLASS_NAME,"failed to register udp channel with selector: "+e1.getMessage());
			return null;
		}
		
		ses.setUdpchannel(channel);
		
		synchronized(syncTable){
			if(!table.containsKey(keys)){
				table.put(keys, ses);
			}else{
				found = true;
			}
		}
		if(found){
			try {
				channel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if(ses != null){
            //Log.d(CLASS_NAME,"new UDP session successfully created.");
		}
		return ses;
	}
	public Session createNewSession(int ip, int port, int srcIp, int srcPort, String transport, int tos){
        // TODO: Hoque, Create new types of key including protocol and application
		Integer dscp = BroadcastMessageQueue.getQueueInstance().get(DSCP_CODES);
//		Log.d(CLASS_NAME, "DSCP "+dscp.intValue());
		String keys = createKey(ip,port, srcIp, srcPort, transport);
		boolean found = false;
		synchronized(syncTable){
			found = table.containsKey(keys);
		}
		if(found){
			return null;
		}



		Session ses = new Session();
		ses.setDestAddress(ip);
		ses.setDestPort(port);
		ses.setSourceIp(srcIp);
		ses.setSourcePort(srcPort);
        ses.setTransport(transport);
		ses.setConnected(false);
		
		SocketChannel channel = null;

        // TODO: Hoque, here Aro sets the initial receive window size to maximum at the very beginning connecting the remote server.
        // TODO: Hoque, we also need to check the window size initiated by the client. This is checked. The maximum window size is MAX_RECEIVE_BUFFER_SIZE

		try {
			channel = SocketChannel.open();

			//channel.socket().setTrafficClass(0x40);
			channel.configureBlocking(false);
			channel.socket().setKeepAlive(true);
			channel.socket().setTcpNoDelay(true);
			channel.socket().setSoTimeout(0);
			channel.socket().setReceiveBufferSize(DataConst.MAX_RECEIVE_BUFFER_SIZE);

			//tos = channel.socket().getTrafficClass();
		}catch(SocketException ex){
			return null;
		} catch (IOException e) {
			Log.e(CLASS_NAME,"Failed to create SocketChannel: "+e.getMessage());
			return null;
		}

 		String ips = PacketUtil.intToIPAddress(ip);
		//Log.d(CLASS_NAME,"created new socketchannel for "+ips+":"+port+"::"+PacketUtil.intToIPAddress(srcIp)+":"+srcPort+" DSCP "+tos);
		protector.protect(channel.socket());
		
		//Log.d(CLASS_NAME,"Protected new socketchannel");
		
		//initiate connection to redude latency
		SocketAddress addr = new InetSocketAddress(ips,port);
		//Log.d(CLASS_NAME,"initiate connecting to remote tcp server: "+ips+":"+port);
		boolean connected = false;
		try{
			connected = channel.connect(addr);

			ses.setConnected(connected);
			//channel.socket().setTrafficClass(0x40);
			//tos = channel.socket().getTrafficClass();
			//Log.d(CLASS_NAME, "DSCP tos"+tos);


			//TODO: Hoque this is new session key for tracking the real session from VPNClient to remote address
            ses.setFlowBeginTime(System.currentTimeMillis());
            String localAddress = channel.socket().getLocalAddress().getHostAddress();
            int localPort = channel.socket().getLocalPort();
            String aliasKey = localAddress+":"+localPort+"::"+ips+":"+port+"::"+transport;
            ses.setSessionKey(aliasKey);
            if(VpnFlowCache.getQueueInstance().get(aliasKey)==null){
                FlowMetaData flow = new FlowMetaData();
                flow.updateFlowMetaData(InternalMessages.srcAddress,localAddress);
                flow.updateFlowMetaData(InternalMessages.srcPort,Integer.toString(localPort));
                flow.updateFlowMetaData(InternalMessages.dstAddress,ips);
                flow.updateFlowMetaData(InternalMessages.dstPort,Integer.toString(port));
                flow.updateFlowMetaData(InternalMessages.transport,transport);
                String appName = TransportFlowCache.getCacheIntance().get(keys);
                if(appName!=null)
                    flow.updateFlowMetaData(InternalMessages.appName,appName);

                //Log.d(CLASS_NAME,"TCP SessionCreated "+flow.toJSON());
                VpnFlowCache.getQueueInstance().push(aliasKey,flow);
            }


        }catch(ClosedChannelException ex){
		}catch(UnresolvedAddressException ex2){
		}catch(UnsupportedAddressTypeException ex3){
		}catch(SecurityException ex4){
		}catch(IOException ex5){
		}
		


        //register for non-blocking operation
		try {
			synchronized(SocketNIODataService.syncSelector2){
				selector.wakeup();
				synchronized(SocketNIODataService.syncSelector){
					SelectionKey selectkey = channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
					ses.setSelectionkey(selectkey);
					//Log.d(CLASS_NAME,"Registered tcp selector successfully");
				}
			}
		} catch (ClosedChannelException e1) {
			e1.printStackTrace();
			Log.e(CLASS_NAME,"failed to register tcp channel with selector: "+e1.getMessage());
			return null;
		}
		
		ses.setSocketchannel(channel);
		
		synchronized(syncTable){
			if(!table.containsKey(keys)){
				table.put(keys, ses);
			}else{
				found = true;
			}
		}
		if(found){
			try {
				channel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			ses = null;
		}
		
		return ses;
	}


    //TODO: Hoque, this TCP session is to disseminate flow meta data realtime to the MetaMine broker
	/*
    public Session createMetaMineSession(int ip, int port, int srcIp, int srcPort, String transport){
        // TODO: Hoque, Create new types of key including protocol and application
        String keys = createKey(ip,port, srcIp, srcPort, transport);
        Session ses = new Session();
        ses.setDestAddress(ip);
        ses.setDestPort(port);
        ses.setSourceIp(srcIp);
        ses.setSourcePort(srcPort);
        ses.setTransport(transport);
        ses.setConnected(false);

        SocketChannel channel = null;


        try {
            channel = SocketChannel.open();
            channel.socket().setKeepAlive(true);
            channel.socket().setTcpNoDelay(true);
            channel.socket().setSoTimeout(0);
            channel.socket().setReceiveBufferSize(DataConst.MAX_RECEIVE_BUFFER_SIZE);
            channel.configureBlocking(false);
        }catch(SocketException ex){
            return null;
        } catch (IOException e) {
            Log.e(CLASS_NAME,"Failed to create SocketChannel: "+e.getMessage());
            return null;
        }

        String ips = PacketUtil.intToIPAddress(ip);
        Log.d(CLASS_NAME,"created new MetaMine socketchannel "+ips+":"+port+"::"+PacketUtil.intToIPAddress(srcIp)+":"+srcPort);
        protector.protect(channel.socket());
        Log.d(CLASS_NAME,"Protected new MetaMine socketchannel");

        //initiate connection to redude latency
        SocketAddress addr = new InetSocketAddress(ips,port);
        Log.d(CLASS_NAME,"initiate connecting to remote MetaMine Broker: "+ips+":"+port);
        boolean connected = false;
        try{
            connected = channel.connect(addr);
            ses.setConnected(connected);



        }catch(ClosedChannelException ex){
        }catch(UnresolvedAddressException ex2){
        }catch(UnsupportedAddressTypeException ex3){
        }catch(SecurityException ex4){
        }catch(IOException ex5){
        }




        return ses;
    }
	*/
    /**
	 * create session key based on destination ip+port and source ip+port
	 * @param ip
	 * @param port
	 * @param srcIp
	 * @param srcPort
	 * @return
	 */


	public String createKey(int ip, int port, int srcIp, int srcPort, String protocol){
		return PacketUtil.intToIPAddress(srcIp)+":"+srcPort+"::"+PacketUtil.intToIPAddress(ip) + ":" + port+"::"+protocol;
	}
}
