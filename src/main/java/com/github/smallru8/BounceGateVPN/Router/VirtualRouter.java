package com.github.smallru8.BounceGateVPN.Router;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.java_websocket.WebSocket;

import com.github.Mealf.BounceGateVPN.Multicast.Multicast;
import com.github.Mealf.BounceGateVPN.Multicast.MulticastType;
import com.github.Mealf.BounceGateVPN.Router.RoutingTable;
import com.github.Mealf.util.ConvertIP;
import com.github.smallru8.BounceGateVPN.Switch.SwitchPort;
import com.github.smallru8.driver.tuntap.TapDevice;
import com.github.smallru8.util.abstracts.Port;

public class VirtualRouter extends Thread {
	public static byte[] MACAddr_Upper = new byte[] { 0x5E, 0x06, 0x10 };

	private boolean powerFlag;
	private RoutingTable routingTable;
	public Map<Integer, RouterPort> port;// 紀錄連接上此Router的設備，用hashCode()識別
	private BlockingQueue<byte[]> outputQ;// 要輸出的data queue

	private Multicast multicast;
	private Timer timer;
	private String routerIP = "";
	
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

		// Send IGMP query regularly

		System.out.println("Create Timer");
		timer = new Timer();
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				byte[] query_msg = multicast.generateQuery(2);
				if(query_msg!= null)
					sendDataToDevice(0, query_msg);
			}
		}, QUERY_START_AFTER, QUERY_INTERVAL);

	}

	public VirtualRouter(String routerIP, byte[] routerMAC) {
		this();
		this.routerIP = routerIP;
		multicast.setRouterIP(routerIP);
		multicast.setRouterMAC(routerMAC);
	}

	/**
	 * 刪除Router
	 */
	public void delVirtualSwitch() {
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
		System.out.println("add device :" + String.valueOf(ws.hashCode()));
		if (port.size() == 1)
			routingTable.addRoutingTable(-1062709502, 32, -1062709502, ws.hashCode());
		if (port.size() == 2)
			routingTable.addRoutingTable(-1062709501, 32, -1062709501, ws.hashCode());
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
		if (routerIP != "") {
			int IPNumber = ConvertIP.toInt(routerIP);
			routingTable.addRoutingTable(IPNumber, 32, IPNumber, td.hashCode());
		}
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
		System.out.println("devHashCode:" + devHashCode);
		if (devHashCode == 0) {
			multicast.setPacket(data);
			if (multicast.getType() == MulticastType.MULTICAST && !multicast.isSpecialAddress()) {
				System.out.println("send data is multicast");
				ArrayList<byte[]> IPList = multicast.getIPinGroup();
				if (IPList == null) {
					System.out.println("No member is group.\n");
					return;
				}
				for (byte[] ip : IPList) {
					System.out.println("send to:" + ConvertIP.toString(ip));
					int IPNum = ConvertIP.toInt(ip), hashcode;
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
			port.get(devHashCode).sendToDevice(data);
		}

		System.out.print("\n");
	}
	
	private void broadcast(byte[] data) {
		int tmpHashCode = routingTable.searchSrcPortHashCode(data);
		
		for (int k : port.keySet()) {
			if (k != tmpHashCode) {
				System.out.println("send to:" + String.valueOf(k));
				port.get(k).sendToDevice(data);
			}
		}
	}

	@Override
	public void run() {
		byte[] buffer;
		while (powerFlag) {
			try {
				buffer = outputQ.take();
				sendDataToDevice(routingTable.searchDesPortHashCode(buffer), buffer);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
