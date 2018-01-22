package net.joshuad.hypnos.audio;

import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.joshuad.hypnos.Hypnos;
import net.joshuad.hypnos.Track;
import net.joshuad.hypnos.audio.AudioSystem.StopReason;
import net.joshuad.hypnos.audio.decoders.*;

public class AudioPlayer {

	public enum PlayState {
		STOPPED, PAUSED, PLAYING;
	}
	
	private static final Logger LOGGER = Logger.getLogger( AudioPlayer.class.getName() );

	public static final int NO_REQUEST = -1;
	
	boolean unpauseRequested = false;
	boolean pauseRequested = false;
	boolean stopRequested = false;
	boolean volumeErrorRequested = false;
	Track trackRequested = null;
	double seekPercentRequested = NO_REQUEST;
	double seekMSRequested = NO_REQUEST;
	double volumePercentRequested = NO_REQUEST;
	
	PlayState state = PlayState.STOPPED;
	
	AbstractDecoder decoder;
	AudioSystem controller;
	Track track;

	private double volumePercent = 1;
	Thread playerThread;
	
	public AudioPlayer( AudioSystem controller ) {
		this.controller = controller;
		
		playerThread = new Thread ( () -> {
			runPlayerThread();
		});
		
		playerThread.setName( "Audio Player Thread" );
		playerThread.setDaemon( true );
	}
	
	public void start() {
		playerThread.start();
	}
	
	public void runPlayerThread() {
		
		while ( true ) {
			try {
				if ( state != PlayState.STOPPED && stopRequested ) {
					Track thisTrack = decoder.getTrack();
					track = null;
					state = PlayState.STOPPED;
					AbstractDecoder closeMe = decoder;
					decoder = null;
					closeMe.closeAllResources();
					controller.playerStopped( thisTrack, StopReason.USER_REQUESTED );
					stopRequested = false;
				}	

				if ( trackRequested != null ) {
					
					Track currentRequest = trackRequested;
					track = currentRequest;
					trackRequested = null;
					
					if ( decoder != null ) {
						decoder.closeAllResources();
					}

					try {
						decoder = getDecoder ( currentRequest );
						
					} catch ( Exception e ) {
						LOGGER.log( Level.INFO, "Unable to initialize decoder for: " + currentRequest.getFilename(), e );
						decoder = null;
					}

					if ( decoder != null ) {

						track = currentRequest;
						volumePercentRequested = volumePercent;
						controller.playerStarted( currentRequest );
						updateTrackPosition();
						state = PlayState.PLAYING;
						
					} else {
						stopRequested = false;
						state = PlayState.STOPPED;
						controller.playerStopped( null, StopReason.UNABLE_TO_START_TRACK );
					}
					
				} 

				if ( volumeErrorRequested ) {
					state = PlayState.PAUSED;
					controller.playerPaused();			
					volumeErrorRequested = false;
					Hypnos.warnUserVolumeNotSet();
				}
				
				if ( state != PlayState.STOPPED ) {

					if ( pauseRequested ) {
						pauseRequested = false;
						state = PlayState.PAUSED;
						controller.playerPaused();
						decoder.pause();
					}

					if ( unpauseRequested ) {
						unpauseRequested = false;
						state = PlayState.PLAYING;
						controller.playerUnpaused();
						decoder.unpause();
					}
					
					if ( seekPercentRequested != NO_REQUEST ) {
						decoder.seekTo ( seekPercentRequested );
						setDecoderVolume( volumePercent );
						updateTrackPosition();
						seekPercentRequested = NO_REQUEST;
					}

					if ( seekMSRequested != NO_REQUEST ) {
						decoder.seekTo ( seekMSRequested / (double)( track.getLengthS() * 1000 ) );
						setDecoderVolume( volumePercent );
						updateTrackPosition();
						seekMSRequested = NO_REQUEST;
					}

					if ( volumePercentRequested != NO_REQUEST ) {
						setDecoderVolume ( volumePercentRequested );
						controller.volumeChanged ( volumePercentRequested );
						volumePercent = decoder.getVolumePercent();
						volumePercentRequested = NO_REQUEST;
					}

					if ( state == PlayState.PLAYING ) {

						boolean finishedPlaying = decoder.playSingleFrame();
						updateTrackPosition();
						
						if ( finishedPlaying ) {
							Track thisTrack = decoder.getTrack();
							decoder.closeAllResources();
							decoder = null;
							state = PlayState.STOPPED;
							controller.playerTrackPositionChanged( thisTrack, (int)getPositionMS(), (int)getPositionMS() );
							controller.playerStopped( thisTrack, StopReason.TRACK_FINISHED );
						}			

					} else {
						try {
							Thread.sleep( 10 );
						} catch ( InterruptedException e ) {
							LOGGER.fine( "Interrupted while paused." );
						}
					}
					
				} else { 
					try {
						Thread.sleep( 20 );
					} catch ( InterruptedException e ) {
						LOGGER.fine ( "Interrupted while stopped" );
					}
				}
				
			} catch ( Exception e ) {
				//Note: We catch everything here because this loop has to keep running no matter what, or the program can't play music. 
				LOGGER.log( Level.WARNING, "Exception in AudioPlayer Loop.", e );
				requestStop(); //TODO: this sets the wrong reason for stopping, but w/e
			}
		}
	}
	
	public void requestUnpause() {
		unpauseRequested = true;
	}
	
	public void requestPause() {
		pauseRequested = true;
	}
	
