package net.joshuad.hypnos;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.nio.file.WatchKey;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import net.joshuad.hypnos.Hypnos.OS;

public class Library {

	private static final Logger LOGGER = Logger.getLogger( Library.class.getName() );
	
	public static final int SAVE_ALL_INTERVAL = 10000;
		
	private WatchService watcher;
    private final HashMap<WatchKey,Path> keys = new HashMap <WatchKey,Path> ();
    
    private InitialScanFileVisitor fileWalker = null; //YOU MUST SET THIS TO NULL AFTER IT WALKS
    
	private Thread loaderThread;
	private final ModifiedFileUpdaterThread modifiedFileDelayedUpdater;
	
	// These are all three representations of the same data. Add stuff to the
	// Observable List, the other two can't accept add.
	final ObservableList <Album> albums = FXCollections.observableArrayList( new ArrayList <Album>() );
	final FilteredList <Album> albumsFiltered = new FilteredList <Album>( albums, p -> true );
	final SortedList <Album> albumsSorted = new SortedList <Album>( albumsFiltered );
    
	final ObservableList <Track> tracks = FXCollections.observableArrayList( new ArrayList <Track>() );
	final FilteredList <Track> tracksFiltered = new FilteredList <Track>( tracks, p -> true );
	final SortedList <Track> tracksSorted = new SortedList <Track>( tracksFiltered );
    
	final ObservableList <Playlist> playlists = FXCollections.observableArrayList( new ArrayList <Playlist>() );
	final FilteredList <Playlist> playlistsFiltered = new FilteredList <Playlist>( playlists, p -> true );
	final SortedList <Playlist> playlistsSorted = new SortedList <Playlist>( playlistsFiltered );
	
	final ObservableList <TagError> tagErrors = FXCollections.observableArrayList( new ArrayList <TagError>() );
	final FilteredList <TagError> tagErrorsFiltered = new FilteredList <TagError>( tagErrors, p -> true );
	final SortedList <TagError> tagErrorsSorted = new SortedList <TagError>( tagErrorsFiltered );
	
	final ObservableList <MusicSearchLocation> musicSourceLocations = FXCollections.observableArrayList();
	private boolean sourcesHaveUnsavedData = false;
	
	Vector <Album> albumsToAdd = new Vector <Album>();
	Vector <Album> albumsToRemove = new Vector <Album>();
	Vector <Album> albumsToUpdate = new Vector <Album>();
    
	Vector <Track> tracksToAdd = new Vector <Track>();
	Vector <Track> tracksToRemove = new Vector <Track>();
	Vector <Track> tracksToUpdate = new Vector <Track>();
    
	Vector <Playlist> playlistsToAdd = new Vector <Playlist>();
	Vector <Playlist> playlistsToRemove = new Vector <Playlist>();
	Vector <Playlist> playlistsToUpdate = new Vector <Playlist>();

	private Vector <MusicSearchLocation> sourceToAdd = new Vector <MusicSearchLocation>();
	private Vector <MusicSearchLocation> sourceToRemove = new Vector <MusicSearchLocation>();
	private Vector <MusicSearchLocation> sourceToUpdate = new Vector <MusicSearchLocation>();
		
	private boolean purgeMissing = true, purgeOrphans = true;
	
	private int loaderSleepTimeMS = 50;
	
	public Library() {
		modifiedFileDelayedUpdater = new ModifiedFileUpdaterThread( this );
		if ( watcher == null ) {
			try {
				watcher = FileSystems.getDefault().newWatchService();
				modifiedFileDelayedUpdater.setDaemon( true );
				modifiedFileDelayedUpdater.start();
			} catch ( IOException e ) {
				String message = "Unable to initialize file watcher, changes to file system while running won't be detected";
				LOGGER.log( Level.WARNING, message );
				Hypnos.getUI().notifyUserError( message );
			}
		}
		
		musicSourceLocations.addListener( (ListChangeListener.Change<? extends MusicSearchLocation> change) -> {
			sourcesHaveUnsavedData = true;			
		});
	}
	
	public boolean sourcesHasUnsavedData() {
		return sourcesHaveUnsavedData;
	}
	
	public void setSourcesHasUnsavedData( boolean b ) {
		sourcesHaveUnsavedData = b;
	}

