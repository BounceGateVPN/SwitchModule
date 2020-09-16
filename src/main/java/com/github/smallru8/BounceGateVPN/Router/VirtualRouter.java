package com.github.smallru8.BounceGateVPN.Router;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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
import com.github.Mealf.BounceGateVPN.Router.ARP;
import com.github.Mealf.BounceGateVPN.Router.RouterInterface;
import com.github.Mealf.BounceGateVPN.Router.RoutingTable;
import com.github.Mealf.util.ConvertIP;
import com.github.smallru8.BounceGateVPN.Switch.SwitchPort;
import com.github.smallru8.Secure2.config.Config;
import com.github.smallru8.driver.tuntap.Analysis;
import com.github.smallru8.driver.tuntap.TapDevice;
import com.github.smallru8.util.abstracts.Port;
import com.github.smallru8.util.log.EventSender;

public class VirtualRouter extends Thread {
	public static byte[] MACAddr_Upper = new byte[] { 0x5E, 0x06, 0x10 };

	private boolean powerFlag;
	private RoutingTable routingTable;
	public Map<Integer, RouterPort> port;// 紀錄連接上此Router的設備，用hashCode()識別
	private BlockingQueue<byte[]> outputQ;// 要輸出的data queue
	private ARP arp;

	private Multicast multicast;
	private Timer timer;
	ExecutorService fixedThreadPool = Executors.newFixedThreadPool(5);
	private String routerIP = "";
	private int mask;
	private byte[] MACAddr;
	private int interfaceHashcode;

	final private int QUERY_INTERVAL = 6 * 1000;
	final private int QUERY_START_AFTER = 5 * 1000;

