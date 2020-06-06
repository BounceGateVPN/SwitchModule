package com.github.smallru8.BounceGateVPN.Router;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.java_websocket.WebSocket;

import com.github.Mealf.BounceGateVPN.Router.RoutingTable;
import com.github.smallru8.BounceGateVPN.Switch.SwitchPort;
import com.github.smallru8.BounceGateVPN.device.Port;
import com.github.smallru8.driver.tuntap.TapDevice;

public class VirtualRouter extends Thread{
	public static byte[] MACAddr_Upper = new byte[] {0x5E,0x06,0x10};
	
	private boolean powerFlag;
	private RoutingTable routingTable;
	public Map<Integer,RouterPort> port;//紀錄連接上此Router的設備，用hashCode()識別
	private BlockingQueue<byte[]> outputQ;//要輸出的data queue
	
	/**
	 * 建立Router
	 */
	public VirtualRouter() {
		powerFlag = true;
		outputQ = new LinkedBlockingQueue<byte[]>();
		routingTable = new RoutingTable();
		port = new HashMap<>();
		
		
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
	 * @param ws
	 */
	public Port addDevice(WebSocket ws) {
		if(port.size() == 0)
			routingTable.addRoutingTable(-1062709502, 32, -1062709501, ws.hashCode());
		if(port.size() == 1)
			routingTable.addRoutingTable(-1062709501, 32, -1062709501, ws.hashCode());
		RouterPort sp = new RouterPort(ws);
		sp.vr = this;
		port.put(ws.hashCode(), sp);
		return sp;
	}
	
	/**
	 * 連接TD設備到此Router
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
	 * @param devHashCode
	 */
	public void delDevice(int devHashCode) {
		port.remove(devHashCode);
	}
	
	/**
	 * device送資料給Router
	 * @param devHashCode
	 * @param data
	 */
	public void sendDataToRouter(int devHashCode, byte[] data) {
		outputQ.add(data);
	}
	
	/**
	 * Router送資料給device
	 * @param devHashCode
	 * @param data
	 */
	private void sendDataToDevice(int devHashCode, byte[] data) {
		if(devHashCode == 0) {
			
		}
		else {
			port.get(devHashCode).sendToDevice(data);
		}
	}
	
	@Override
	public void run() {
		byte[] buffer;
		while(powerFlag) {
			try {
				buffer = outputQ.take();
				sendDataToDevice(routingTable.searchDesPortHashCode(buffer), buffer);
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
