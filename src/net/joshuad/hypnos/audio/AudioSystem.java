package net.joshuad.hypnos.audio;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Vector;
import java.util.logging.Logger;

import net.joshuad.hypnos.CurrentList;
import net.joshuad.hypnos.CurrentListTrack;
import net.joshuad.hypnos.History;
import net.joshuad.hypnos.Persister;
import net.joshuad.hypnos.Playlist;
import net.joshuad.hypnos.PreviousStack;
import net.joshuad.hypnos.Queue;
import net.joshuad.hypnos.Track;
import net.joshuad.hypnos.Persister.Setting;

public class AudioSystem {
	
	private static final Logger LOGGER = Logger.getLogger( AudioSystem.class.getName() );
	
	public enum StopReason {
		TRACK_FINISHED,
		USER_REQUESTED,
		END_OF_CURRENT_LIST,
		EMPTY_LIST,
		WRITING_TO_TAG,
		ERROR
	}

	public enum ShuffleMode {
		SEQUENTIAL ( "⇉" ), SHUFFLE ( "🔀" );
		String symbol;
		ShuffleMode ( String symbol ) { this.symbol = symbol; }
		public String getSymbol () { return symbol; }
	}

	public enum RepeatMode {
		PLAY_ONCE ( "⇥" ), REPEAT ( "🔁" ), REPEAT_ONE_TRACK ( "🔂" );
		String symbol;
		RepeatMode ( String symbol ) { this.symbol = symbol; }
		public String getSymbol () { return symbol; }
	}
	
	private ShuffleMode shuffleMode = ShuffleMode.SEQUENTIAL;
	private RepeatMode repeatMode = RepeatMode.PLAY_ONCE;
	
	private Vector<PlayerListener> playerListeners = new Vector<PlayerListener> ();
	
	private Random randomGenerator = new Random();
	
	private int shuffleTracksPlayedCounter = 0;
	
	private final AudioPlayer player;
	private final Queue queue;
	private final History history; 
	private final PreviousStack previousStack;
	private final CurrentList currentList;
	
	private Double unmutedVolume = null;
	
	public AudioSystem () {
		player = new AudioPlayer ( this );
		queue = new Queue();
		history = new History();
		previousStack = new PreviousStack();
		currentList = new CurrentList( this, queue );
	}
	
	public void unpause () {
		player.requestUnpause();
	}
	
	public void pause () {
		player.requestPause();
	}
	
	public void togglePause () {
		player.requestTogglePause();
	}
	
	public void play () {
		switch ( player.getState() ) {
			case PAUSED:
				player.requestUnpause();
				break;
			case PLAYING:
				player.requestPlayTrack( player.getTrack(), false );
				break;
			case STOPPED:
				next( false );
				break;
		}
	}
	
	public void stop ( StopReason reason ) {
		//if ( player != null ) { Can't we assume player isn't null?  2017/09/20 I removed this. JDH
			Track track = player.getTrack();
			if ( track instanceof CurrentListTrack ) ((CurrentListTrack)track).setIsCurrentTrack( false );
			player.requestStop();

			notifyListenersStopped( player.getTrack(), reason ); 
		//}
		
		shuffleTracksPlayedCounter = 0;
	}
	
	public void previous ( ) {
		boolean startPaused = player.isPaused() || player.isStopped();
		previous ( startPaused );
	}
	
	public void previous ( boolean startPaused ) {
		
		if ( player.isPlaying() || player.isPaused() ) {
			
			if ( player.getPositionMS() >= 5000 ) {
				playTrack ( player.getTrack(), player.isPaused(), false );
				return;
			}
		}		
		
		Track previousTrack = null;

		int previousStackSize = previousStack.size();
		while ( !previousStack.isEmpty() && previousTrack == null ) {
			Track candidate = previousStack.removePreviousTrack( player.getTrack() );
								
			if ( currentList.getItems().contains( candidate ) ) {
				previousTrack = candidate;
			}
		}
		
		int previousStackSizeDifference = previousStackSize - previousStack.size();
		shuffleTracksPlayedCounter -= previousStackSizeDifference;
		
		if ( previousTrack != null ) {
			playTrack ( previousTrack, startPaused, true );
			
		} else if ( repeatMode == RepeatMode.PLAY_ONCE || repeatMode == RepeatMode.REPEAT_ONE_TRACK ) {
			shuffleTracksPlayedCounter = 1;
			Track previousTrackInList = null;
			for ( CurrentListTrack track : currentList.getItems() ) {
				if ( track.getIsCurrentTrack() ) {
					if ( previousTrackInList != null ) {
						playTrack( previousTrackInList, startPaused, false );
					} else {
						playTrack( track, startPaused, false );
					}
					break;
				} else {
					previousTrackInList = track;
				}
			}
		} else if ( repeatMode == RepeatMode.REPEAT ) {
			Track previousTrackInList = null;
			for ( CurrentListTrack track : currentList.getItems() ) {
				if ( track.getIsCurrentTrack() ) {
					if ( previousTrackInList != null ) {
						playTrack( previousTrackInList, startPaused, false );
					} else {
						playTrack( currentList.getItems().get( currentList.getItems().size() - 1 ), startPaused, false );
					}
					break;
				} else {
					previousTrackInList = track;
				}
			}
		}
	}
	
