package net.joshuad.library;

import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import net.joshuad.hypnos.fxui.FXUI;
import net.joshuad.library.UpdateAction.ActionType;

public class LibraryMerger {
  private static final Logger LOGGER = Logger.getLogger(LibraryMerger.class.getName());

  private Vector<UpdateAction> pendingActions = new Vector<>();

  Library library;
  
  private FXUI ui;

  private Thread mergerThread;

  private boolean runLaterPending = false;

  private int sleepTimeMS = 400;

  public LibraryMerger(Library library) {
    this.library = library;

    mergerThread = new Thread(() -> {
      while (true) {
        if (!runLaterPending) {
          updateLibrary();
        }

        try {
          Thread.sleep(sleepTimeMS);
        } catch (InterruptedException e) {
          LOGGER.log(Level.FINE, "Sleep interupted during wait period.");
        }
      }
    });

    mergerThread.setName("Library Merger");
    mergerThread.setDaemon(true);
  }
  
	void setUI ( FXUI ui ) {
		this.ui = ui;
	}

  public void start() {
    if (!mergerThread.isAlive()) {
      mergerThread.start();
    } else {
      LOGGER.log(Level.INFO, "Library Merger thread asked to start, but it's already running, request ignored.");
    }
  }

  public void setSleepTimeMS(int timeMS) {
    this.sleepTimeMS = timeMS;
  }

  private void updateLibrary() {
    if (!pendingActions.isEmpty()) {
      runLaterPending = true;

      Platform.runLater(() -> {
        long startTime = System.currentTimeMillis();
        try {
          synchronized (pendingActions) {
	          while ( pendingActions.size() > 0 && System.currentTimeMillis() - startTime < 500 ) {
	          	UpdateAction action = pendingActions.remove( 0 );
              switch (action.getActionType()) {
                case ADD_MUSIC_ROOT:
                	if ( !library.musicRoots.contains( (MusicRoot) action.getItem() ) ) {
                		library.musicRoots.add((MusicRoot) action.getItem());
                	}
                  break;
                case REMOVE_MUSIC_ROOT:
                  library.musicRoots.remove((MusicRoot) action.getItem());
                  break;
                case ADD_ALBUM:
                  library.albums.add((Album) action.getItem());
                  break;
                case REMOVE_ALBUM:
                  library.albums.remove((Album) action.getItem());
                  break;
                case UPDATE_ALBUM: 
                	Album updateMe = (Album)(((Object[])action.getItem())[0]);
                	Album newData = (Album)(((Object[])action.getItem())[1]);
                	updateMe.setData( newData );
                	break;
                case ADD_TRACK:
                  library.tracks.add((Track) action.getItem());
                  break;
                case REMOVE_TRACK:
                  library.tracks.remove((Track) action.getItem());
                  break;
                case CLEAR_ALL:
                	library.tracks.clear();
                	library.albums.clear();
                	library.artists.clear();
                	ui.libraryCleared();
                	break;
                case REFRESH_TRACK_TABLE: 
                	if (ui != null) {
	                	ui.refreshTrackTable();
	                }
                  break;
                case REFRESH_ALBUM_TABLE:
                  if (ui != null) {
                  	ui.refreshAlbumTable();
                  }
                  break;
								case ADD_PLAYLIST:
									//TODO: 
									break;
								case REFRESH_PLAYLIST_TABLE:
									//TODO: 
									break;
								case REMOVE_PLAYLIST:
									//TODO: 
									break;
								default:
									break;
              }
	            library.setDataNeedsToBeSavedToDisk (true);
	          }
          }

        } finally {
          runLaterPending = false;
        }
      });
    }
  }

  /*---------------------------------------------------------------------------*/
  /* These methods are run off the JavaFX thread, and help prepare fast merges */
  /*---------------------------------------------------------------------------*/

