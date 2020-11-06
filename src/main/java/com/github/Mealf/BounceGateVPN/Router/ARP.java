package com.github.Mealf.BounceGateVPN.Router;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import com.github.Mealf.util.ConvertIP;
import com.github.smallru8.driver.tuntap.Analysis;

public class ARP extends TimerTask {
	private static Timer timer;
	private ArrayList<ARPTable> table;

	public byte[] IPAddr;
	public byte[] MACAddr;

	public ARP() {
		table = new ArrayList<ARPTable>();
		timer = new Timer();
		timer.schedule(this, 1000, 30000);// 30s
		IPAddr = new byte[4];
		MACAddr = new byte[6];
	}

	/**
	 * 取得Tap nif 所有IPv4 address、MAC address
	 * 
	 * @param MACAddr
	 * @throws SocketException
	 */
	public void setMAC(byte[] MACAddr) {
		this.MACAddr = MACAddr;
	}

	public void setIP(byte[] IPAddr) {
		this.IPAddr = IPAddr;
	}
	
	public boolean isARPRequest(byte[] data) {
		return (data[20] == 0x00 && data[21] == 0x01);
	}
	
	public boolean isARPReply(byte[] data) {
		return (data[20] == 0x00 && data[21] == 0x02);
	}
	
	public int getDesIP(byte[] data) {
		byte[] desIPAddr = { data[38], data[39], data[40], data[41] };
		return ConvertIP.toInteger(desIPAddr);
	}

	/**
	 * 自動發送 arp request return arp packet 接收arp reply return null 目標IP不符合local IP
	 * return null
	 * 
	 * @param data
	 * @return
	 */
	public byte[] arpAnalyzer(byte[] data) {

		if (isARPRequest(data)) {// arp request
			byte[] desIPAddr = { data[38], data[39], data[40], data[41] };

			if (Arrays.equals(IPAddr, desIPAddr)) {// 目標IP為本機

				byte[] srcIPAddr = { data[28], data[29], data[30], data[31] };// 發送者IP address(本機)
				byte[] srcMACAddr = { data[22], data[23], data[24], data[25], data[26], data[27] };// 發送者 MAC
																									// address(本機)

				return generateARPreplyPacket(IPAddr, MACAddr, srcIPAddr, srcMACAddr);// 回應reply packet

			}

		} else if (isARPReply(data)) {// arp reply
			System.out.println("get arp reply");
			byte[] desIPAddr = { data[38], data[39], data[40], data[41] };
			byte[] desMACAddr = { data[32], data[33], data[34], data[35], data[36], data[37] };

			if (Arrays.equals(desMACAddr, MACAddr)) {// 為本機MAC address
				if (Arrays.equals(IPAddr, desIPAddr)) {// 目標IP為本機

					byte[] IPAddr = { data[28], data[29], data[30], data[31] };// 對方IP
					byte[] MACAddr = { data[22], data[23], data[24], data[25], data[26], data[27] };// 對方MAC

					ARPTable tmpTable = new ARPTable(IPAddr, MACAddr);
					Iterator<ARPTable> it = table.iterator();
					while (it.hasNext()) {// 檢查重複 並更新ARP table
						if (Arrays.equals(it.next().IPAddr, IPAddr) || Arrays.equals(it.next().MACAddr, MACAddr)) {
							it.remove();
							break;
						}
					}
					table.add(tmpTable);
					return null;
				}

			}
		}
		return null;
	}

	/**
	 * 搜尋ARP table中該IP address對應的MAC address
	 * 
	 * @param IPAddr
	 * @return
	 */
	public byte[] searchMACbyIP(byte[] IPAddr) {
		Iterator<ARPTable> it = table.iterator();
		while (it.hasNext()) {
			ARPTable filed = it.next();
			if (Arrays.equals(filed.IPAddr, IPAddr)) {
				filed.flag = true;
				return filed.MACAddr;
			}
		}
		return null;// table沒有紀錄
	}
	
	/**
	 * 搜尋ARP table中該IP address對應的MAC address
	 * 
	 * @param IPAddr
	 * @return
	 */
	public byte[] searchMACbyIP(int IPAddr) {
		return searchMACbyIP(ConvertIP.toByteArray(IPAddr));
	}