	public void startLoader( Persister persister ) {
				
		loaderThread = new Thread ( new Runnable() {

			long lastSaveTime = System.currentTimeMillis();
			boolean albumTrackDataChangedSinceLastSave = false;
			
			@Override
			public void run() {
				int orphanCounter = 0;
				int missingCounter = 0;
				while ( true ) {
					
					for ( MusicSearchLocation location : musicSourceLocations ) {
						location.recheckValidity();
					}
					
					if ( !sourceToRemove.isEmpty() ) {
						removeOneSource();
						albumTrackDataChangedSinceLastSave = true;
						
					} else if ( !sourceToAdd.isEmpty() ) {
						loadOneSource();
						albumTrackDataChangedSinceLastSave = true;
						
					} else if ( !sourceToUpdate.isEmpty() ) {
						updateOneSource();
						
					} else if ( purgeMissing && albumsToRemove.isEmpty() && tracksToRemove.isEmpty() ) {
						boolean missingFiles = purgeMissingFiles();
						purgeMissing = false;
						
						if ( missingFiles ) {
							albumTrackDataChangedSinceLastSave = true;
						}
						
					} else if ( purgeOrphans && albumsToRemove.isEmpty() && tracksToRemove.isEmpty() ) {
						boolean orphans = purgeOrphans();
						purgeOrphans = false;
						
						if ( orphans ) {
							albumTrackDataChangedSinceLastSave = true;
						}
						
					} else {
						processWatcherEvents(); //Note: this blocks for 250ms, see function
					}

					//TODO: This really doesn't belong here. 
					if ( System.currentTimeMillis() - lastSaveTime > SAVE_ALL_INTERVAL ) {
						if ( albumTrackDataChangedSinceLastSave ) {
							persister.saveAlbumsAndTracks();
							albumTrackDataChangedSinceLastSave = false;
						}
						
						persister.saveSources();
						persister.saveCurrentList();
						persister.saveQueue();
						persister.saveHistory();
						persister.saveLibraryPlaylists();
						persister.saveHotkeys();

						//there's no easy way to check if settings changed right now, so we just don't bother
						//it's not a big deal if they are lost. 
						//persister.saveSettings(); 
						
						lastSaveTime = System.currentTimeMillis();
					}
					
					try {
						Thread.sleep( loaderSleepTimeMS );
						orphanCounter++;
						missingCounter++;
						
						if ( orphanCounter > 1000 ) {
							//TODO: This is a hack because things aren't setup right. Get all these threads and arraylists coordinating properly. 
							purgeOrphans = true;
							orphanCounter = 0;
						}
						
						if ( missingCounter > 50 ) {
							//TODO: This is a hack because things aren't setup right. Get all these threads and arraylists coordinating properly. 
							purgeMissing = true;
							missingCounter = 0;
						}
						
					} catch ( InterruptedException e ) {
						LOGGER.fine ( "Sleep interupted during wait period." );
					}
				}
			}
		});
		
		loaderThread.setName( "Libary Loader" );
		loaderThread.setDaemon( true );
		loaderThread.start();
	}
	
	public void setLoaderSleepTimeMS ( int timeMS ) {
		this.loaderSleepTimeMS = timeMS;
	}
	
	public void requestUpdateSources ( List<MusicSearchLocation> locations ) {
		sourceToUpdate.addAll( locations );
		for ( MusicSearchLocation location : locations ) {
			musicSourceLocations.add( location );
		}
	}
	
	public void requestAddSources ( List<MusicSearchLocation> locations ) {
		for ( MusicSearchLocation location : locations ) {
			Path path = location.getPath();
			if ( path != null ) {
				path = path.toAbsolutePath();
				
				if ( path.toFile().exists() && path.toFile().isDirectory() ) {

					boolean addSelectedPathToList = true;
					for ( MusicSearchLocation alreadyAddedLocation : musicSourceLocations ) {
						try {
							if ( Files.isSameFile( path, alreadyAddedLocation.getPath() ) ) {
								addSelectedPathToList = false;
							}
						} catch ( IOException e1 ) {} // Do nothing, assume they don't match.
					}
					
					if ( addSelectedPathToList ) {
						sourceToAdd.add ( location );
						musicSourceLocations.add ( location );
						if ( fileWalker != null ) {
							fileWalker.interrupt();
						}
					}
				}
			}
		}
	}	
	
