package net.joshuad.musicplayer.players;

import net.joshuad.musicplayer.Track;

public abstract class AbstractPlayer {

	static final int NO_SEEK_REQUESTED = -1;
	
	public abstract void pause();
	public abstract void play();
	public abstract void stop();
	public abstract void seekPercent ( double positionPercent );
	public abstract void seekMS ( long positionMS );
	public abstract boolean isPaused();
	public abstract Track getTrack();
	public abstract long getPositionMS();
}
