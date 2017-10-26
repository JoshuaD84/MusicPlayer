package net.joshuad.hypnos;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import net.joshuad.hypnos.audio.AudioSystem;
import net.joshuad.hypnos.fxui.FXUI;

public class LibraryUpdater {
	private static final Logger LOGGER = Logger.getLogger( LibraryUpdater.class.getName() );
	
	public enum LoaderSpeed {
		LOW, MED, HIGH
	}

	private int maxChangesPerUpdate = 2000;
	private int sleepTimeMS = 15;
	
	private boolean runLaterPending = false;
	
	private FXUI ui;
	private Library library;
	private AudioSystem player;
	private Thread libraryUpdaterThread;
	
	public LibraryUpdater( Library library, AudioSystem player, FXUI ui ) {
		this.ui = ui;
		this.library = library;
		this.player = player;
		
		libraryUpdaterThread = new Thread( () -> {
			while ( true ) {
				if( !runLaterPending ) {
					updateLibrary();
				}
				
				try {
					Thread.sleep ( 15 );
				} catch ( InterruptedException e ) {
					LOGGER.log ( Level.FINE, "Sleep interupted during wait period." );
				}
			}
		});
		
		libraryUpdaterThread.setDaemon( true );
	}
	
	public void start () {
		libraryUpdaterThread.start();
	}
	
	public void setMaxChangesPerUpdate ( int max ) {
		this.maxChangesPerUpdate = max;
	}
	
	public void setSleepTimeMS ( int timeMS ) {
		this.sleepTimeMS = timeMS;
	}
	