	public void requestRemoveSources ( List<MusicSearchLocation> locations ) {
		sourceToRemove.addAll ( locations );
		
		if ( fileWalker != null ) {
			fileWalker.interrupt();
		}
		
		ArrayList<MusicSearchLocation> locationsCopy = new ArrayList<> ( locations );
		
		for ( MusicSearchLocation path : locationsCopy ) {
			musicSourceLocations.remove( path );
		}
	}
	
	public void requestUpdate ( MusicSearchLocation path ) {
		sourceToUpdate.add( path );
	}
	
	public void requestUpdateSource ( MusicSearchLocation path ) {
		requestUpdateSources( Arrays.asList( path ) );
	}

	public void requestAddSource ( MusicSearchLocation path ) {
		requestAddSources( Arrays.asList( path ) );
	}
	
	public void requestRemoveSource ( MusicSearchLocation path ) {
		requestRemoveSources ( Arrays.asList( path ) );
	}
	
	public boolean containsAlbum ( Album album ) {
		if ( albumsToRemove.contains ( album ) ) return false;
		else if ( albums.contains( album ) ) return true;
		else if ( albumsToAdd.contains( album ) ) return true;
		else return false;
	}
	
	public void addAlbums ( ArrayList<Album> albums ) {
		for ( Album album : albums ) {
			addAlbum ( album );
		}
	}
	
	public void addAlbum ( Album album ) {
		if ( containsAlbum( album ) ) {
			albumsToUpdate.add ( album ); 
		} else {
			albumsToAdd.add ( album );
		}
	
		addTracks( album.getTracks() );
	}
	
	void removeAlbums ( ArrayList<Album> albums ) {
		for ( Album album : albums ) {
			removeAlbum ( album );
		}
	}
	
	void removeAlbum ( Album album ) {
		if ( !albumsToRemove.contains( album ) ) {
			albumsToRemove.add ( album );
			removeTracks ( album.tracks );
		}
	}
	
	
	public boolean containsTrack ( Track track ) {
		if ( tracksToRemove.contains ( track ) ) return false;
		else if ( tracks.contains( track ) ) return true;
		else if ( tracksToAdd.contains( track ) ) return true;
		else return false;
	}
	
	public void addTracks ( ArrayList<Track> tracks ) {
		for ( Track track : tracks ) {
			addTrack ( track );
		}
	}
	
	public void addTrack ( Track track ) {
		if ( containsTrack( track ) ) {
			tracksToUpdate.add ( track );
		} else {
			tracksToAdd.add ( track );
		}
	}
	
	void removeTracks ( ArrayList<Track> tracks ) {
		for ( Track track : tracks ) {
			removeTrack ( track );
		}
	}
	
	void removeTrack ( Track track ) {
		if ( !tracksToRemove.contains( track ) ) {
			tracksToRemove.add( track );
		}
	}
	
	public void addPlaylists ( ArrayList<Playlist> playlists ) {
		for ( Playlist playlist : playlists ) {
			addPlaylist ( playlist );
		}
	}
	
	public void addPlaylist ( Playlist playlist ) {
		playlistsToAdd.add( playlist );
	}
	
	public void removePlaylist ( Playlist playlist ) {
		playlistsToRemove.add( playlist );
	}
	
	public void removePlaylists ( List <Playlist> playlists ) {
		for ( Playlist playlist : playlists ) {
			removePlaylist ( playlist );
		}
	}
	
