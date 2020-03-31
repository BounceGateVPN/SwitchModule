package com.github.smallru8.BounceGateVPN.Switch;

import org.java_websocket.WebSocket;

import com.github.smallru8.BounceGateVPN.Router.RouterPort;
import com.github.smallru8.BounceGateVPN.device.Port;
import com.github.smallru8.driver.tuntap.TapDevice;

public class SwitchPort extends Port{
	
	public VirtualSwitch vs;
	
	public SwitchPort(WebSocket ws) {
		super(ws);
	}
	public SwitchPort(TapDevice td) {
		super(td);
	}
	public SwitchPort(SwitchPort port) {
		super(port);
	}
	public SwitchPort(RouterPort port) {
		super(port);
	}

	@Override
	/**
	 * Switch send data to device(for switch call)
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
	 * Device send data to this switch
	 * @param data
	 */
	@Override
	public void sendToVirtualDevice(byte[] data) {
		vs.sendDataToSwitch(sPort.hashCode(), data);
	}
	
}