	private void updateLibrary () { 
		if ( !library.albumsToAdd.isEmpty() || !library.albumsToRemove.isEmpty() || !library.albumsToUpdate.isEmpty() 
		  || !library.tracksToAdd.isEmpty() || !library.tracksToRemove.isEmpty() || !library.tracksToUpdate.isEmpty() 	
		  || !library.playlistsToAdd.isEmpty() || !library.playlistsToRemove.isEmpty() || !library.playlistsToUpdate.isEmpty() 
		  ){
			
			runLaterPending = true;
			
			Platform.runLater( () -> {
				try {
				
					int changeCount = 0;
					
					//TODO: make sure these don't violate MAX_CHANGES like above, but whatever do it later not gonna happen. 
					synchronized ( library.playlistsToRemove ) {
						for ( Playlist deleteMe : library.playlistsToRemove ) {
							library.playlists.remove ( deleteMe );
							Hypnos.getPersister().deletePlaylistFile( deleteMe );
						}
						
						library.playlistsToRemove.clear();
						ui.updatePlaylistPlaceholder();
					}
				
					synchronized ( library.playlistsToAdd ) {
						
						for ( Playlist candidate : library.playlistsToAdd ) {
							candidate.setName( library.getUniquePlaylistName( candidate.getName() ) );
							library.playlists.add( candidate );
						}
						
						library.playlistsToAdd.clear();
					}
					
					
					library.playlistsToUpdate.clear(); //PENDING: update playlists. 

					ui.updatePlaylistPlaceholder();
					
					synchronized ( library.albumsToRemove ) {
						if ( !library.albumsToRemove.isEmpty() ) {
							while ( changeCount < maxChangesPerUpdate && !library.albumsToRemove.isEmpty() ) {
								library.albums.remove( library.albumsToRemove.remove( 0 ) );
								changeCount++;
							}
							
							ui.updateAlbumListPlaceholder();

							if ( changeCount >= maxChangesPerUpdate ) {
								ui.refreshAlbumTable(); //TODO: this may not be necessary. 
								return;
							}
						}
					}
						
					synchronized ( library.albumsToAdd ) {
						if ( !library.albumsToAdd.isEmpty() ) {
							while ( changeCount < maxChangesPerUpdate && !library.albumsToAdd.isEmpty() ) {
								library.albums.add( library.albumsToAdd.remove( 0 ) );
								changeCount++;
							}
							
							ui.updateAlbumListPlaceholder();

							if ( changeCount >= maxChangesPerUpdate ) {
								ui.refreshAlbumTable();  //TODO: this may not be necessary. 
								return;
							}
						}
					}
					
					synchronized ( library.albumsToUpdate ) {
						if ( !library.albumsToUpdate.isEmpty() ) {
							synchronized ( library.albumsToUpdate ) {
								while ( changeCount < maxChangesPerUpdate && !library.albumsToUpdate.isEmpty() ) {
									Album updateSource = library.albumsToUpdate.remove( 0 );
									
									if ( library.albums.contains( updateSource ) ) {
										Album updateMe = library.albums.get( library.albums.indexOf( updateSource ) );
										try {
											updateMe.updateData();
											
											List <Album> currentListAlbums = player.getCurrentList().getState().getAlbums();
											
											//TODO: handle this when we have multiple discs loaded
											if ( currentListAlbums.size() == 1 && updateMe.equals( currentListAlbums.get( 0 ) ) ) {
												
												//There is a small window where we need to let the UI thread start playing the new album
												//So we get an accurate currentTrack, so we give it a little time
												//This is definitely a hack, but it works just fine. 
												Thread.sleep( 100 ); 
												
												Track currentTrack = null;
												Track currentArtImages = ui.getCurrentImagesTrack();
												List <CurrentListTrack> selectedItems = new ArrayList<> ( ui.getSelectedTracks() );
												
												if ( !player.isStopped() ) {
													currentTrack = player.getCurrentTrack();
												}
												
												player.getCurrentList().setAlbum( updateMe );
												
												ui.setImages( currentArtImages );
												ui.setSelectedTracks ( selectedItems );
												
												library.albumsToUpdate.remove( updateMe ); //prevent an infinite loop
												
												if ( currentTrack != null ) {
													for ( CurrentListTrack currentListTrack : player.getCurrentList().getItems() ) {
														if ( currentListTrack.equals( currentTrack ) ) {
															currentListTrack.setIsCurrentTrack( true );
															currentListTrack.setIsLastCurrentListTrack( true );
															break;
														}
													}
												}
											}
											
										} catch ( Exception e ) {
											try {
												library.albums.remove( updateMe );
											} catch ( Exception e2 ) {}
										}
									} else {
										library.albums.add( updateSource );
									}
									
									changeCount += 2; //We charge two here because this is a costly transaction
	 							}
								
								if ( changeCount >= maxChangesPerUpdate ) {
									ui.refreshAlbumTable();  //TODO: this may not be necessary. 
									return;
								}
							}
						}
					}
					
					synchronized ( library.tracksToRemove ) {
						if ( !library.tracksToRemove.isEmpty() ) {
							while ( changeCount < maxChangesPerUpdate && !library.tracksToRemove.isEmpty() ) {
								Track removeMe = library.tracksToRemove.remove( 0 );
								
								if ( removeMe != null ) {
									boolean removed = library.tracks.remove( removeMe );
									library.tagErrors.removeAll( removeMe.getTagErrors() );
									
									if ( removed ) {
										changeCount++;
									}
								}
							}

							ui.updateTrackListPlaceholder();
							
							if ( changeCount >= maxChangesPerUpdate ) {
								ui.refreshTrackTable(); //TODO: this may not be necessary. 
								return;
							}
						}
					}
						
					synchronized ( library.tracksToAdd ) {
						if ( !library.tracksToAdd.isEmpty() ) {
							while ( changeCount < maxChangesPerUpdate && !library.tracksToAdd.isEmpty() ) {
								Track track = library.tracksToAdd.remove( 0 );
								if ( !library.containsTrack( track ) ) {
									library.tracks.add( track );
									library.tagErrors.addAll( track.getTagErrors() );
								}
								changeCount+=2;
							}


							ui.updateTrackListPlaceholder();
							
							if ( changeCount >= maxChangesPerUpdate ) {
								ui.refreshTrackTable(); //TODO: this may not be necessary. 
								return;
							}
						}
					}
	
					synchronized ( library.tracksToUpdate ) {
						if ( !library.tracksToUpdate.isEmpty() ) {
							while ( changeCount < maxChangesPerUpdate && !library.tracksToUpdate.isEmpty() ) {
								Track track = library.tracksToUpdate.remove( 0 );
								
								List <TagError> removeMe = new ArrayList <TagError> ();
								for ( TagError error : library.tagErrors ) {
									if ( error.getTrack().equals( track ) ) {
										removeMe.add( error );
									}
								}
								
								library.tagErrors.removeAll( removeMe );
								
								try { 
									track.refreshTagData();
								} catch ( Exception e ) {
									LOGGER.log ( Level.INFO, "Error updating track info.", e );
								} 
								
								library.tagErrors.addAll( track.getTagErrors() );
								
								changeCount ++;
							}


							ui.updateTrackListPlaceholder();
							
							if ( changeCount >= maxChangesPerUpdate ) {
								ui.refreshTrackTable(); //TODO: this may not be necessary. 
								return;
							}
						}
					}
					
				
					
				} finally {
					runLaterPending = false;
				}
			});
		}
	}
}
