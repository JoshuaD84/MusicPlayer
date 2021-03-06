package net.joshuad.hypnos.library;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import net.joshuad.hypnos.AlphanumComparator;
import net.joshuad.hypnos.Utils;
import net.joshuad.hypnos.audio.AudioSystem;
import net.joshuad.hypnos.fxui.FXUI;

public class Library {
	private static final Logger LOGGER = Logger.getLogger(Library.class.getName());

	public enum LoaderSpeed {
		LOW, MED, HIGH
	}

	// These are all three representations of the same data. Add stuff to the
	// Observable List, the other two can't accept add.
	private final CachedList<Track> tracks = new CachedList<>();
	private final FilteredList<Track> tracksFiltered = new FilteredList<>(tracks.getDisplayItems(), p -> true);
	private final SortedList<Track> tracksSorted = new SortedList<>(tracksFiltered);

	private final CachedList<Album> albums = new CachedList<>();
	private final FilteredList<Album> albumsFiltered = new FilteredList<>(albums.getDisplayItems(), p -> true);
	private final SortedList<Album> albumsSorted = new SortedList<>(albumsFiltered);

	private final CachedList<Artist> artists = new CachedList<>();
	private final FilteredList<Artist> artistsFiltered = new FilteredList<>(artists.getDisplayItems(), p -> true);
	private final SortedList<Artist> artistsSorted = new SortedList<>(artistsFiltered);

	private final CachedList<Playlist> playlists = new CachedList<>();
	private final FilteredList<Playlist> playlistsFiltered = new FilteredList<>(playlists.getDisplayItems(), p -> true);
	private final SortedList<Playlist> playlistsSorted = new SortedList<>(playlistsFiltered);

	private final CachedList<TagError> tagErrors = new CachedList<>();
	private final FilteredList<TagError> tagErrorsFiltered = new FilteredList<>(tagErrors.getDisplayItems(), p -> true);
	private final SortedList<TagError> tagErrorsSorted = new SortedList<>(tagErrorsFiltered);

	private final CachedList<MusicRoot> musicRoots = new CachedList<>();

	private AudioSystem audioSystem;
	private final LibraryLoader loader;
	private final DiskWatcher diskWatcher;
	private final LibraryScanLogger scanLogger = new LibraryScanLogger();
	
	private boolean dataNeedsToBeSavedToDisk = false;
	private boolean upkeepNeeded = false;
	private final Object doUpkeepFlagLock = new Object();