	private void removeOneSource() {

		while ( fileWalker != null ) {
			try {
				Thread.sleep( 20 );
			} catch ( InterruptedException e ) {
				LOGGER.fine ( "Interrupted while waiting for filewalker to terminate." );
			}
		}
		
		MusicSearchLocation removeMeLocation = sourceToRemove.remove( 0 );
		
		String message = "Removing " + removeMeLocation.getPath().toString() + "...";
		
		Hypnos.getUI().setLibraryLoaderStatus( message, 0 );

		sourceToUpdate.remove( removeMeLocation );
		sourceToAdd.remove( removeMeLocation );
		musicSourceLocations.remove( removeMeLocation );
		
		if ( musicSourceLocations.size() == 0 ) {

			Platform.runLater( () -> {
				synchronized ( albumsToAdd ) {
					albumsToAdd.clear();
				}
				
				synchronized ( albumsToRemove ) {
					albumsToRemove.clear();
				}
				
				synchronized ( albumsToUpdate ) {
					albumsToRemove.clear();
				}
				
				synchronized ( albums ) {
					albums.clear();
				}
				
				synchronized ( tracksToAdd ) {
					tracksToAdd.clear();
				}
				
				synchronized ( tracksToRemove ) {
					tracksToRemove.clear();
				}
				
				synchronized ( tracksToUpdate ) {
					tracksToUpdate.clear();
				}
				
				synchronized ( tracks ) {
					tracks.clear();
				}
				
				synchronized ( tagErrors ) {
					tagErrors.clear();
				}
				
				Hypnos.getUI().getLibraryPane().updateLibraryListPlaceholder(); //REFACTOR: This desn't really belong here. 
			});
			
		} else {

			ArrayList <Album> albumsCopy = new ArrayList <Album> ( albums );
			ArrayList <Track> tracksCopy = new ArrayList <Track> ( tracks );
			
			int totalCount = albumsCopy.size() + tracksCopy.size();
			
			int countDone = 0;
			for ( Album album : albumsCopy ) {
				if ( album.getPath().toAbsolutePath().startsWith( removeMeLocation.getPath() ) ) {
					removeAlbum ( album );
				}
				countDone++;
				Hypnos.getUI().setLibraryLoaderStatus( message, countDone / (double)totalCount );
			}
			
			for ( Track track : tracksCopy ) {
				if ( track.getPath().toAbsolutePath().startsWith( removeMeLocation.getPath() ) ) {
					removeTrack( track );
				}
				countDone++;
				Hypnos.getUI().setLibraryLoaderStatus( message, countDone / (double)totalCount );
			}
		}
		
		Hypnos.getUI().setLibraryLoaderStatusToStandby();
	}
	
	private long getDirectoryCount ( Path dir ) throws IOException {
	    return Files.walk(dir).parallel().filter(p -> p.toFile().isDirectory()).count();
	}
	
	private void loadOneSource() {
		MusicSearchLocation selectedLocation = sourceToAdd.get( 0 );
		
		long directoryCount = -1;
		
		try {
			directoryCount = getDirectoryCount ( selectedLocation.getPath() );
		} catch ( IOException e ) {
			LOGGER.log( Level.INFO, "Unable to count directories, library loader progress bar will not function: " 
				+ selectedLocation.getPath().toString(), e );
		}
		
		fileWalker = new InitialScanFileVisitor( this, "Scanning " + selectedLocation.getPath().toString() + "...", directoryCount );
		try {

			Files.walkFileTree ( 
					selectedLocation.getPath(), 
				EnumSet.of( FileVisitOption.FOLLOW_LINKS ), 
				Integer.MAX_VALUE,
				fileWalker
			);
			
			if ( !fileWalker.getWalkInterrupted() ) {
				sourceToAdd.remove( selectedLocation );
				watcherRegisterAll ( selectedLocation );
			}
			
		} catch ( Exception e ) {
			LOGGER.log( Level.WARNING, "Unable to load some files in path: " + selectedLocation.getPath().toString(), e );
		}
		fileWalker = null;

		Hypnos.getUI().setLibraryLoaderStatusToStandby();
	}
	
	private void updateOneSource() {
		MusicSearchLocation selectedLocation = sourceToUpdate.get( 0 );
		
		long directoryCount = -1;
		
		try {
			directoryCount = getDirectoryCount ( selectedLocation.getPath() );
		} catch ( IOException e ) {
			LOGGER.log( Level.INFO, "Unable to count directories, library loader progress bar will not function: " 
				+ selectedLocation.getPath().toString(), e );
		}
		
		fileWalker = new InitialScanFileVisitor( this, "Rescanning " + 
			selectedLocation.getPath().toString() + "...", directoryCount );
		
		try {
			Files.walkFileTree ( 
				selectedLocation.getPath(), 
				EnumSet.of( FileVisitOption.FOLLOW_LINKS ), 
				Integer.MAX_VALUE,
				fileWalker
			);
			
			if ( !fileWalker.getWalkInterrupted() ) {
				sourceToUpdate.remove( selectedLocation );
			}
			
		} catch ( Exception e ) {
			LOGGER.log( Level.WARNING, "Unable to load some files in path: " + selectedLocation.getPath().toString(), e );
		}
		
		fileWalker = null;
		
		watcherRegisterAll ( selectedLocation );
		
		Hypnos.getUI().setLibraryLoaderStatusToStandby();
	}
	
