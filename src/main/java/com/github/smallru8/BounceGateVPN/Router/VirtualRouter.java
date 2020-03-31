package com.github.smallru8.BounceGateVPN.Router;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.java_websocket.WebSocket;

import com.github.smallru8.BounceGateVPN.Switch.SwitchPort;
import com.github.smallru8.BounceGateVPN.device.Port;
import com.github.smallru8.driver.tuntap.TapDevice;

public class VirtualRouter extends Thread{
	public static byte[] MACAddr_Upper = new byte[] {0x5E,0x06,0x10};
	
	private boolean powerFlag;
	public Map<Integer,RouterPort> port;//紀錄連接上此Router的設備，用hashCode()識別
	private BlockingQueue<byte[]> outputQ;//要輸出的data queue
	
	/**
	 * 建立Router
	 */
	public VirtualRouter() {
		powerFlag = true;
		outputQ = new LinkedBlockingQueue<byte[]>();
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
	
	
}