	public void requestTogglePause() {
		switch ( state ) {
			case PAUSED:
				requestUnpause();
				break;
				
			case PLAYING:
				requestPause();
				break;
				
			case STOPPED: //Fallthrough. 
			default:
				//Do nothing. 
				break;
		}
	}
	
	public void requestStop () {
		stopRequested = true;
		trackRequested = null;
	}
	
	public void requestPlayTrack ( Track track, boolean startPaused ) {
		trackRequested = track;
		pauseRequested = startPaused;
		stopRequested = false;
	}
	
	public void requestSeekPercent ( double seekPercent ) {
		this.seekPercentRequested = seekPercent;
	}

	public void requestIncrementMS ( int diffMS ) {
		if ( seekMSRequested == NO_REQUEST ) {
			seekMSRequested = this.getPositionMS() + diffMS;
		} else {
			seekMSRequested += diffMS;
		}
	}

	public void requestSeekMS ( long seekMS ) {
		if ( seekMS < 0 ) {
			LOGGER.info( "Requested a seek to a negative location. Seeking to 0 instead." );
			seekMS = 0;
		}

		this.seekMSRequested = seekMS;
	}
	
	public void requestVolumePercent ( double volumePercent ) {
		if ( volumePercent < 0 ) {
			LOGGER.info( "Volume requested to be turned down below 0. Setting to 0 instead." );
			volumePercent = 0;
		} 
		
		if ( volumePercent > 1 ) {
			LOGGER.info( "Volume requested to be more than 1 (i.e. 100%). Setting to 1 instead." );
			volumePercent = 1;
		}
		
		this.volumePercentRequested = volumePercent;
		this.volumePercent = volumePercent;
		
		if ( this.decoder == null ) {
			controller.volumeChanged ( volumePercent );
		}
	}
		
	public Track getTrack() {
		if ( trackRequested != null ) { 
			return trackRequested;
		} else {
			return track;
		}
	}
	
	public PlayState getState() {
		if ( isStopped() ) return PlayState.STOPPED;
		else if ( isPaused() ) return PlayState.PAUSED;
		else return PlayState.PLAYING;
	}
	
	public boolean isStopped () {
		if ( trackRequested != null ) return false;
		else if ( state == PlayState.STOPPED ) return true;
		else if ( stopRequested ) return true;
		else return false;
	}
	
	public boolean isPaused () {
		if ( trackRequested != null ) return false;
		else if ( stopRequested ) return false;
		else if ( unpauseRequested ) return false;
		else if ( state == PlayState.PAUSED ) return true;
		else if ( pauseRequested ) return true;
		else return false; 
	}
	
	public boolean isPlaying() {
		return ( !isStopped() && !isPaused() );
	}
	
	public long getPositionMS () {
		if ( decoder == null ) {
			return 0;
		} else { 
			return decoder.getPositionMS();
		}
	}
		
	private AbstractDecoder getDecoder ( Track track ) {
		
		if ( track == null ) {
			LOGGER.info ( "Asked to play null track, ignoring." );
			return null;
		}
		
		if ( !Files.exists( track.getPath() ) ) {
			LOGGER.info ( "Track file does not exist: " + track.getPath().toString() );
			return null;
		}
		
		AbstractDecoder decoder = null;
				
		switch ( track.getFormat() ) {
			case FLAC:
				try {
					decoder = new FlacDecoder ( track );
				} catch ( Exception e ) {
					LOGGER.info("Using backup flac decoder for: " + track.getPath() );
					decoder = new BackupFlacDecoder ( track );
				}
				break;
				
			case MP3:
				decoder = new MP3Decoder ( track );
				break;
				
			case M4A:
				decoder = new MP4Decoder ( track );
				break;
				
			//	Currently Disabled because the sample rates reported in the files as I read them aren't accurate
			/*case M4B:
				decoder = new MP4Decoder ( track );
				break;
			*/
				
			case OGG:
				decoder = new OggDecoder ( track );
				break;
				
			case WAV:
				decoder = new WavDecoder ( track );
				break;
			
			case UNKNOWN:
			default:
				LOGGER.info( "Unrecognized file format. Unable to initialize decoder." );
				break;
		}
		
		return decoder;
	}
	
	private void setDecoderVolume ( double volumePercent ) { 
		
		try {
			decoder.setVolumePercent( volumePercent );
			
		} catch ( IllegalArgumentException e ) {
			if ( volumePercent < 1 ) {		
				volumeErrorRequested = true;
			}
		}
	}
	
	public boolean volumeChangeSupported() {
		if ( decoder != null ) {
			return decoder.volumeChangeSupported();
			
		} else {
			return true;
		}
	}
	
	void updateTrackPosition() {
		int timeElapsedMS;
		int lengthMS = track.getLengthS() * 1000;
		
		if ( seekPercentRequested == NO_REQUEST ) {
			double positionPercent = (double) getPositionMS() / ( (double) track.getLengthS() * 1000 );
			timeElapsedMS = (int)( lengthMS * positionPercent );
		} else {
			timeElapsedMS = (int)( lengthMS * seekPercentRequested );
		}
		
		controller.playerTrackPositionChanged ( track, timeElapsedMS, lengthMS );
	}

	public double getVolumePercent () {
		if ( volumePercentRequested != NO_REQUEST ) {
			return volumePercentRequested;
			
		} else if ( decoder != null ) {
			return decoder.getVolumePercent();
			
		} else {
			return volumePercent;
		}
	}
}