	/**
	 * 建立Router
	 */
	public VirtualRouter() {
		powerFlag = true;
		outputQ = new LinkedBlockingQueue<byte[]>();
		routingTable = new RoutingTable();
		port = new HashMap<>();

		multicast = new Multicast();
		arp = new ARP();
		setMAC();
		multicast.setRouterMAC(MACAddr);
		arp.setMAC(MACAddr);
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

	/**
	 * Constructor router with have IP
	 */
	public VirtualRouter(String routerIP) {
		this();
		this.routerIP = routerIP;
		multicast.setRouterIP(routerIP);
		arp.setIP(ConvertIP.toByteArray(routerIP));
	}

	private void setMAC() {
		MACAddr = new byte[6];
		for (int i = 0; i < 3; i++)
			MACAddr[i] = VirtualRouter.MACAddr_Upper[i];
		UUID MACAddr_Lower = UUID.randomUUID();
		MACAddr[3] = (byte) MACAddr_Lower.getLeastSignificantBits();
		MACAddr[4] = (byte) (MACAddr_Lower.getLeastSignificantBits() >> 8);
		MACAddr[5] = (byte) MACAddr_Lower.getMostSignificantBits();
	}

	public void setMask(int slashNumber) {
		mask = 0;
		for (int i = 0; i < 32; i++) {
			mask = mask << 1;
			if (i < slashNumber)
				mask = mask | 1;
		}
	}
	
	public void setIP(String IP) {
		this.routerIP = IP;
	}
	
	public String getIP() {
		return this.routerIP;
	}
	
	public int getMask() {
		int tmpMask = this.mask;
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
	 * 
	 * @param ws
	 */
	public Port addDevice(WebSocket ws) {
		RouterPort sp = new RouterPort(ws);
		sp.vr = this;
		port.put(ws.hashCode(), sp);
		return sp;
	}

	/**
	 * 連接TD設備到此Router
	 * 
	 * @param td
	 */
	public Port addDevice(TapDevice td) {
		RouterPort sp = new RouterPort(td);
		sp.vr = this;
		port.put(td.hashCode(), sp);
		return sp;
	}

	/**
	 * 連接Switch設備到此Router
	 * 
	 * @param sPort
	 * @return
	 */
	public Port addDevice(SwitchPort sPort) {
		RouterPort sp = new RouterPort(sPort);
		sp.vr = this;
		port.put(sPort.hashCode(), sp);
		return sp;
	}

	/**
	 * 連接Router設備到此Router
	 * 
	 * @param rPort
	 * @return
	 */
	public Port addDevice(RouterPort rPort) {
		RouterPort sp = new RouterPort(rPort);
		sp.vr = this;
		port.put(rPort.hashCode(), sp);
		return sp;
	}

	public void addInterface() throws URISyntaxException {
		Config user = new Config();
		user.setConf("test", Config.ConfType.CLIENT);
		RouterInterface routerInterface = new RouterInterface(user);
		RouterPort rPort = (RouterPort) addDevice(routerInterface);
		interfaceHashcode = rPort.hashCode();
		routerInterface.rPort = rPort;
		routerInterface.connect();
	}
	
	public void addRoutingTable(int des, int mask, int gateway, int hashcode) {
		routingTable.addRoutingTable(des, mask, gateway, hashcode);
	}

	/**
	 * 從Router中移除設備
	 * 
	 * @param devHashCode
	 */
	public void delDevice(int devHashCode) {
		port.remove(devHashCode);
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

		int tmpHashCode = routingTable.searchSrcPortHashCode(data);
		Analysis analysis = new Analysis();
		analysis.setFramePacket(data);
		if (analysis.packetType() == 0x06) {
			EventSender.sendLog("data is arp");
			// get arp reply
			try {
				arpHandler(data);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else if (devHashCode == 0) {
			multicast.setPacket(data);

			// fill srcMAC
			for (int i = 0; i < 6; i++)
				data[i + 6] = MACAddr[i];

			if (multicast.getType() == MulticastType.MULTICAST && !multicast.isSpecialAddress()) {
				EventSender.sendLog("data is multicast");
				ArrayList<byte[]> IPList = multicast.getIPinGroup();
				if (IPList == null) {
					EventSender.sendLog("No member in group.\n");
					return;
				}
				for (byte[] ip : IPList) {
					EventSender.sendLog("send to:" + ConvertIP.toString(ip));
					int IPNum = ConvertIP.toInteger(ip), hashcode;
					hashcode = routingTable.searchDesPortHashCode(IPNum);
					// 保險處理
					RouterPort dst_port = port.get(hashcode);
					if (tmpHashCode != hashcode && dst_port != null)
						dst_port.sendToDevice(data);
				}

			} else {
				broadcast(data);
			}
		} else {
			/*
			 * can replace with port.get(devHashCode).type == routerInterface (if hava this
			 * type)
			 */
			if (devHashCode == interfaceHashcode) {
				port.get(devHashCode).sendToDevice(data);
			} else {
				try {
					data = arpHandler(data);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					data = null;
					e.printStackTrace();
				}
				if (data == null)
					return;

				port.get(devHashCode).sendToDevice(data);
			}
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
		for (Entry<Integer, RouterPort> e : port.entrySet()) {
			if (e.getValue().type == Port.DeviceType.virtualSwitch)
				e.getValue().sendToDevice(data);
		}
	}

	private byte[] arpHandler(byte[] data) throws InterruptedException {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(data);
		// arp reply & request
		if (analysis.packetType() == 0x06) {
			arp.arpAnalyzer(data);
			byte[] arpReturn = arp.arpAnalyzer(data);
			if (arpReturn != null) {
				int devHashCode = routingTable.searchSrcPortHashCode(data);
				if (devHashCode != 0)
					port.get(devHashCode).sendToDevice(arpReturn);
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
		do {
			if (count >= 10)
				return null;
			desMAC = arp.searchMACbyIP(nextHostIP);
			byte[] srcIPAddr = ConvertIP.toByteArray(routerIP);
			byte[] desIPAddr = ConvertIP.toByteArray(analysis.getDesIPaddress());
			sendToSwitch(arp.generateARPrequestPacket(srcIPAddr, MACAddr, desIPAddr));
			count++;
			Thread.sleep(500);
		} while (desMAC == null);

		// fill desMAC
		for (int i = 0; i < 6; i++)
			data[i] = desMAC[i];

		// fill srcMAC
		for (int i = 0; i < 6; i++)
			data[i + 6] = MACAddr[i];

		return data;
	}
	
	private boolean isInLocalNetwork(int searchIP) {
		return ((searchIP&mask) == (ConvertIP.toInteger(routerIP)&mask));
	}

	@Override
	public void run() {
		byte[] buffer;
		while (powerFlag) {
			try {
				buffer = outputQ.take();

				final byte[] data = buffer;
				fixedThreadPool.execute(new Runnable() {
					@Override
					public void run() {
						sendDataToDevice(routingTable.searchDesPortHashCode(data), data);
					}
				});

				// sendDataToDevice(routingTable.searchDesPortHashCode(buffer), buffer);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