	private boolean purgeMissingFiles() {
		boolean changed = false;
		ArrayList <Album> albumsCopy = new ArrayList <Album> ( albums );
		for ( Album album : albumsCopy ) {
			if ( !Files.exists( album.getPath() ) || !Files.isDirectory( album.getPath() ) ) {
				removeAlbum ( album );
				changed = true;
			}
		}
		
		ArrayList <Track> tracksCopy = new ArrayList <Track> ( tracks );
		for ( Track track : tracksCopy ) {
			if ( !Files.exists( track.getPath() ) || !Files.isRegularFile( track.getPath() ) ) {
				removeTrack ( track );
				changed = true;
			}
		}
		return changed;
	}
	
	private boolean purgeOrphans () {
		boolean changed = false;
		ArrayList <Album> albumsCopy = new ArrayList <Album> ( albums );
		for ( Album album : albumsCopy ) {
			boolean hasParent = false;
			for ( MusicSearchLocation sourceLocation : musicSourceLocations ) {
				if ( album.getPath().toAbsolutePath().startsWith( sourceLocation.getPath() ) ) {
					hasParent = true;
				}
			}
			
			if ( !hasParent ) {
				removeAlbum ( album );
				ArrayList <Track> albumTracks = album.getTracks();
				if ( albumTracks != null ) {
					tracksToRemove.addAll( albumTracks );
					changed = true;
				}
			}
		}
		
		ArrayList <Track> tracksCopy = new ArrayList <Track> ( tracks );
		for ( Track track : tracksCopy ) {
			boolean hasParent = false;
			for ( MusicSearchLocation sourceLocation : musicSourceLocations ) {
				if ( track.getPath().toAbsolutePath().startsWith( sourceLocation.getPath() ) ) {
					hasParent = true;
				}
			}
			
			if ( !hasParent ) {
				tracksToRemove.add( track );
				changed = true;
			}
		}
		return changed;
	}
	
	private void watcherRegisterAll ( final MusicSearchLocation start ) {
		/* Note: in my testing, this takes less than a few seconds to run, depending on folder count */ 
		try {
			Files.walkFileTree( 
				start.getPath(), 
				EnumSet.of( FileVisitOption.FOLLOW_LINKS ), 
				Integer.MAX_VALUE,
				new SimpleFileVisitor <Path>() {
					@Override
					public FileVisitResult preVisitDirectory ( Path dir, BasicFileAttributes attrs ) throws IOException {
						WatchKey key = dir.register( 
							watcher, 
							StandardWatchEventKinds.ENTRY_CREATE, 
							StandardWatchEventKinds.ENTRY_DELETE, 
							StandardWatchEventKinds.ENTRY_MODIFY 
						);
						
						keys.put( key, dir );
						return FileVisitResult.CONTINUE;
					}
				}
			);
		
		} catch ( IOException e ) {
			if ( Hypnos.getOS() == OS.NIX && e.getMessage().matches( ".*inotify.*" ) ) {
				Hypnos.getUI().notifyUserLinuxInotifyIssue();
				LOGGER.log( Level.INFO, e.getMessage() + "\nUnable to watch directory for changes: " + start.getPath().toString() +
					"\nSee here for how to fix this error on linux: " + HypnosURLS.HELP_INOTIFY 
				);
				start.setHadInotifyError( true );
			} else {
				LOGGER.log( Level.INFO, e.getMessage() + "\nUnable to watch directory for changes: " + start.getPath().toString(), e );
			}
		}
	}
	
