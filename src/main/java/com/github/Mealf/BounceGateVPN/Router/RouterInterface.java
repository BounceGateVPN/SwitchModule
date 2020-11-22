package com.github.Mealf.BounceGateVPN.Router;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import org.skunion.BunceGateVPN.core2.websocket.WS_Client;

import com.github.Mealf.BounceGateVPN.Multicast.Multicast;
import com.github.Mealf.BounceGateVPN.Multicast.MulticastType;
import com.github.Mealf.util.ConvertIP;
import com.github.smallru8.BounceGateVPN.Router.RouterPort;
import com.github.smallru8.Secure2.config.Config;
import com.github.smallru8.driver.tuntap.Analysis;

public class RouterInterface extends WS_Client {

	private ARP arp;
	private byte[] IP= {(byte) 0xC0,(byte) 0xA8,(byte) 0x57,(byte) 0x02};	//192.168.87.2
	private byte[] gateway = {(byte) 0xC0,(byte) 0xA8,(byte) 0x57,(byte) 0x03}; //192.168.87.1
	private int mask = -256; ////netmask is IP format (ex. 255.255.255.0)
	public RouterPort rPort;

	public RouterInterface(Config cfg) throws URISyntaxException {
		super(cfg);
		
		arp = new ARP();
		setIP(cfg.InterfaceIP);
		setGateway(cfg.InterfaceGateway);
		setNetmask(cfg.InterfaceNetmask);
	}
	
	public void setIP(String IP) {
		this.IP = ConvertIP.toByteArray(IP);
		arp.setIP(this.IP);
	}
	
	public void setGateway(String gateway) {
		this.gateway = ConvertIP.toByteArray(gateway);
	}
	
	public void setNetmask(String netmask) {
		this.mask = ConvertIP.toInteger(netmask);
	}
	
	public void setMAC() {
		arp.setMAC(rPort.MACAddr);
	}

	@Override
	public void send(byte[] data) {
		Multicast multicast = new Multicast();
		multicast.setPacket(data);
		if(multicast.getType() != MulticastType.NULL) {
			// fill srcMAC
			for (int i = 0; i < 6; i++)
				data[i + 6] = rPort.MACAddr[i];
		} else {
			try {
				data = arpHandler(data, "send");
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (data != null) {
			super.send(data);
		}
	}

	@Override
	public void onMessage(ByteBuffer message) {
		if (ud.readyFlag && readyFlag) {
			byte[] data = ud.dh.decryption(message.array());
			//arp only deal with reply and request
			try {
				data = arpHandler(data, "onMessage");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if (data != null)
				rPort.sendToVirtualDevice(data);
		}
	}

	private byte[] arpHandler(byte[] data, String act) throws InterruptedException {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(data);
		System.out.println("in arpHandler");
		// arp reply & request
		if (analysis.packetType() == 0x06) {
			byte[] arpReturn = arp.arpAnalyzer(data);
			if (arpReturn != null) {
				super.send(arpReturn);
			}
			return null;
		}

		if (act == "onMessage")
			return data;

		byte[] desMAC;
		byte[] nextHostIP = null;
		// In same local network
		if ((ConvertIP.toInteger(IP) & mask) == (analysis.getDesIPaddress() & mask)) {
			nextHostIP = ConvertIP.toByteArray(analysis.getDesIPaddress());
		} else {
			nextHostIP = gateway;
		}

		int count = 0;
		desMAC = arp.searchMACbyIP(nextHostIP);
		while(desMAC == null) {
			if (count >= 10)
				return null;
			
			byte[] srcIPAddr = this.IP;
			byte[] desIPAddr = ConvertIP.toByteArray(analysis.getDesIPaddress());
			super.send(arp.generateARPrequestPacket(srcIPAddr, rPort.MACAddr, desIPAddr));
			count++;
			Thread.sleep(10);
			desMAC = arp.searchMACbyIP(nextHostIP);
		} 

		// fill desMAC
		for (int i = 0; i < 6; i++)
			data[i] = desMAC[i];

		// fill srcMAC
		for (int i = 0; i < 6; i++)
			data[i + 6] = rPort.MACAddr[i];

		return data;
	}
}
