package com.github.Mealf.BounceGateVPN.Router;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.java_websocket.WebSocket;

import com.github.Mealf.BounceGateVPN.Multicast.Multicast;
import com.github.Mealf.BounceGateVPN.Multicast.MulticastType;
import com.github.Mealf.util.ConvertIP;
import com.github.smallru8.BounceGateVPN.Router.RouterPort;
import com.github.smallru8.Secure2.config.Config;
import com.github.smallru8.driver.tuntap.Analysis;
import com.github.smallru8.util.abstracts.Port;
import com.github.smallru8.util.log.EventSender;

public class VirtualRouter extends Thread {
	public static byte[] MACAddr_Upper = new byte[] { 0x5E, 0x06, 0x10 };
	private int switch_hashcode = 2;
	private int interface_hashcode = 1;

	private boolean powerFlag;
	private RoutingTable routingTable;
	public Map<Integer, RouterPort> port;// 紀錄連接上此Router的設備，用hashCode()識別
	private BlockingQueue<byte[]> outputQ;// 要輸出的data queue
	private ARP arp;
	private RouterInterface routerInterface;

	private Multicast multicast;
	private Timer timer;
	ExecutorService fixedThreadPool = Executors.newCachedThreadPool();
	private String routerIP = "";
	private int netmask;
	private byte[] MACAddr;
	final private int QUERY_INTERVAL = 6 * 1000;
	final private int QUERY_START_AFTER = 5 * 1000;

