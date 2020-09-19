package com.github.smallru8.BounceGateVPN.bridge;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;

import javax.net.ssl.SSLSession;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.enums.Opcode;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.framing.Framedata;
import com.github.smallru8.util.abstracts.Port;

public class BridgeInterface implements WebSocket{

	protected Port port = null;//device port
	protected BridgeInterface bi = null;//其他BridgeInterface
	
	public void setPort(Port port) {
		this.port = port;
	}
	
	public void setInterface(BridgeInterface bi) {
		this.bi = bi;
	}
	
	/**
	 * 給其他BridgeInterface call
	 * @param data
	 */
	public void recv(byte[] data) {
		if(port!=null)
			port.sendToVirtualDevice(data);
	}
	
	@Override
	public void close(int code, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void close(int code) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		port = null;
		bi = null;
	}

	@Override
	public void closeConnection(int code, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void send(String text) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void send(ByteBuffer bytes) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void send(byte[] bytes) {
		// TODO Auto-generated method stub
		if(bi!=null)
			bi.recv(bytes);
	}

	@Override
	public void sendFrame(Framedata framedata) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendFrame(Collection<Framedata> frames) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendPing() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendFragmentedFrame(Opcode op, ByteBuffer buffer, boolean fin) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean hasBufferedData() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public InetSocketAddress getRemoteSocketAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public InetSocketAddress getLocalSocketAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isOpen() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isClosing() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isFlushAndClose() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isClosed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Draft getDraft() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ReadyState getReadyState() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getResourceDescriptor() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> void setAttachment(T attachment) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public <T> T getAttachment() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasSSLSupport() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public SSLSession getSSLSession() throws IllegalArgumentException {
		// TODO Auto-generated method stub
		return null;
	}

}