  public void addOrUpdateTrack(Track track) {
    if (track == null) {
      System.out.println("[Merger] Asked to add a null track to library, ignoring");
      return;
    }
    
    synchronized (pendingActions) {
      boolean didUpdate = false;
      for (int k= 0; k < pendingActions.size(); k++) {
        UpdateAction action = pendingActions.get(k);
        if (action.getActionType() == ActionType.ADD_TRACK && track.equals(action.getItem())) {
          //We can do updates to data off the javafx thread because the values aren't observable. 
          ((Track)action.getItem()).setData(track);
          library.setDataNeedsToBeSavedToDisk (true);
          didUpdate = true;
        }
      }

      int existingLibraryIndex = library.tracks.indexOf(track);
      if (existingLibraryIndex != -1) {
        //We can do updates to data off the javafx thread because the values aren't observable. 
        library.tracks.get(existingLibraryIndex).setData(track);
        library.setDataNeedsToBeSavedToDisk (true);
        didUpdate = true;
      }

      if (!didUpdate) {
        pendingActions.add(new UpdateAction(track, ActionType.ADD_TRACK));
      } else {
        pendingActions.add(new UpdateAction(null, ActionType.REFRESH_TRACK_TABLE));
      }
    }
  }
  
  public void addOrUpdatePlaylist(Playlist playlist) {
    if (playlist == null) {
      System.out.println("[Merger] Asked to add a null playlist to library, ignoring");
      return;
    }
    
    synchronized (pendingActions) {
      boolean didUpdate = false;
      for (int k= 0; k < pendingActions.size(); k++) {
        UpdateAction action = pendingActions.get(k);
        if (action.getActionType() == ActionType.ADD_PLAYLIST && playlist.equals(action.getItem())) {
          //We can do updates to data off the javafx thread because the values aren't observable. 
          ((Playlist)action.getItem()).setData(playlist);
          didUpdate = true;
        }
      }
      
      int existingLibraryIndex = library.playlists.indexOf(playlist);
      if (existingLibraryIndex != -1) {
        //We can do updates to data off the javafx thread because the values aren't observable. 
        library.playlists.get(existingLibraryIndex).setData(playlist);
        didUpdate = true;
      }
      
      if (!didUpdate) {
        pendingActions.add(new UpdateAction(playlist, ActionType.ADD_PLAYLIST));
      } else {
        pendingActions.add(new UpdateAction(null, ActionType.REFRESH_PLAYLIST_TABLE));
      }
    }
  }

  void addOrUpdateAlbum(Album album) {
    if (album == null) {
      System.out.println("[Merger] Asked to add/update a null album to library, ignoring");
      return;
    }
    
    synchronized (pendingActions) {
      boolean didUpdate = false;
      for (int k= 0; k < pendingActions.size(); k++) {
        UpdateAction action = pendingActions.get(k);
        if (action.getActionType() == ActionType.ADD_ALBUM && album.equals(action.getItem())) {
          //We can do updates to data off the javafx thread because the values aren't observable. 
          ((Album)action.getItem()).setData(album);
          library.setDataNeedsToBeSavedToDisk(true);
          didUpdate = true;
        }
      }

      int existingLibraryIndex = library.albums.indexOf(album);
      if (existingLibraryIndex != -1) {
      	pendingActions.add(new UpdateAction(new Object[]{ library.albums.get(existingLibraryIndex), album }, ActionType.UPDATE_ALBUM));
        //We can do updates to data off the javafx thread because the values aren't observable. 
        didUpdate = true;
      }

      if (!didUpdate) {
        pendingActions.add(new UpdateAction(album, ActionType.ADD_ALBUM));
      }
    }
  }
  
  public void addMusicRoot(MusicRoot musicRoot) {    
    pendingActions.add(new UpdateAction(musicRoot, ActionType.ADD_MUSIC_ROOT));
  }
  
  public void removeMusicRoot(MusicRoot musicRoot) {    
    pendingActions.add(new UpdateAction(musicRoot, ActionType.REMOVE_MUSIC_ROOT));
  }
  
  public void removeTrack(Track track) {
    pendingActions.add(new UpdateAction(track, ActionType.REMOVE_TRACK));
  }

  public void removeAlbum(Album album) {
    pendingActions.add(new UpdateAction(album, ActionType.REMOVE_ALBUM));
  }
  
  public void removePlaylist(Playlist playlist) {
    pendingActions.add(new UpdateAction(playlist, ActionType.REMOVE_PLAYLIST));
  }

	public void clearAll() {
    pendingActions.add(new UpdateAction(null, ActionType.CLEAR_ALL));
	}
}
