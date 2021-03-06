package net.joshuad.hypnos;

import java.io.Serializable;

public class SocketCommand implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public enum CommandType {
		CONTROL,
		SET_TRACKS
	}
	
	//REFACTOR: Maybe change these to enums
	public static final int NEXT = 0;
	public static final int PREVIOUS = 1;
	public static final int PAUSE = 2;
	public static final int PLAY = 3;
	public static final int TOGGLE_PAUSE = 4;
	public static final int STOP = 5;
	public static final int TOGGLE_MINIMIZED = 6;
	public static final int VOLUME_DOWN = 7;
	public static final int VOLUME_UP = 8;
	public static final int SEEK_FORWARD = 9;
	public static final int SEEK_BACK = 10;
	public static final int SHOW = 11;
	public static final int RECEIPT_ACKNOWLEDGED = 12;
	
	private CommandType type;
	private Object object;
	
	public SocketCommand ( CommandType type, Object object ) {
		this.type = type;
		this.object = object;
	}
	
	public CommandType getType () { return type; }
	public Object getObject() { return object; }
}