	/**
	 * generate a ARP request packet
	 * 
	 * @param desIPAddr
	 * @param srcIPAddr
	 * @param srcMACAddr
	 * @return
	 */
	public byte[] generateARPrequestPacket(byte[] srcIPAddr, byte[] srcMACAddr, byte[] desIPAddr) {
		byte[] packet = new byte[60];

		// des MAC addr FF:FF:FF:FF:FF:FF
		for (int i = 0; i < 6; i++)
			packet[i] = (byte) 0xFF;

		// src MAC addr srcMACAddr
		for (int i = 0; i < 6; i++)
			packet[i + 6] = srcMACAddr[i];

		// Type = ARP
		packet[12] = 0x08;
		packet[13] = 0x06;

		// Hardware type = Ethernet
		packet[14] = 0x00;
		packet[15] = 0x01;

		// Protocol type = IPv4
		packet[16] = 0x08;
		packet[17] = 0x00;

		// Hardware size = 6
		packet[18] = 0x06;

		// Protocol size = 4
		packet[19] = 0x04;

		// Opcode = 1
		packet[20] = 0x00;
		packet[21] = 0x01;

		// src MAC address = srcMACAddr
		for (int i = 0; i < 6; i++)
			packet[i + 22] = srcMACAddr[i];

		// src IP address = srcIPAddr
		packet[28] = srcIPAddr[0];
		packet[29] = srcIPAddr[1];
		packet[30] = srcIPAddr[2];
		packet[31] = srcIPAddr[3];

		// des MAC address = 00:00:00:00:00:00
		for (int i = 32; i < 38; i++)
			packet[i] = 0x00;

		// des IP address = desIPAddr
		packet[38] = desIPAddr[0];
		packet[39] = desIPAddr[1];
		packet[40] = desIPAddr[2];
		packet[41] = desIPAddr[3];

		// Padding
		for (int i = 42; i < 60; i++)
			packet[i] = 0x00;

		return packet;
	}

	/**
	 * generate a ARP reply packet
	 * 
	 * @param srcIPAddr
	 * @param srcMACAddr
	 * @param desIPAddr
	 * @param desMACAddr
	 * @return
	 */
	public byte[] generateARPreplyPacket(byte[] srcIPAddr, byte[] srcMACAddr, byte[] desIPAddr, byte[] desMACAddr) {
		byte[] packet = new byte[60];

		// des MAC addr desMACAddr
		for (int i = 0; i < 6; i++)
			packet[i] = desMACAddr[i];

		// src MAC addr srcMACAddr
		for (int i = 0; i < 6; i++)
			packet[i + 6] = srcMACAddr[i];

		// Type = ARP
		packet[12] = 0x08;
		packet[13] = 0x06;

		// Hardware type = Ethernet
		packet[14] = 0x00;
		packet[15] = 0x01;

		// Protocol type = IPv4
		packet[16] = 0x08;
		packet[17] = 0x00;

		// Hardware size = 6
		packet[18] = 0x06;

		// Protocol size = 4
		packet[19] = 0x04;

		// Opcode = 2
		packet[20] = 0x00;
		packet[21] = 0x02;

		// src MAC address = srcMACAddr
		for (int i = 0; i < 6; i++)
			packet[i + 22] = srcMACAddr[i];

		// src IP address = srcIPAddr
		packet[28] = srcIPAddr[0];
		packet[29] = srcIPAddr[1];
		packet[30] = srcIPAddr[2];
		packet[31] = srcIPAddr[3];

		// des MAC address = desMACAddr
		for (int i = 32; i < 38; i++)
			packet[i] = desMACAddr[i - 32];

		// des IP address = desIPAddr
		packet[38] = desIPAddr[0];
		packet[39] = desIPAddr[1];
		packet[40] = desIPAddr[2];
		packet[41] = desIPAddr[3];

		// Padding
		for (int i = 42; i < 60; i++)
			packet[i] = 0x00;

		return packet;
	}
	
	public byte[] generateARPreplyPacket(byte[] requestPacket) {
		if(!isARPRequest(requestPacket))
			return null;
		
		byte[] srcIPAddr = { requestPacket[28], requestPacket[29], requestPacket[30], requestPacket[31] };// 發送者IP address(本機)
		byte[] srcMACAddr = { requestPacket[22], requestPacket[23], requestPacket[24], 
				requestPacket[25], requestPacket[26], requestPacket[27] };// 發送者 MAC
		byte[] desIPAddr = { requestPacket[38], requestPacket[39], requestPacket[40], requestPacket[41] };
		
		return generateARPreplyPacket(desIPAddr, MACAddr, srcIPAddr, srcMACAddr);// 回應reply packet
	}
	
	public boolean isARP(byte[] data) {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(data);
		return (analysis.packetType() == 0x06);
	}

	/**
	 * 定時刪除過久未使用ARP
	 */
	private void TTLCounter() {
		Iterator<ARPTable> it = table.iterator();
		while (it.hasNext()) {
			
			ARPTable field = it.next();
			if (field.flag)
				field.flag = false;
			else
				it.remove();
		}
	}

	@Override
	public void run() {
		TTLCounter();
	}

}