	public Library() {
		diskWatcher = new DiskWatcher(this, scanLogger);
		loader = new LibraryLoader(this, scanLogger);
		InvalidationListener invalidationListener = new InvalidationListener() {
			@Override
			public void invalidated(Observable arg0) {
				dataNeedsToBeSavedToDisk = true;
				synchronized (doUpkeepFlagLock) {
					upkeepNeeded = true;
				}
			}
		};
		tracks.addListenerToBase(invalidationListener);
		albums.addListenerToBase(invalidationListener);
		Thread artistGeneratorThread = new Thread() {
			@Override
			public void run() {
				while (true) {
					boolean doUpkeep = false;
					synchronized (doUpkeepFlagLock) {
						doUpkeep = upkeepNeeded;
						upkeepNeeded = false;
					}
					if (doUpkeep) {
						scanLogger.println("[Library] Changes to library were made, regenerating artist list");
						artists.getItemsCopy().setAll(generateArtists());
						relinkPlaylistsToLibrary();
						relinkCurrentListToLibrary();
					}
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						LOGGER.log(Level.INFO, "Sleep interupted during wait period.", e);
					}
				}
			}
		};
		artistGeneratorThread.setName("Artist Generator");
		artistGeneratorThread.setDaemon(true);
		artistGeneratorThread.start();
	}

	public void setUI(FXUI ui) {
		loader.setUI(ui);
		diskWatcher.setUI(ui);
	}

	public boolean dataNeedsToBeSavedToDisk() {
		return dataNeedsToBeSavedToDisk;
	}

	public SortedList<Playlist> getPlaylistsSorted() {
		return playlistsSorted;
	}

	public ObservableList<Playlist> getPlaylistData() {
		return playlists.getItemsCopy();
	}

	public ObservableList<Playlist> getPlaylistsDisplayCache() {
		return playlists.getDisplayItems();
	}

	public ObservableList<MusicRoot> getMusicRootData() {
		return musicRoots.getItemsCopy();
	}

	public ObservableList<MusicRoot> getMusicRootDisplayCache() {
		return musicRoots.getDisplayItems();
	}

	public FilteredList<Playlist> getPlaylistsFiltered() {
		return playlistsFiltered;
	}

	public SortedList<Album> getAlbumsSorted() {
		return albumsSorted;
	}

	public FilteredList<Album> getAlbumsFiltered() {
		return albumsFiltered;
	}

	public ObservableList<Track> getTrackData() {
		return tracks.getItemsCopy();
	}

	public ObservableList<Track> getTrackDisplayCache() {
		return tracks.getDisplayItems();
	}

	public ObservableList<Album> getAlbumData() {
		return albums.getItemsCopy();
	}

	public ObservableList<Album> getAlbumDisplayCache() {
		return albums.getDisplayItems();
	}

	public ObservableList<Artist> getArtistData() {
		return artists.getItemsCopy();
	}

	public ObservableList<Artist> getArtistDisplayCache() {
		return artists.getDisplayItems();
	}

	public SortedList<Track> getTracksSorted() {
		return tracksSorted;
	}

	public FilteredList<Track> getTracksFiltered() {
		return tracksFiltered;
	}

	public SortedList<Artist> getArtistsSorted() {
		return artistsSorted;
	}

	public FilteredList<Artist> getArtistsFiltered() {
		return artistsFiltered;
	}

	public SortedList<TagError> getTagErrorsSorted() {
		return tagErrorsSorted;
	}

	public void requestRescan(Path path) {
		loader.queueUpdatePath(path);
	}
	
	public void requestRescan(List<Album> albums) {
		for (Album album : albums) {
			requestRescan(album.getPath());
		}
	}

	public void setMusicRootsOnInitialLoad(List<MusicRoot> roots) {
		for (MusicRoot musicRoot : roots) {
			musicRoot.setNeedsRescan(true);
		}
		musicRoots.getItemsCopy().setAll(roots);
	}
	
	public void setDataOnInitialLoad(List<Playlist> playlists) {
		this.playlists.setDataOnInitialLoad(playlists);
	}

	public void setDataOnInitialLoad(List<Track> tracks, List<Album> albums) {
		this.tracks.setDataOnInitialLoad(tracks);
		this.albums.setDataOnInitialLoad(albums);
		this.artists.setDataOnInitialLoad(generateArtists());
		List<TagError> errors = new ArrayList<>();
		for(Track track : tracks) {
			errors.addAll(track.getTagErrors());
		}
		this.tagErrors.setDataOnInitialLoad(errors);
	}

	public void addMusicRoot(Path path) {
		for (MusicRoot root : musicRoots.getItemsCopy()) {
			if(root.getPath().equals(path)) {
				return;
			}
		}
		musicRoots.addItem(new MusicRoot(path), true);
	}

	public void removeMusicRoot(MusicRoot musicRoot) {
		loader.interruptDiskReader();
		musicRoots.remove(musicRoot, true);
		loader.setMusicRootRemoved(true);
	}

	public void removePlaylist(Playlist playlist) {
		playlists.remove(playlist, true);
	}

	public void addPlaylist(Playlist playlist) {
		playlists.addItem(playlist, true);
	}

	public String getUniquePlaylistName() {
		return getUniquePlaylistName("New Playlist");
	}

	public void startThreads() {
		loader.start();
	}

	public LibraryScanLogger getScanLogger() {
		return scanLogger;
	}

	public String getUniquePlaylistName(String base) {
		String name = base;
		int number = 0;
		while (true) {
			boolean foundMatch = false;
			for (Playlist playlist : playlists.getItemsCopy()) {
				if (playlist.getName().toLowerCase().equals(name.toLowerCase())) {
					foundMatch = true;
				}
			}
			if (foundMatch) {
				number++;
				name = base + " " + number;
			} else {
				break;
			}
		}
		return name;
	}

	private List<Artist> generateArtists() {
		List<Artist> newArtistList = new ArrayList<>();
		List<Album> libraryAlbums = new ArrayList<>(albums.getItemsCopy());
		Album[] albumArray = libraryAlbums.toArray(new Album[libraryAlbums.size()]);
		AlphanumComparator comparator = new AlphanumComparator(AlphanumComparator.CaseHandling.CASE_INSENSITIVE);
		Arrays.sort(albumArray, Comparator.comparing(Album::getAlbumArtist, comparator));
		Artist lastArtist = null;
		for (Album album : albumArray) {
			if (album.getAlbumArtist().isBlank()) {
				continue;
			}
			if (lastArtist != null && lastArtist.getName().equals(album.getAlbumArtist())) {
				lastArtist.addAlbum(album);
			} else {
				Artist artist = null;
				for (Artist test : newArtistList) {
					if (test.getName().equalsIgnoreCase(album.getAlbumArtist())) {
						artist = test;
						break;
					}
				}
				if (artist == null) {
					artist = new Artist(album.getAlbumArtist());
					newArtistList.add(artist);
				}
				artist.addAlbum(album);
				lastArtist = artist;
			}
		}
		List<Track> looseTracks = new ArrayList<>();
		for (Track track : tracks.getItemsCopy()) {
			if (track.getAlbum() == null) {
				looseTracks.add(track);
			}
		}
		Track[] trackArray = looseTracks.toArray(new Track[looseTracks.size()]);
		Arrays.sort(trackArray, Comparator.comparing(Track::getAlbumArtist, comparator));
		lastArtist = null;
		for (Track track : trackArray) {
			if (track.getAlbumArtist().isBlank())
				continue;
			if (lastArtist != null && lastArtist.getName().equals(track.getAlbumArtist())) {
				lastArtist.addLooseTrack(track);
			} else {
				Artist artist = null;
				for (Artist test : newArtistList) {
					if (test.getName().equalsIgnoreCase(track.getAlbumArtist())) {
						artist = test;
						break;
					}
				}
				if (artist == null) {
					artist = new Artist(track.getAlbumArtist());
					newArtistList.add(artist);
				}
				artist.addLooseTrack(track);
				lastArtist = artist;
			}
		}
		return newArtistList;
	}

	LibraryLoader getLoader() {
		return loader;
	}

	DiskWatcher getDiskWatcher() {
		return diskWatcher;
	}

	public void setDataNeedsToBeSavedToDisk(boolean b) {
		this.dataNeedsToBeSavedToDisk = b;
	}

	void addTrack(Track track) {
		tracks.addItem(track);
		for (TagError error : track.getTagErrors()) {
			tagErrors.addItem(error);
		}
	}

	void removeTrack(Track track) {
		tracks.remove(track);
		for (TagError error : track.getTagErrors()) {
			tagErrors.remove(error);
		}
	}

	void addAlbum(Album album) {
		albums.addItem(album);
	}

	void notAnAlbum(Path path) {
		for (Album album : albums.getItemsCopy()) {
			if (album.getPath().equals(path)) {
				albums.remove(album);
				break;
			}
		}
	}

	void removeAlbum(Album album) {
		albums.remove(album);
	}
	
	void requestRegenerateArtists() {
		upkeepNeeded = true;
	}
	
	public boolean isArtistDirectory(Path path) {
		String directoryName = Utils.prepareArtistForCompare(path.getFileName().toString());
		List<Album> albumsInPath = new ArrayList<>();
		for (Album album : albums.getItemsCopy()) {
			if (album.getPath().getParent().equals(path)) {
				albumsInPath.add(album);
			}
		}
		List<Track> tracksInPath = new ArrayList<>();
		for (Track track : tracks.getItemsCopy()) {
			if (track.getPath().getParent().equals(path) && track.getAlbum() == null) {
				tracksInPath.add(track);
			}
		}
		if(albumsInPath.size() == 0 && tracksInPath.size() == 0) {
			return false;
		}
		for (Album album : albumsInPath) {
			try {
				int matchPercent = FuzzySearch.weightedRatio(directoryName, Utils.prepareArtistForCompare(album.getAlbumArtist()));
				if (matchPercent < 90) {
					return false;
				}
			} catch (Exception e) {
				continue;
			}
		}
		for (Track track : tracksInPath) {
			try {
				int matchPercent = FuzzySearch.weightedRatio(directoryName, Utils.prepareArtistForCompare(track.getAlbumArtist()));
				if (matchPercent < 90) {
					return false;
				}
			} catch (Exception e) {
				continue;
			}
		}
		return true;
	}
	
	private void relinkCurrentListToLibrary() {
		for (int k = 0; k < audioSystem.getCurrentList().getItems().size(); k++ ) {
			boolean foundInLibrary = false;
			for (Track libraryTrack : tracks.getItemsCopy()) {
				if (libraryTrack.equals(audioSystem.getCurrentList().getItems().get(k))) {
					audioSystem.getCurrentList().getItems().get(k).setData(libraryTrack);
					foundInLibrary = true;
				}
			}
			if(!foundInLibrary) {
				audioSystem.getCurrentList().getItems().get(k).setAlbum(null);
			}
		}
	}
	
	private void relinkPlaylistsToLibrary() {
		for (Playlist playlist : playlists.getItemsCopy()) {
			linkPlaylistToLibrary(playlist);
		}
	}

	public void linkPlaylistToLibrary(Playlist playlist) {
		for (int k = 0; k < playlist.getTracks().size(); k++ ) {
			boolean foundInLibrary = false;
			for (Track libraryTrack : tracks.getItemsCopy()) {
				if (libraryTrack.equals(playlist.getTracks().get(k))) {
					playlist.getTracks().set(k, libraryTrack);
					foundInLibrary = true;
				}
			}
			if(!foundInLibrary) {
				playlist.getTracks().get(k).setAlbum(null);
			}
		}
	}

	public void setAudioSystem(AudioSystem audioSystem) {
		this.audioSystem = audioSystem;
	}

	public void removeTagErrors(Vector<TagError> errors) {
		for (TagError error : errors) {
			tagErrors.remove(error);
		}
	}
	
	public void addTagErrors(Vector<TagError> errors) {
		for (TagError error : errors) {
			tagErrors.addItem(error);
		}
	}
}
