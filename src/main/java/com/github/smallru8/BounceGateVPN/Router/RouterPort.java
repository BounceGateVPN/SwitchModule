package com.github.smallru8.BounceGateVPN.Router;

import java.util.UUID;

import org.java_websocket.WebSocket;

import com.github.smallru8.BounceGateVPN.Switch.SwitchPort;
import com.github.smallru8.BounceGateVPN.device.Port;
import com.github.smallru8.driver.tuntap.TapDevice;

public class RouterPort extends Port{

	public byte[] MACAddr;
	public VirtualRouter vr;
	
	public RouterPort(WebSocket ws) {
		super(ws);
		setMAC();
	}
	public RouterPort(TapDevice td) {
		super(td);
		setMAC();
	}
	public RouterPort(SwitchPort port) {
		super(port);
		setMAC();
	}
	public RouterPort(RouterPort port) {
		super(port);
		setMAC();
	}
	
	/**
	   * 設定該port的MAC address
	 */
	private void setMAC() {
		MACAddr = new byte[6];
		for(int i=0;i<3;i++)
			MACAddr[i] = VirtualRouter.MACAddr_Upper[i];
		UUID MACAddr_Lower = UUID.randomUUID();
		MACAddr[3] = (byte)MACAddr_Lower.getLeastSignificantBits();
		MACAddr[4] = (byte)(MACAddr_Lower.getLeastSignificantBits()>>8);
		MACAddr[5] = (byte)MACAddr_Lower.getMostSignificantBits();
	}
	
	@Override
	/**
	 * Router send data to device(for router call)
	 */
	public void sendToDevice(byte[] data) {
		if(type==DeviceType.WS) {
			ws.send(data);
		}else if(type==DeviceType.TunTap){
			td.write(data);
		}else if(type==DeviceType.virtualSwitch){
			sPort.sendToVirtualDevice(data);
		}else {
			rPort.sendToVirtualDevice(data);
		}
	}
	
	/**
	 * 未實作
	 * @param data
	 */
	public void sendToVirtualDevice(byte[] data) {
		
	}

}