	public void next() {
		boolean startPaused = player.isPaused() || player.isStopped();
		next ( startPaused );
	}
	
	public void next ( boolean startPaused ) {

		if ( queue.hasNext() ) {
			playTrack( queue.getNextTrack(), startPaused );
			
		} else if ( currentList.getItems().size() == 0 ) {
			stop ( StopReason.EMPTY_LIST );
			return;
			
		} else if ( repeatMode == RepeatMode.REPEAT_ONE_TRACK ) {
			playTrack ( history.getLastTrack() );

		} else if ( shuffleMode == ShuffleMode.SEQUENTIAL ) {
			ListIterator <CurrentListTrack> currentListIterator = currentList.getItems().listIterator();
			boolean didSomething = false;
			
			while ( currentListIterator.hasNext() ) {
				if ( currentListIterator.next().getIsCurrentTrack() ) {
					if ( currentListIterator.hasNext() ) {
						playTrack( currentListIterator.next(), startPaused );
						didSomething = true;
						
					} else if ( repeatMode == RepeatMode.PLAY_ONCE ) {
						shuffleTracksPlayedCounter = 1;
						stop( StopReason.END_OF_CURRENT_LIST );
						didSomething = true;
					
					} else if ( currentList.getItems().size() <= 0 ) {
						stop( StopReason.EMPTY_LIST );
						didSomething = true;

					} else if ( repeatMode == RepeatMode.REPEAT && currentList.getItems().size() > 0 ) {
						playTrack( currentList.getItems().get( 0 ), startPaused );
						didSomething = true;
					}
					
					break;
				}
			}
			if ( !didSomething ) {
				if ( currentList.getItems().size() > 0 ) {
					playTrack ( currentList.getItems().get( 0 ), startPaused );
				}
			}
			
		} else if ( shuffleMode == ShuffleMode.SHUFFLE ) {
			if ( repeatMode == RepeatMode.REPEAT ) {
				
				shuffleTracksPlayedCounter = 1;
				// TODO: I think there may be issues with multithreading here.
				// TODO: Ban the most recent X tracks from playing
				int currentListSize = currentList.getItems().size();
				int collisionWindowSize = currentListSize / 3; // TODO: Fine tune this amount
				int permittedRetries = 3; // TODO: fine tune this number
	
				boolean foundMatch = false;
				int retryCount = 0;
				Track playMe;
	
				List <Track> collisionWindow;
	
				if ( previousStack.size() >= collisionWindowSize ) {
					collisionWindow = previousStack.subList( 0, collisionWindowSize );
				} else {
					collisionWindow = previousStack.getData();
				}
	
				do {
					playMe = currentList.getItems().get( randomGenerator.nextInt( currentList.getItems().size() ) );
					if ( !collisionWindow.contains( playMe ) ) {
						foundMatch = true;
					} else {
						++retryCount;
					}
				} while ( !foundMatch && retryCount < permittedRetries );
	
				playTrack( playMe, startPaused );
				
			} else {
				if ( shuffleTracksPlayedCounter < currentList.getItems().size() ) {
					List <Track> alreadyPlayed = previousStack.subList( 0, shuffleTracksPlayedCounter );
					ArrayList <Track> viableTracks = new ArrayList <Track>( currentList.getItems() );
					viableTracks.removeAll( alreadyPlayed );
					Track playMe = viableTracks.get( randomGenerator.nextInt( viableTracks.size() ) );
					playTrack( playMe, startPaused );
					++shuffleTracksPlayedCounter;
				} else {
					stop( StopReason.END_OF_CURRENT_LIST );
				}
			} 
		}
	}
	
	
	public int getCurrentTrackIndex() {
		for ( int k = 0 ; k < currentList.getItems().size(); k++ ) {
			if ( currentList.getItems().get( k ).getIsCurrentTrack() ) {
				return k;
			}
		}
		
		return -1;
	}
	
