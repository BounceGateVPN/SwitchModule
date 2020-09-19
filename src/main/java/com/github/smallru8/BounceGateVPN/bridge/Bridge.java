package com.github.smallru8.BounceGateVPN.bridge;

import com.github.smallru8.BounceGateVPN.Switch.VirtualSwitch;

/**
 * 橋接兩台VirtualSwitch設備
 * @author smallru8
 *
 */
public class Bridge{

	protected BridgeInterface biA;//Virtual device A
	protected BridgeInterface biB;//Virtual device B
	
	protected VirtualSwitch vsA;
	protected VirtualSwitch vsB;
	
	/**
	 * 橋接兩台VirtualSwitch
	 * @param vsA
	 * @param vsB
	 */
	public Bridge(VirtualSwitch vsA,VirtualSwitch vsB) {
		biA = new BridgeInterface();
		biB = new BridgeInterface();
		biA.setInterface(biB);
		biB.setInterface(biA);
		biA.setPort(vsA.addDevice(biA));
		biB.setPort(vsB.addDevice(biB));
		this.vsA = vsA;
		this.vsB = vsB;
	}
	
	public void close() {
		biA.close();
		biB.close();
		vsA.delDevice(biA.hashCode());
		vsB.delDevice(biB.hashCode());
	}
	
}
