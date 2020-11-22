package com.github.Mealf.BounceGateVPN.Router;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.github.smallru8.driver.tuntap.Analysis;

public class RoutingTable {
	class RoutingField {
		public int networkDes;
		public int netmask;	//netmask is IP format (ex. 255.255.255.0)
		public int gateway;	//only use in search gateway
		public int sessionHashCode;
		public int metric;

		RoutingField(int des, int mask, int gateway, int hashcode, int metric) {
			this.networkDes = des;
			this.netmask = mask;
			this.gateway = gateway;
			this.sessionHashCode = hashcode;
			this.metric = metric;
		}
	}

	List<RoutingField> table;

	public RoutingTable() {
		table = new ArrayList<>();
	}

	public void addRoutingTable(int des, int mask, int gateway, int hashcode) {	
		/*The bigger the mask, the more front*/
		
		RoutingField field = new RoutingField(des, mask, gateway, hashcode, 1);
		for (int i = 0; i < table.size(); i++) {
			if (Integer.compareUnsigned(table.get(i).netmask, mask) <= 0) {
				table.add(i, field);
				return;
			}
		}
		table.add(field);
		return;
	}

	public int searchDesPortHashCode(byte[] packet) {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(packet);
		int desIP = analysis.getDesIPaddress();
		return searchDesPortHashCode(desIP);
	}

	public int searchDesPortHashCode(int desIP) {
		for (int i = 0; i < table.size(); i++) {
			RoutingField field = table.get(i);
			int netmask =  field.netmask;
			if ((field.networkDes&netmask) == (desIP&netmask))
				return field.sessionHashCode;
		}
		return 0;
	}

	public int searchSrcPortHashCode(byte[] packet) {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(packet);
		int desIP = analysis.getSrcIPaddress();
		for (int i = 0; i < table.size(); i++) {
			RoutingField field = table.get(i);
			int netmask = field.netmask;
			if ((field.networkDes&netmask) == (desIP&netmask))
				return field.sessionHashCode;
		}
		return 0;
	}
	
	public int searchGateway(byte[] packet) {
		Analysis analysis = new Analysis();
		analysis.setFramePacket(packet);
		int desIP = analysis.getSrcIPaddress();
		for (int i = 0; i < table.size(); i++) {
			RoutingField field = table.get(i);
			int netmask = field.netmask;
			if ((field.networkDes&netmask) == (desIP&netmask))
				return field.gateway;
		}
		return 0;
	}
	
	public void changeHashCode(int oldHashCode, int newHashCode) {
		for (int i = 0; i < table.size(); i++) {
			RoutingField field = table.get(i);
			if(field.sessionHashCode == oldHashCode)
				field.sessionHashCode = newHashCode;
		}
		
		return;
	}

	boolean remove(int hashCode) {
		Iterator<RoutingField> it = table.iterator();
		boolean flag = false;
		while (it.hasNext()) {
			RoutingField field = it.next();
			if (field.sessionHashCode == hashCode) {
				it.remove();
				flag = true;
			}
		}
		return flag;
	}
	
	boolean remove(int des, int mask, int gateway) {
		Iterator<RoutingField> it = table.iterator();
		while (it.hasNext()) {
			RoutingField field = it.next();
			if (field.networkDes == des && field.netmask == mask && field.gateway == gateway) {
				it.remove();
				return true;
			}
		}
		return false;
	}
}
