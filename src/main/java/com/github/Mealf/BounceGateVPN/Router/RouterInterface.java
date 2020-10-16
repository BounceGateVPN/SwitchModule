package com.github.Mealf.BounceGateVPN.Router;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import org.skunion.BunceGateVPN.core2.websocket.WS_Client;

import com.github.Mealf.util.ConvertIP;
import com.github.smallru8.BounceGateVPN.Router.RouterPort;
import com.github.smallru8.Secure2.config.Config;
import com.github.smallru8.driver.tuntap.Analysis;

public class RouterInterface extends WS_Client {

	private ARP arp;
	private byte[] IP;
	private byte[] gateway;
	private int mask;
	public RouterPort rPort;

	public RouterInterface(Config cfg) throws URISyntaxException {
		super(cfg);
		arp = new ARP();
	}

	@Override
	public void send(ByteBuffer bytes) {
		byte[] data = bytes.array();
		try {
			data = arpHandler(data, "send");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if (data != null)
			super.send(data);
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
				sport.sendToVirtualDevice(data);
		}
	}

	private byte[] arpHandler(byte[] data, String act) throws InterruptedException {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(data);
		System.out.println("in arpHandler");
		// arp reply & request
		if (analysis.packetType() == 0x06) {
			arp.arpAnalyzer(data);
			byte[] arpReturn = arp.arpAnalyzer(data);
			if (arpReturn != null) {
				send(arpReturn);
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
		do {
			if (count >= 10)
				return null;
			desMAC = arp.searchMACbyIP(nextHostIP);
			byte[] srcIPAddr = this.IP;
			byte[] desIPAddr = ConvertIP.toByteArray(analysis.getDesIPaddress());
			super.send(arp.generateARPrequestPacket(srcIPAddr, rPort.MACAddr, desIPAddr));
			count++;
			Thread.sleep(500);
		} while (desMAC == null);

		// fill desMAC
		for (int i = 0; i < 6; i++)
			data[i] = desMAC[i];

		// fill srcMAC
		for (int i = 0; i < 6; i++)
			data[i + 6] = rPort.MACAddr[i];

		return data;
	}
}
