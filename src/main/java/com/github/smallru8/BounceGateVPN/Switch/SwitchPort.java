package com.github.smallru8.BounceGateVPN.Switch;

import org.java_websocket.WebSocket;

import com.github.smallru8.BounceGateVPN.Router.RouterPort;
import com.github.smallru8.driver.tuntap.TapDevice;
import com.github.smallru8.util.abstracts.Port;

public class SwitchPort extends Port{
	
	public VirtualSwitch vs;
	
	public WebSocket ws;
	public TapDevice td;
	public SwitchPort sPort;
	public RouterPort rPort;
	
	public SwitchPort(TapDevice td) {
		this.td = td;
		type = DeviceType.TunTap;
	}
	public SwitchPort(WebSocket ws) {
		this.ws = ws;
		type = DeviceType.WS;
	}
	public SwitchPort(SwitchPort sPort) {
		this.sPort = sPort;
		type = DeviceType.virtualSwitch;
	}
	public SwitchPort(RouterPort rPort) {
		this.rPort = rPort;
		type = DeviceType.virtualRouter;
	}

	@Override
	/**
	 * Switch send data to device(for switch call)
	 */
	public void sendToDevice(byte[] data) {
		if(type==DeviceType.WS) {//ws
			ws.send(data);
		}else if(type==DeviceType.TunTap){//tap
			td.write(data);
		}else if(type==DeviceType.virtualSwitch){//switch
			sPort.sendToVirtualDevice(data);
		}else {//router
			rPort.sendToVirtualDevice(data);
		}
	}
	
	/**
	 * Device send data to this switch
	 * @param data
	 */
	@Override
	public void sendToVirtualDevice(byte[] data) {
		if(type==DeviceType.WS) {//ws
			vs.sendDataToSwitch(ws.hashCode(), data);
		}else if(type==DeviceType.TunTap){//tap
			vs.sendDataToSwitch(td.hashCode(), data);
		}else if(type==DeviceType.virtualSwitch){//switch
			vs.sendDataToSwitch(sPort.hashCode(), data);
		}else {//router
			vs.sendDataToSwitch(rPort.hashCode(), data);
		}
	}
	
}
