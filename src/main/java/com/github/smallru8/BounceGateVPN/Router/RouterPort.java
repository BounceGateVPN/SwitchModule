package com.github.smallru8.BounceGateVPN.Router;

import java.util.UUID;

import org.java_websocket.WebSocket;

import com.github.Mealf.BounceGateVPN.Router.ARP;
import com.github.Mealf.BounceGateVPN.Router.VirtualRouter;
import com.github.smallru8.BounceGateVPN.Switch.SwitchPort;
import com.github.smallru8.driver.tuntap.TapDevice;
import com.github.smallru8.util.abstracts.Port;

public class RouterPort extends Port{

	public byte[] MACAddr;
	public VirtualRouter vr;
	private ARP arp = new ARP();
	
	public WebSocket ws;
	public TapDevice td;
	public SwitchPort sPort;
	public RouterPort rPort;
	
	public WebSocket ws;
	public TapDevice td;
	public SwitchPort sPort;
	public RouterPort rPort;
	
	public RouterPort(WebSocket ws) {
		this.ws = ws;
		type = DeviceType.WS;
		setMAC();
		arp.setMAC(MACAddr);
	}
	public RouterPort(TapDevice td) {
		this.td = td;
		type = DeviceType.TunTap;
		setMAC();
		arp.setMAC(MACAddr);
	}
	public RouterPort(SwitchPort sPort) {
		this.sPort = sPort;
		type = DeviceType.virtualSwitch;
		setMAC();
		arp.setMAC(MACAddr);
	}
	public RouterPort(RouterPort rPort) {
		this.rPort = rPort;
		type = DeviceType.virtualRouter;
		setMAC();
		arp.setMAC(MACAddr);
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
	 * Device send data to router
	 * @param data
	 */
	@Override
	public void sendToVirtualDevice(byte[] data) {
		if(type==DeviceType.WS) {//ws
			vr.sendDataToRouter(ws.hashCode(), data);
		}else if(type==DeviceType.TunTap){//tap
			vr.sendDataToRouter(td.hashCode(), data);
		}else if(type==DeviceType.virtualSwitch){//switch
			vr.sendDataToRouter(sPort.hashCode(), data);
		}else {//router
			vr.sendDataToRouter(rPort.hashCode(), data);
		}
	}

}
