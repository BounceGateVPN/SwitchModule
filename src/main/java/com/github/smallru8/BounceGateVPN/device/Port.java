package com.github.smallru8.BounceGateVPN.device;

import org.java_websocket.WebSocket;

import com.github.smallru8.BounceGateVPN.Router.RouterPort;
import com.github.smallru8.BounceGateVPN.Switch.SwitchPort;
import com.github.smallru8.driver.tuntap.TapDevice;

public class Port {
	
	public enum DeviceType{
		WS,TunTap,virtualRouter,virtualSwitch
	}
	
	public DeviceType type;//為WebSocket / TunTap / virtualSwitch / virtualRouter
	
	public WebSocket ws;
	public TapDevice td;
	public SwitchPort sPort;
	public RouterPort rPort;
	
	public Port() {
		
	}
	public Port(TapDevice td) {
		this.td = td;
		type = DeviceType.TunTap;
	}
	public Port(WebSocket ws) {
		this.ws = ws;
		type = DeviceType.WS;
	}
	public Port(SwitchPort sPort) {
		this.sPort = sPort;
		type = DeviceType.virtualSwitch;
	}
	public Port(RouterPort rPort) {
		this.rPort = rPort;
		type = DeviceType.virtualRouter;
	}
	
	/**
	 * Switch/Router to device
	 * @param data
	 */
	public void sendToDevice(byte[] data) {
		if(type==DeviceType.WS) {
			ws.send(data);
		}else if(type==DeviceType.TunTap){
			td.write(data);
		}
	}
	
	/**
	 * Device to switch/router
	 * @param data
	 */
	public void sendToVirtualDevice(byte[] data) {
		
	}

}
