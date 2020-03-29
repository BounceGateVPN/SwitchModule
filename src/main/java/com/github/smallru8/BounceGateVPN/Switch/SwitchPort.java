package com.github.smallru8.BounceGateVPN.Switch;

import org.java_websocket.WebSocket;
import com.github.smallru8.driver.tuntap.TapDevice;

public class SwitchPort {
	public enum DeviceType{
		WS,TunTap
	}
	
	public DeviceType type;//為WebSocket or TunTap
	public WebSocket ws;
	public TapDevice td;
	
	public SwitchPort(WebSocket ws) {
		this.ws = ws;
		type = DeviceType.WS;
	}
	public SwitchPort(TapDevice td) {
		this.td = td;
		type = DeviceType.TunTap;
	}
	
	public void send(byte[] data) {
		if(type==DeviceType.WS) {
			ws.send(data);
		}else {
			td.write(data);
		}
	}
}
