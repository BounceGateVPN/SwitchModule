package com.github.smallru8.BounceGateVPN.bridge;


import com.github.Mealf.BounceGateVPN.Router.VirtualRouter;
import com.github.smallru8.BounceGateVPN.Switch.VirtualSwitch;

/**
 * 橋接兩台VirtualSwitch設備
 * @author smallru8
 *
 */
public class Bridge{
	enum BridgeType {switch_switch, switch_router}

	protected BridgeInterface biA;//Virtual device A
	protected BridgeInterface biB;//Virtual device B
	
	protected VirtualSwitch vsA;
	protected VirtualSwitch vsB;
	protected VirtualRouter vr;
	protected BridgeType type;
	
	/**
	 * 橋接兩台VirtualSwitch
	 * @param vsA
	 * @param vsB
	 */
	public Bridge(VirtualSwitch vsA,VirtualSwitch vsB) {
		type = BridgeType.switch_switch; 
		biA = new BridgeInterface();
		biB = new BridgeInterface();
		biA.setInterface(biB);
		biB.setInterface(biA);
		biA.setPort(vsA.addDevice(biA));
		biB.setPort(vsB.addDevice(biB));
		this.vsA = vsA;
		this.vsB = vsB;
	}
	
	public Bridge(VirtualSwitch vs, VirtualRouter vr) {
		type = BridgeType.switch_router; 
		biA = new BridgeInterface();
		biB = new BridgeInterface();
		biA.setInterface(biB);
		biB.setInterface(biA);
		biA.setPort(vs.addDevice(biA));
		biB.setPort(vr.addDevice(biB));
		this.vsA = vs;
		this.vr = vr;
	}
	
	public void close() {
		biA.close();
		biB.close();
		
		if(type == BridgeType.switch_switch) {
			vsA.delDevice(biA.hashCode());
			vsB.delDevice(biB.hashCode());
		} else if(type == BridgeType.switch_router) {
			vsA.delDevice(biA.hashCode());
			vr.delDevice(biB.hashCode());
		}
	}
	
}
