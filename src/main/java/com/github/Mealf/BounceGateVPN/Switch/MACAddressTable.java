package com.github.Mealf.BounceGateVPN.Switch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import com.github.smallru8.driver.tuntap.Analysis;

public class MACAddressTable extends TimerTask {
	class MACAddressField {
		public byte[] MACAddr;
		public int sessionHashCode;
		public boolean flag;// 被用到就設為true

		MACAddressField(byte[] MACAddr, int hashCode) {
			this.MACAddr = MACAddr;
			this.sessionHashCode = hashCode;
			flag = true;
		}
	}

	private static Timer timer;
	private ArrayList<MACAddressField> table;

	public MACAddressTable() {
		table = new ArrayList<MACAddressField>();
		timer = new Timer();
		timer.schedule(this, 1000, 30000);// 30s
	}

	/**
	 * 找desMAC address 的 WS/Tap hashCode
	 * 不在switch table則回傳0
	 * @param packet
	 * @param hashCode
	 * @return
	 */
	public int analysisPacket(byte[] packet, int hashCode) {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(packet);
		byte[] srcMAC = analysis.getFrameSrcMACAddr();
		byte[] desMAC = analysis.getFrameDesMACAddr();
		if (searchSessionByMAC(srcMAC) == 0) {
			this.table.add(new MACAddressField(srcMAC, hashCode));
		}

		return searchSessionByMAC(desMAC);
	}

	public void addSwitchTable(byte[] packet, int hashCode) {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(packet);
		byte[] srcMAC = analysis.getFrameSrcMACAddr();
		if (searchSessionByMAC(srcMAC) == 0) {
			this.table.add(new MACAddressField(srcMAC, hashCode));
		}
	}
	
	/**
	 * 用封包資料找Des的port
	 * @param packet
	 * @return
	 */
	public int searchDesPortHashCode(byte[] packet) {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(packet);
		byte[] desMAC = analysis.getFrameDesMACAddr();

		return searchSessionByMAC(desMAC);
	}
	
	/**
	 * 用封包資料找Src的port
	 * @param packet
	 * @return
	 */
	public int searchSrcPortHashCode(byte[] packet) {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(packet);
		byte[] srcMAC = analysis.getFrameSrcMACAddr();

		return searchSessionByMAC(srcMAC);
	}
	
	/**
	 * 用MAC address找port
	 * @param MACAddr
	 * @return
	 */
	public int searchSessionByMAC(byte[] MACAddr) {
		Iterator<MACAddressField> it = table.iterator();
		while (it.hasNext()) {
			MACAddressField field = it.next();
			if (Arrays.equals(field.MACAddr, MACAddr)) {
				field.flag = true;
				return field.sessionHashCode;
			}
		}
		return 0;
	}
	
	public boolean remove(int hashCode) {
		Iterator<MACAddressField> it = table.iterator();
		while (it.hasNext()) {
			MACAddressField field = it.next();
			if (field.sessionHashCode == hashCode) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	@Override
	public void run() {
		TTLCounter();
	}

	private void TTLCounter() {
		Iterator<MACAddressField> it = table.iterator();
		while (it.hasNext()) {
			MACAddressField field = it.next();
			if (field.flag)
				field.flag = false;
			else
				it.remove();
		}
	}

}