	public void setShuffleMode ( ShuffleMode newMode ) {
		this.shuffleMode = newMode;
		notifyListenersShuffleModeChanged ( shuffleMode );
	}

	public ShuffleMode getShuffleMode() {
		return shuffleMode;
	}

	public void toggleShuffleMode() {
		if ( shuffleMode == ShuffleMode.SEQUENTIAL ) {
			shuffleMode = ShuffleMode.SHUFFLE;
		} else {
			shuffleMode = ShuffleMode.SEQUENTIAL;
		}
		
		notifyListenersShuffleModeChanged ( shuffleMode );
	}
	
	
	
	public void setRepeatMode ( RepeatMode newMode ) {
		this.repeatMode = newMode;
		notifyListenersRepeatModeChanged ( newMode );
	}
	
	public RepeatMode getRepeatMode() {
		return repeatMode;
	}
	
	public void toggleRepeatMode() {
		if ( repeatMode == RepeatMode.PLAY_ONCE ) {
			repeatMode = RepeatMode.REPEAT;
		} else if ( repeatMode == RepeatMode.REPEAT ) {
			repeatMode = RepeatMode.REPEAT_ONE_TRACK;
		} else {
			repeatMode = RepeatMode.PLAY_ONCE;
		}
		
		notifyListenersRepeatModeChanged ( repeatMode );
	}
	
	public History getHistory () {
		return history;
	}
	
	public Queue getQueue() {
		return queue;
	}
	
	public CurrentList getCurrentList() {
		return currentList;
	}
	
	public Track getCurrentTrack() {
		return player.getTrack();
	}

	public Playlist getCurrentPlaylist () {
		return currentList.getCurrentPlaylist();
	}
	
	public void shuffleList() {
		currentList.shuffleList( );
	}

	public void seekPercent ( double percent ) {
		player.requestSeekPercent( percent );
	}
	
	public void skipMS ( int diffMS ) {
		player.requestIncrementMS ( diffMS );
	}

	public void seekMS ( long ms ) {
		player.requestSeekMS( ms );
	}
	
	public long getPositionMS() {
		return player.getPositionMS();
	}

	public void setVolumePercent ( double percent ) {
		player.requestVolumePercent( percent );
	}

	public void decrementVolume () {
		double target = player.getVolumePercent() - .05;
		if ( target < 0 ) target = 0;
		player.requestVolumePercent( target );
	}
	
	public void incrementVolume () {
		double target = player.getVolumePercent() + .05;
		if ( target > 1 ) target = 1;
		player.requestVolumePercent( target );
	}
	
	public boolean isPlaying () {
		return player.isPlaying();
	}
	
	public boolean isPaused() {
		return player.isPaused();
	}

	public boolean isStopped () {
		return player.isStopped();
	}
	
	public boolean volumeChangeSupported() {
		return player.volumeChangeSupported();
	}
	
	
	public EnumMap <Persister.Setting, ? extends Object> getSettings () {
		EnumMap <Persister.Setting, Object> retMe = new EnumMap <Persister.Setting, Object> ( Persister.Setting.class );
		
		if ( !player.isStopped() ) {
			retMe.put ( Setting.TRACK, player.getTrack().getPath().toString() );
			retMe.put ( Setting.TRACK_POSITION, player.getPositionMS() );
			retMe.put ( Setting.TRACK_NUMBER, getCurrentTrackIndex() );
		}

		retMe.put ( Setting.SHUFFLE, getShuffleMode().toString() );
		retMe.put ( Setting.REPEAT, getRepeatMode() );
		retMe.put ( Setting.VOLUME, player.getVolumePercent() );
		
		retMe.put ( Setting.DEFAULT_SHUFFLE_TRACKS, currentList.getDefaultTrackShuffleMode() );
		retMe.put ( Setting.DEFAULT_SHUFFLE_ALBUMS, currentList.getDefaultAlbumShuffleMode() );
		retMe.put ( Setting.DEFAULT_SHUFFLE_PLAYLISTS, currentList.getDefaultPlaylistShuffleMode() );

		retMe.put ( Setting.DEFAULT_REPEAT_TRACKS, currentList.getDefaultTrackRepeatMode() );
		retMe.put ( Setting.DEFAULT_REPEAT_ALBUMS, currentList.getDefaultAlbumRepeatMode() );
		retMe.put ( Setting.DEFAULT_REPEAT_PLAYLISTS, currentList.getDefaultPlaylistRepeatMode() );
		
		return retMe;
	}
	
	
	
//Manage Listeners