	private boolean processWatcherEvents () {
		WatchKey key;
		try {
			key = watcher.poll( 250, TimeUnit.MILLISECONDS );
		} catch ( InterruptedException e ) {
			return false;
		}
		
		Path directory = keys.get( key );
		if ( directory == null ) {
			return false;
		}
		
		System.out.println ( "Directory: " + directory );

		for ( WatchEvent <?> event : key.pollEvents() ) {
			WatchEvent.Kind<?> eventKind = event.kind();

			WatchEvent <Path> watchEvent = (WatchEvent<Path>)event;
			Path child = directory.resolve( watchEvent.context() );

			if ( eventKind == StandardWatchEventKinds.ENTRY_CREATE ) {
				if ( Files.isDirectory( child ) ) {
					sourceToAdd.add( new MusicSearchLocation ( child ) );  
				} else {
					sourceToAdd.add( new MusicSearchLocation ( child.getParent() ) );
				}
				
			} else if ( eventKind == StandardWatchEventKinds.ENTRY_DELETE ) {
				purgeOrphans();
				purgeMissingFiles();
				
			} else if ( eventKind == StandardWatchEventKinds.ENTRY_MODIFY ) {
				if ( Files.isDirectory( child ) ) {
					modifiedFileDelayedUpdater.addUpdateItem( new MusicSearchLocation ( child ) );
				} else {
					modifiedFileDelayedUpdater.addUpdateItem( new MusicSearchLocation ( child ) );
				}
			
			} else if ( eventKind == StandardWatchEventKinds.OVERFLOW ) {
				for ( MusicSearchLocation location : musicSourceLocations ) {
					sourceToUpdate.add( location );
				}
			}

			boolean valid = key.reset();
			if ( !valid ) {
				keys.remove( key );
			}
		}
		
		return true;
	}
	
	public String getUniquePlaylistName() {
		return getUniquePlaylistName ( "New Playlist" );
	}
	
	public String getUniquePlaylistName( String base ) {
		String name = base;
		
		int number = 0;
		
		while ( true ) {
			
			boolean foundMatch = false;
			for ( Playlist playlist : playlists ) {
				if ( playlist.getName().toLowerCase().equals( name.toLowerCase() ) ) {
					foundMatch = true;
				}
			}
			
			if ( foundMatch ) {
				number++;
				name = base + " " + number;
			} else {
				break;
			}
		}
		
		return name;
	}

	public SortedList <Playlist> getPlaylistSorted () {
		return playlistsSorted;
	}

	public ObservableList <Playlist> getPlaylists () {
		return playlists;
	}

	public ObservableList <MusicSearchLocation> getMusicSourcePaths () {
		return musicSourceLocations;
	}

	public FilteredList <Playlist> getPlaylistsFiltered () {
		return playlistsFiltered;
	}

	public FilteredList <Track> getTracksFiltered () {
		return tracksFiltered;
	}
	
	public FilteredList <Album> getAlbumsFiltered() {
		return albumsFiltered;
	}
	
	public SortedList <Album> getAlbumsSorted() {
		return albumsSorted;
	}
	
	public ObservableList <Album> getAlbums () {
		return albums;
	}
	
	public ObservableList <Track> getTracks () {
		return tracks;
	}

	public SortedList <Track> getTracksSorted () {
		return tracksSorted;
	}
	
	public SortedList <TagError> getTagErrorsSorted () {
		return tagErrorsSorted;
	}
}

class ModifiedFileUpdaterThread extends Thread {
	private final Logger LOGGER = Logger.getLogger( ModifiedFileUpdaterThread.class.getName() );
	public final int DELAY_LENGTH_MS = 1000; 
	public int counter = DELAY_LENGTH_MS;
	
	Vector <MusicSearchLocation> updateItems = new Vector <MusicSearchLocation> ();
	Library library;
	
	public ModifiedFileUpdaterThread ( Library library ) {
		this.library = library;
	}
	
	public void run() {
		while ( true ) {
			long startSleepTime = System.currentTimeMillis();
			try { 
				Thread.sleep ( 50 ); 
			} catch ( InterruptedException e ) {
				LOGGER.log ( Level.FINE, "Sleep interupted during wait period." );
			}

			long sleepTime = System.currentTimeMillis() - startSleepTime;
			
			if ( counter > 0 ) {
				counter -= sleepTime; 
			} else {
				Vector <MusicSearchLocation> copyUpdateItems = new Vector<MusicSearchLocation> ( updateItems );
				for ( MusicSearchLocation location : copyUpdateItems ) {
					library.requestUpdate ( location );
					updateItems.remove( location );
				}
			}
		}
	}
	
	public void addUpdateItem ( MusicSearchLocation location ) {
		counter = DELAY_LENGTH_MS;
		if ( !updateItems.contains( location ) ) {
			updateItems.add ( location );
		}
	}
};