	/**
	 * 建立Router 
	 * default IP is 192.168.0.1/24
	 */
	public VirtualRouter() {
		powerFlag = true;
		outputQ = new LinkedBlockingQueue<byte[]>();
		routingTable = new RoutingTable();
		port = new HashMap<>();
		
		multicast = new Multicast();
		arp = new ARP();
		
		setIP("192.168.0.1");
		setMAC();
		setMask(24);

		// Send IGMP query regularly
		EventSender.sendLog("Create Timer");
		timer = new Timer();
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				byte[] query_msg = multicast.generateQuery(2);
				if (query_msg != null)
					sendDataToDevice(0, query_msg);
			}
		}, QUERY_START_AFTER, QUERY_INTERVAL);
	}
	
	public VirtualRouter(Config cfg) {
		this();
		setIP(cfg.ip);
		setMask(cfg.netmask);
		setRoutingTable(cfg.routingTable);

	}
	

	private void setMAC() {
		MACAddr = new byte[6];
		for (int i = 0; i < 3; i++)
			MACAddr[i] = VirtualRouter.MACAddr_Upper[i];
		UUID MACAddr_Lower = UUID.randomUUID();
		MACAddr[3] = (byte) MACAddr_Lower.getLeastSignificantBits();
		MACAddr[4] = (byte) (MACAddr_Lower.getLeastSignificantBits() >> 8);
		MACAddr[5] = (byte) MACAddr_Lower.getMostSignificantBits();
		
		multicast.setRouterMAC(MACAddr);
		arp.setMAC(MACAddr);
	}

	public void setMask(int slashNumber) {
		netmask = 0;
		for (int i = 0; i < 32; i++) {
			netmask = netmask << 1;
			if (i < slashNumber)
				netmask = netmask | 1;
		}
	}
	private void setRoutingTable(String routingTable) {
		String[] routingFields = routingTable.split(";");
		for(String routingField:routingFields) {
			String[] args = routingField.split(",");
			int mask = 0,tmpMask = ConvertIP.toInteger(args[1]);
			for(int i=32;i>=0;i--) {
				if((tmpMask & 1) == 1) {
					mask =  i;
					break;
				}
				tmpMask = tmpMask >> 1;
			}
			int des = ConvertIP.toInteger(args[0]);
			int gateway = ConvertIP.toInteger(args[2]);
			int hashcode;
			if(args[3] == "switch")
				hashcode = switch_hashcode;
			else
				hashcode = interface_hashcode;
			//hashcode may not same
			this.routingTable.addRoutingTable(des, mask, gateway, hashcode);
		}
	}
	
	public void setMask(String ip_format) {
		netmask = ConvertIP.toInteger(ip_format);
	}
	
	public void setIP(String IP) {
		this.routerIP = IP;
		multicast.setRouterIP(routerIP);
		arp.setIP(ConvertIP.toByteArray(routerIP));
	}
	
	public String getIP() {
		return this.routerIP;
	}
	
	public int getMask() {
		int tmpMask = this.netmask;
		for(int i=32;i>=0;i--) {
			if((tmpMask & 1) == 1) {
				return i;
			}
			tmpMask = tmpMask >> 1;
		}
		return 0;
	}
	
	public byte[] getMAC() {
		return this.getMAC();
	}

	/**
	 * 刪除Router
	 */
	public void delVirtualRouter() {
		powerFlag = false;
		port.clear();
		outputQ.clear();
	}

	/**
	 * 連接WS設備到此Router
	 * 只用來加入連接switch的Bridge
	 * @param ws
	 */
	public Port addDevice(WebSocket ws) {
		RouterPort sp = new RouterPort(ws);
		sp.vr = this;
		
		/*change current hashcode*/
		routingTable.changeHashCode(switch_hashcode, ws.hashCode());
		switch_hashcode = ws.hashCode();
		
		port.put(switch_hashcode, sp);
		
		return sp;
	}

	public void addRouterInterface(Config user) throws URISyntaxException {
		routerInterface = new RouterInterface(user);
		RouterPort rPort = new RouterPort(routerInterface);
		rPort.vr = this;
		
		/*change current hashcode*/
		routingTable.changeHashCode(interface_hashcode, routerInterface.hashCode());
		interface_hashcode = routerInterface.hashCode();
		
		routerInterface.rPort = rPort;
		routerInterface.setMAC();
		routerInterface.connect();
		
		port.put(interface_hashcode, rPort);
	}
	
	public void addRoutingTable(int des, int mask, int gateway, int hashcode) {
		routingTable.addRoutingTable(des, mask, gateway, hashcode);
	}
	
	public void addRoutingTable(int des, int mask, int gateway, String device) {
		if(device == "switch")
			routingTable.addRoutingTable(des, mask, gateway, switch_hashcode);
		else
			routingTable.addRoutingTable(des, mask, gateway, interface_hashcode);
	}

	/**
	 * 從Router中移除設備,只用於刪除switch port
	 * 
	 * @param devHashCode
	 */
	public void delDevice(int devHashCode) {
		port.remove(switch_hashcode);
	}

	/**
	 * device送資料給Router
	 * 
	 * @param devHashCode
	 * @param data
	 */
	public void sendDataToRouter(int devHashCode, byte[] data) {
		outputQ.add(data);
	}

	/**
	 * Router送資料給device
	 * 
	 * @param devHashCode
	 * @param data
	 */
	private void sendDataToDevice(int devHashCode, byte[] data) {
		if (data == null)
			return;
		EventSender.sendLog("send data.");
		int tmpHashCode = routingTable.searchSrcPortHashCode(data);
		Analysis analysis = new Analysis();
		analysis.setFramePacket(data);
		
		
		if (analysis.packetType() == 0x06) {
			/*handle ARP reply*/
			
			EventSender.sendLog("data is arp");
			try {
				arpHandler(data);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		} 	else if (devHashCode == 0 && !isInLocalNetwork(analysis.getDesIPaddress())) {
			/*handle multicast*/
			multicastHandler(tmpHashCode, data);
			
		} else if(devHashCode == interface_hashcode) {
			/*send to routerInterface*/
			
			RouterPort dst_port = port.get(devHashCode);
			if (dst_port != null)	
				dst_port.sendToDevice(data);
			
		} else if(devHashCode == switch_hashcode || (devHashCode==0 && isInLocalNetwork(analysis.getDesIPaddress()))) {
			/*send to switch*/
			
			if(port.get(switch_hashcode)==null)	//not yet added switch
				return;
			
			try {
				data = arpHandler(data);
			} catch (InterruptedException e) {
				data = null;
				e.printStackTrace();
			}
			if (data == null)
				return;

			port.get(switch_hashcode).sendToDevice(data);
		}
	}

	private void broadcast(byte[] data) {
		int tmpHashCode = routingTable.searchSrcPortHashCode(data);

		for (int k : port.keySet()) {
			if (k != tmpHashCode) {
				port.get(k).sendToDevice(data);
			}
		}
	}

	private void sendToSwitch(byte[] data) {
		RouterPort desPort = port.get(switch_hashcode);
		if(desPort != null)
			desPort.sendToDevice(data);
	}

	private byte[] arpHandler(byte[] data) throws InterruptedException {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(data);
		// arp reply & request
		if (analysis.packetType() == 0x06) {
			arp.arpAnalyzer(data);
			byte[] arpReturn = arp.arpAnalyzer(data);
			
			if (arpReturn != null) {
					port.get(switch_hashcode).sendToDevice(arpReturn);
			}
			return null;
		}

		byte[] desMAC;
		byte[] nextHostIP = null;
		String gateway = ConvertIP.toString(routingTable.searchGateway(data));
		if (isInLocalNetwork(analysis.getDesIPaddress())) {
			nextHostIP = ConvertIP.toByteArray(analysis.getDesIPaddress());
		}  else {
			nextHostIP = ConvertIP.toByteArray(gateway);
		}
		int count = 0;
		desMAC = arp.searchMACbyIP(nextHostIP);
		while(desMAC == null) {
			/*send ARP request*/
			if (count >= 10)
				return null;
			
			byte[] srcIPAddr = ConvertIP.toByteArray(routerIP);
			byte[] desIPAddr = ConvertIP.toByteArray(analysis.getDesIPaddress());
			sendToSwitch(arp.generateARPrequestPacket(srcIPAddr, MACAddr, desIPAddr));
			count++;
			Thread.sleep(500);
			desMAC = arp.searchMACbyIP(nextHostIP);
		}

		// fill desMAC
		for (int i = 0; i < 6; i++)
			data[i] = desMAC[i];

		// fill srcMAC
		for (int i = 0; i < 6; i++)
			data[i + 6] = MACAddr[i];

		return data;
	}
	
	private void multicastHandler(int srcPortHashCode, byte[] data) {
		multicast.setPacket(data);

		// fill srcMAC
		for (int i = 0; i < 6; i++)
			data[i + 6] = MACAddr[i];

		if (multicast.getType() == MulticastType.MULTICAST && !multicast.isSpecialAddress()) {	
			/*handle multicast*/
			
			EventSender.sendLog("data is multicast");
			ArrayList<byte[]> IPList = multicast.getIPinGroup();
			if (IPList == null) {
				EventSender.sendLog("No member in group.\n");
				return;
			}
			
			Set<Integer> ports = new HashSet<>();
			for (byte[] ip : IPList) {
				EventSender.sendLog("send to:" + ConvertIP.toString(ip));
				int IPNum = ConvertIP.toInteger(ip), hashcode;
				hashcode = routingTable.searchDesPortHashCode(IPNum);
				ports.add(hashcode);
			}
			for(int hashcode: ports) {
				RouterPort dst_port = port.get(hashcode);
				if (srcPortHashCode != hashcode && dst_port != null)
					dst_port.sendToDevice(data);
			}

		} else {
			broadcast(data);
		}
	}
	
	private boolean isInLocalNetwork(int searchIP) {
		return ((searchIP&netmask) == (ConvertIP.toInteger(routerIP)&netmask));
	}

	@Override
	public void run() {
		byte[] buffer;
		System.out.println("router run!!");
		while (powerFlag) {
			try {
				buffer = outputQ.take();

				/*final byte[] data = buffer;
				fixedThreadPool.execute(new Runnable() {
					@Override
					public void run() {
						sendDataToDevice(routingTable.searchDesPortHashCode(data), data);
					}
				});*/

				 sendDataToDevice(routingTable.searchDesPortHashCode(buffer), buffer);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