	public void addPlayerListener ( PlayerListener listener ) {
		if ( listener != null ) {
			playerListeners.add( listener );
		} else {
			LOGGER.info( "Null player listener was attempted to be added, ignoring." );
		}
	}
	
	private void notifyListenersPositionChanged ( int positionMS, int lengthMS ) {
		for ( PlayerListener listener : playerListeners ) {
			listener.playerPositionChanged( positionMS, lengthMS );
		}
	}
	
	private void notifyListenersStopped ( Track track, StopReason reason ) {
		for ( PlayerListener listener : playerListeners ) {
			listener.playerStopped( track, reason );
		}
	}
	
	private void notifyListenersStarted ( Track track ) {
		for ( PlayerListener listener : playerListeners ) {
			listener.playerStarted( track );
		}
	}
	
	private void notifyListenersPaused () {
		for ( PlayerListener listener : playerListeners ) {
			listener.playerPaused();
		}
	}
	
	private void notifyListenersUnpaused () {
		for ( PlayerListener listener : playerListeners ) {
			listener.playerUnpaused( );
		}
	}
	
	private void notifyListenersVolumeChanged ( double newVolumePercent ) {
		for ( PlayerListener listener : playerListeners ) {
			listener.playerVolumeChanged( newVolumePercent );
		}
	}
	
	private void notifyListenersShuffleModeChanged ( ShuffleMode newMode ) {
		for ( PlayerListener listener : playerListeners ) {
			listener.playerShuffleModeChanged( newMode );
		}
	}
	
	private void notifyListenersRepeatModeChanged ( RepeatMode newMode ) {
		for ( PlayerListener listener : playerListeners ) {
			listener.playerRepeatModeChanged( newMode );
		}
	}
	
	
	
//TODO: Make these a listener interface, and add this object as a listener to player? 	
	
	void playerStopped ( StopReason reason ) { 
		if ( reason == StopReason.TRACK_FINISHED ) {
			next ( false );
		}
		
		notifyListenersStopped ( history.getLastTrack(), reason );
	}

	void playerPaused () {
		notifyListenersPaused();
	}

	void playerUnpaused () {
		notifyListenersUnpaused();
	}

	void volumeChanged ( double volumePercentRequested ) {
		notifyListenersVolumeChanged ( volumePercentRequested );
	}

	void playerStarted ( Track track ) {
		notifyListenersStarted( track );
	}

	void playerTrackPositionChanged ( int positionMS, int lengthMS ) {
		notifyListenersPositionChanged ( positionMS, lengthMS );
	}
	
	public void playTrack ( Track track ) {
		playTrack ( track, false );
	}
	
	public void playTrack ( Track track, boolean startPaused ) {
		playTrack ( track, startPaused, true );
	}
	                                                                 
	public void playTrack ( Track track, boolean startPaused, boolean addToPreviousNextStack ) {
		
		player.requestPlayTrack( track, startPaused );
		
		for ( CurrentListTrack listTrack : currentList.getItems() ) {
			listTrack.setIsCurrentTrack( false );
		}
		
		if ( track instanceof CurrentListTrack ) {
			((CurrentListTrack)track).setIsCurrentTrack( true );
		}
		
		if ( addToPreviousNextStack ) {
			previousStack.addToStack ( track );
		}
		
		history.trackPlayed( track );
	}
	

	public void playItems ( List <Track> items ) {
		//TODO: maybe break this into two separate functions and have the UI determine whether to set tracks or just play
		if ( items.size() == 1 ) {
			playTrack ( items.get( 0 ) );
		} else if ( items.size() > 1 ) {
			currentList.setTracks( items );
			next ( false );
		}
	}

	//Used after program loads to get everything linked back up properly. 
	public void linkQueueToCurrentList () {
		for ( CurrentListTrack track : currentList.getItems() ) {
			for ( int index : track.getQueueIndices() ) {
				if ( index < queue.size() ) {
					queue.getData().set( index - 1, track );
				} else {
					LOGGER.fine( "Current list had a queue index beyond the length of the queue. Removing." );
					track.getQueueIndices().remove( new Integer ( index ) );
				}
			}
		}
	}

	public void toggleMute () {
		if ( player.getVolumePercent() == 0 ) {
			if ( unmutedVolume != null ) {
				player.requestVolumePercent( unmutedVolume );
				unmutedVolume = null;
			} else { 
				player.requestVolumePercent( 100 );
			}
		} else {
			unmutedVolume = player.getVolumePercent();
			player.requestVolumePercent( 0 );
		}
	}

	
}







