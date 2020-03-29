package com.github.smallru8.BounceGateVPN.Switch;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.java_websocket.WebSocket;
import com.github.Mealf.BounceGateVPN.Switch.MACAddressTable;
import com.github.smallru8.driver.tuntap.TapDevice;

/**
 * VirtualSwitch
 * 建立方法:
 * 
 * VirtualSwitch sw = new VirtualSwitch();
 * sw.start();
 * 
 * @author smallru8
 *
 */
public class VirtualSwitch extends Thread{
	
	private boolean powerFlag;
	private MACAddressTable switchTable;
	public Map<Integer,SwitchPort> port;//紀錄連接上此Switch的設備，用hashCode()識別
	private BlockingQueue<byte[]> outputQ;//要輸出的data queue
	
	/**
	 * 建立一台Switch
	 */
	public VirtualSwitch() {
		powerFlag = true;
		outputQ = new LinkedBlockingQueue<byte[]>();
		switchTable = new MACAddressTable();
		port = new HashMap<>();
	}
	
	/**
	 * 刪除此Switch
	 */
	public void delVirtualSwitch() {
		powerFlag = false;
		port.clear();
		outputQ.clear();
	}
	
	/**
	 * 連接WS設備到此Switch
	 * @param ws
	 */
	public void addDevice(WebSocket ws) {
		SwitchPort sp = new SwitchPort(ws);
		port.put(ws.hashCode(), sp);
	}
	
	/**
	 * 連接TD設備到此Switch
	 * @param td
	 */
	public void addDevice(TapDevice td) {
		SwitchPort sp = new SwitchPort(td);
		port.put(td.hashCode(), sp);
	}
	
	/**
	 * 從Switch中移除設備
	 * 傳入ws.hashCode()或td.hashCode()
	 * @param devHashCode
	 */
	public void delDevice(int devHashCode) {
		switchTable.remove(devHashCode);
		port.remove(devHashCode);
	}
	
	/**
	 * device送資料給Switch
	 * @param devHashCode 來源port編號
	 * @param data
	 */
	public void sendDataToSwitch(int devHashCode,byte[] data) {//由其他執行緒呼叫，資料要解密過
		//int desPort = switchTable.analysisPacket(data, devHashCode);
		switchTable.addSwitchTable(data, devHashCode);//加紀錄
		outputQ.add(data);
	}
	
	/**
	 * Switch送資料給device
	 * @param devHashCode
	 * @param data
	 */
	private void sendDataToDevice(int devHashCode,byte[] data) {//由Switch呼叫，之後要在送出前加密，現在先直接送
		if(devHashCode == 0) {//廣播
			int tmpHashCode = switchTable.searchSrcPortHashCode(data);
			for(int k : port.keySet()) {
				if(k!=tmpHashCode) {//不要送給自己
					/*
					 * 這裡會有個加密模組
					 */
					port.get(k).send(data);
				}
			}
		}else {//送給指定port
			/*
			 * 這裡會有個加密模組
			 */
			port.get(devHashCode).send(data);
		}
	}
	
	@Override
	public void run() {
		byte[] buffer;
		while(powerFlag) {
			try {
				buffer = outputQ.take();//這裡會Blocking直到有東西進來
				sendDataToDevice(switchTable.searchDesPortHashCode(buffer),buffer);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
