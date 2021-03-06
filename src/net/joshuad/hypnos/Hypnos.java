package net.joshuad.hypnos;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import net.joshuad.hypnos.Persister.Setting;
import net.joshuad.hypnos.audio.AudioSystem;
import net.joshuad.hypnos.audio.AudioSystem.StopReason;
import net.joshuad.hypnos.fxui.FXUI;
import net.joshuad.hypnos.hotkeys.GlobalHotkeys;
import net.joshuad.hypnos.hotkeys.GlobalHotkeys.Hotkey;
import net.joshuad.hypnos.library.Album;
import net.joshuad.hypnos.library.Library;
import net.joshuad.hypnos.library.Playlist;
import net.joshuad.hypnos.library.Track;
import net.joshuad.hypnos.library.Library.LoaderSpeed;

public class Hypnos extends Application {

	private static final Logger LOGGER = Logger.getLogger( Hypnos.class.getName() );
	
	public enum ExitCode {
		NORMAL,
		UNKNOWN_ERROR,
		AUDIO_ERROR, 
		UNSUPPORTED_OS
	}
	
	public enum OS {
		WIN_XP ( "Windows XP" ),
		WIN_VISTA ( "Windows Vista" ),
		WIN_7 ( "Windows 7" ),
		WIN_8 ( "Windows 8" ),
		WIN_10 ( "Windows 10" ),
		WIN_UNKNOWN ( "Windows Unknown" ),
		OSX ( "Mac OSX" ),
		NIX ( "Linux/Unix" ), 
		UNKNOWN ( "Unknown" );
		
		private String displayName;
		OS ( String displayName ) { this.displayName = displayName; }
		public String getDisplayName () { return displayName; }
		
		public boolean isWindows() {
			switch ( this ) {
				case WIN_10:
				case WIN_7:
				case WIN_8:
				case WIN_UNKNOWN:
				case WIN_VISTA:
				case WIN_XP:
					return true;
				case NIX:
				case OSX:
				case UNKNOWN:
				default:
					return false;
			}
		}
		
		public boolean isOSX() {
			switch ( this ) {
				case OSX:
					return true;
				case WIN_10:
				case WIN_7:
				case WIN_8:
				case WIN_UNKNOWN:
				case WIN_VISTA:
				case WIN_XP:
				case NIX:
				case UNKNOWN:
				default:
					return false;
			}
		}
		
		public boolean isLinux() {
			switch ( this ) {
				case NIX:
					return true;
				case WIN_10:
				case WIN_7:
				case WIN_8:
				case WIN_UNKNOWN:
				case WIN_VISTA:
				case WIN_XP:
				case UNKNOWN:
				case OSX:
				default:
					return false;
			}
		}
	}
	
	private static OS os = OS.UNKNOWN;
	private static String build;
	private static String version;
	private static String buildDate;
	private static Path rootDirectory;
	private static Path configDirectory;
	private static Path logFile, logFileBackup, logFileBackup2;
	private static boolean isStandalone = false;
	private static boolean isDeveloping = false;
	private static boolean disableGlobalHotkeysRequestedByProperties = false;
	
	private static Persister persister;
	private static AudioSystem audioSystem;
	private static FXUI ui;
	private static Library library;
	private static GlobalHotkeys globalHotkeys;
	
	private static PrintStream originalOut;
	private static PrintStream originalErr;
	
	private static LoaderSpeed loaderSpeed = LoaderSpeed.HIGH;
	
	private static ByteArrayOutputStream logBuffer; //Used to store log info until log file is initialized
	
	private static Formatter logFormat = new Formatter() {
		SimpleDateFormat dateFormat = new SimpleDateFormat ( "MMM d, yyyy HH:mm:ss aaa" );
		public String format ( LogRecord record ) {
			
			String exceptionMessage = "";
			
			if ( record.getThrown() != null ) {
				StringWriter sw = new StringWriter();
				record.getThrown().printStackTrace( new PrintWriter( sw ) );
				exceptionMessage = "\n" + sw.toString();
			}
			
			String retMe = dateFormat.format( new Date ( record.getMillis() ) )
				  + " " + record.getLoggerName()
				  + " " + record.getSourceMethodName()
				  + System.lineSeparator()
				  + record.getLevel() + ": " + record.getMessage()
				  + exceptionMessage
				  + System.lineSeparator()
				  + System.lineSeparator();
			
			return retMe;
		}
	};
	
	public static OS getOS() {
		return os;
	}
	
	public static String getVersion() {
		return version;
	}
	
	public static String getBuild() {
		return build;
	}
	
	public static String getBuildDate() {
		return buildDate;
	}
	
	public static boolean isStandalone() {
		return isStandalone;
	}
	
	public static boolean isDeveloping() {
		return isDeveloping;
	}
	
	public static Path getRootDirectory() {
		return rootDirectory;
	}
	
	public static Path getConfigDirectory() {
		return configDirectory;
	}
	
	public static Path getLogFile() {
		return logFile;
	}
	
	public static Path getLogFileBackup() {
		return logFileBackup;
	}
	
	public static Persister getPersister() {
		return persister;
	}
	
	public static Library getLibrary() {
		return library;
	}
	
	public static FXUI getUI() {
		return ui;
	}
	
	public static LoaderSpeed getLoaderSpeed ( ) {
		return loaderSpeed;
	}
	
	public static void setLoaderSpeed ( LoaderSpeed speed ) {
		loaderSpeed = speed;
		ui.setLoaderSpeedDisplay ( speed );
	}
	
	private static void startLogToBuffer() {
		originalOut = System.out;
		originalErr = System.err;
		
		logBuffer = new ByteArrayOutputStream();
		
		System.setOut( new PrintStream ( logBuffer ) );
		System.setErr( new PrintStream ( logBuffer ) );

		Logger.getLogger( "" ).getHandlers()[0].setFormatter( logFormat );
	}
	
	private void parseSystemProperties() {
				
		isStandalone = Boolean.getBoolean( "hypnos.standalone" );
		isDeveloping = Boolean.getBoolean( "hypnos.developing" );
		disableGlobalHotkeysRequestedByProperties = Boolean.getBoolean( "hypnos.disableglobalhotkeys" );
		
		if ( isStandalone ) LOGGER.info ( "Running as standalone - requested by system properties set at program launch" );
		if ( isDeveloping ) LOGGER.info ( "Running on development port - requested by system properties set at program launch" );
		if ( disableGlobalHotkeysRequestedByProperties ) LOGGER.info ( "Global hotkeys disabled - requested by system properties set at program launch" );
	}
	
	private void determineOS() {
		String osString = System.getProperty( "os.name" ).toLowerCase();
		
		if ( osString.indexOf( "win" ) >= 0 ) {
			if ( osString.indexOf( "xp" ) >= 0 ) {
				os = OS.WIN_XP;

			} else if ( osString.indexOf( "vista" ) >= 0 ) {
				os = OS.WIN_VISTA;
			
			} else if ( osString.indexOf( "7" ) >= 0 ) {
				os = OS.WIN_7;
				
			} else if ( osString.indexOf( "8" ) >= 0 ) {
				os = OS.WIN_8;
				
			} else if ( osString.indexOf( "10" ) >= 0 ) {
				os = OS.WIN_10;

			} else {
				os = OS.WIN_UNKNOWN;
			}
			
		} else if ( osString.indexOf( "nix" ) >= 0 || osString.indexOf( "linux" ) >= 0 ) {
			os = OS.NIX;

		} else if ( osString.indexOf( "mac" ) >= 0 ) {
			os = OS.OSX;
			
		} else {
			os = OS.UNKNOWN;
		}
		
		LOGGER.info ( "Operating System: " + os.getDisplayName() );
	}
	
	public String determineVersionInfo () {
		@SuppressWarnings("rawtypes")
		Enumeration resEnum;
		try {
			resEnum = Thread.currentThread().getContextClassLoader().getResources( JarFile.MANIFEST_NAME );
			while ( resEnum.hasMoreElements() ) {
				try {
					URL url = (URL) resEnum.nextElement();
					
					if ( url.getFile().toLowerCase().contains( "hypnos" ) ) {
						try ( 
							InputStream is = url.openStream();
						) {
							if ( is != null ) {
								Manifest manifest = new Manifest( is );
								Attributes mainAttribs = manifest.getMainAttributes();
								if ( mainAttribs.getValue( "Hypnos" ) != null ) {
									version = mainAttribs.getValue( "Implementation-Version" );
									build = mainAttribs.getValue ( "Build-Number" );
									buildDate = mainAttribs.getValue ( "Build-Date" );
									LOGGER.info ( "Version: " + version + ", Build: " + build + ", Build Date: " + buildDate );
								}
							}
						}
					}
				} catch ( Exception e ) {
					// Silently ignore wrong manifests on classpath?
				}
			}
		} catch ( Exception e1 ) {
			// Silently ignore wrong manifests on classpath?
		}
		return null;
	}
	
	
	private void setupRootDirectory () {
		String path = FXUI.class.getProtectionDomain().getCodeSource().getLocation().getPath();
				
		try {
			String decodedPath = URLDecoder.decode(path, "UTF-8");
			decodedPath = decodedPath.replaceFirst("^/(.:/)", "$1");
			rootDirectory = Paths.get( decodedPath ).getParent();
			
		} catch ( UnsupportedEncodingException e ) {
			rootDirectory = Paths.get( path ).getParent();
		}
	}
	
	private void setupConfigDirectory () {
		// PENDING: We might want to make a few fall-throughs if these locations don't exist.
		String home = System.getProperty( "user.home" );

		if ( Hypnos.isStandalone() ) {
			configDirectory = getRootDirectory().resolve( "config" );

		} else {
			final String x = File.separator;
			switch ( getOS() ) {
				case NIX:
					configDirectory = Paths.get( home + x + ".config/hypnos" );
					break;
				case OSX:
					configDirectory = Paths.get( home + x + "Preferences" + x + "Hypnos" );
					break;
				case WIN_10:
					configDirectory = Paths.get( home + x + "AppData" + x + "Local" + x + "Hypnos" );
					break;
				case WIN_7:
					configDirectory = Paths.get( home + x + "AppData" + x + "Local" + x + "Hypnos" );
					break;
				case WIN_8:
					configDirectory = Paths.get( home + x + "AppData" + x + "Local" + x + "Hypnos" );
					break;
				case WIN_UNKNOWN:
					configDirectory = Paths.get( home + x + "AppData" + x + "Local" + x + "Hypnos" );
					break;
				case WIN_VISTA:
					configDirectory = Paths.get( home + x + "AppData" + x + "Local" + x + "Hypnos" );
					break;
				case WIN_XP:
					//Do nothing, windows XP not supported
					//configDirectory = Paths.get( home + x + "Local Settings" + x + "Application Data" + x + "Hypnos" );
					break;
				case UNKNOWN: //Fall through
				default:
					configDirectory = Paths.get( home + x + ".hypnos" );
					break;
			}
		}
		
		File configDirectory = Hypnos.getConfigDirectory().toFile();
		
		if ( !configDirectory.exists() ) {
			LOGGER.info( "Config directory doesn't exist, creating: " + Hypnos.getConfigDirectory() );
			try {
				configDirectory.mkdirs();
			} catch ( Exception e ) {
				String message = "Unable to create config directory, data will not be saved.\n" + Hypnos.getConfigDirectory();
				LOGGER.info( message );
				///TODO: Some version of a deferred ui.notifyUserError( message );
			}
		} else if ( !configDirectory.isDirectory() ) {
			String message = "There is a file where the config directory should be, data will not be saved.\n" + Hypnos.getConfigDirectory();
			LOGGER.info( message );
			///TODO: Some version of a deferred ui.notifyUserError( message );
			
		} else if ( !configDirectory.canWrite() ) {
			String message = "Cannot write to config directory, data will not be saved.\n" + Hypnos.getConfigDirectory();
			LOGGER.info( message );
			///TODO: Some version of a deferred ui.notifyUserError( message );
		}
	}
	
	private void setupLogFile() {
		logFile = configDirectory.resolve( "hypnos.log" );
		logFileBackup = configDirectory.resolve( "hypnos.log.1" );
		logFileBackup2 = configDirectory.resolve( "hypnos.log.2" );
		
		if ( Files.exists( logFileBackup ) ) {
			try {
				Files.move( logFileBackup, logFileBackup2, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE  );
			} catch ( Exception e ) {
				LOGGER.log ( Level.WARNING, "Unable to create 2nd backup logfile", e );
			}
		}
		
		if ( Files.exists( logFile ) ) {
			try {
				Files.move( logFile, logFileBackup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE  );
			} catch ( Exception e ) {
				LOGGER.log ( Level.WARNING, "Unable to create 1st backup logfile", e );
			}
		}
				
		try {
			logFile.toFile().createNewFile();
		} catch ( Exception e ) {
			LOGGER.log ( Level.WARNING, "Unable to create logfile", e );
		}
		
		try {
			PrintWriter logOut = new PrintWriter ( new FileOutputStream ( logFile.toFile(), false ) );
			String logBufferS = logBuffer.toString();
			logOut.print( logBufferS );
			originalErr.print ( logBufferS );
			logOut.close();
			
		} catch ( Exception e ) {
			LOGGER.log ( Level.WARNING, "Unable to write initial log entries to log file", e );
		}
		
		try {
			//Remove the existing console handler
			Logger.getLogger( "" ).removeHandler( Logger.getLogger( "" ).getHandlers()[0] );
						
			//restore system out and system err, but put all of them to the err channel
			System.setOut( originalErr );
			System.setErr( originalErr );
			
			//Add a file handler to output to logFile			
			FileHandler fileHandler = new FileHandler( logFile.toString(), true );     
			fileHandler.setFormatter( logFormat );
	    Logger.getLogger( "" ).addHandler( fileHandler );
			
			//Create console output
			ConsoleHandler consoleHandler = new ConsoleHandler();    
			consoleHandler.setFormatter( logFormat );
	    Logger.getLogger( "" ).addHandler( consoleHandler );
	        
		} catch ( IOException e ) {
			LOGGER.log( Level.WARNING, "Unable to setup file handler for logger.", e );
		}
	}
	
	public static boolean globalHotkeysDisabled() {
		//TODO: ask hotkeys, don't keep a value in Hypnos
		return disableGlobalHotkeysRequestedByProperties;
	}

	public static void doHotkeyAction ( Hotkey hotkey ) {
		Platform.runLater( () -> { //TODO: Should this runLater() be around everything or just show_hide_ui? 
			switch ( hotkey ) {
				case NEXT:
					audioSystem.next();
					break;
				case PLAY:
					audioSystem.play();
					break;
				case PREVIOUS:
					audioSystem.previous();
					break;
				case SHOW_HIDE_UI:
					ui.toggleHidden();
					break;
				case SKIP_BACK:
					long target = audioSystem.getPositionMS() - 5000 ;
					if ( target < 0 ) target = 0;
					audioSystem.seekMS( target ); 
					break;
				case SKIP_FORWARD:
					audioSystem.seekMS( audioSystem.getPositionMS() + 5000 ); 
					break;
				case STOP:
					audioSystem.stop( StopReason.USER_REQUESTED );
					break;
				case TOGGLE_MUTE:
					audioSystem.toggleMute();
					break;
				case PLAY_PAUSE:
					audioSystem.togglePlayPause();
					break;
				case TOGGLE_REPEAT:
					audioSystem.toggleRepeatMode();
					break;
				case TOGGLE_SHUFFLE:
					audioSystem.toggleShuffleMode();
					break;
				case VOLUME_DOWN:
					audioSystem.decrementVolume();
					break;
				case VOLUME_UP:
					audioSystem.incrementVolume();
					break;
				default:
					break;
			}
		});
	}
	
	public static void warnUserPlaylistsNotSaved ( ArrayList <Playlist> errors ) {
		ui.warnUserPlaylistsNotSaved ( errors );
	}

	public static void warnUserAlbumsMissing ( List <Album> missing ) {
		ui.warnUserAlbumsMissing ( missing );
	}
	
	
	private long setTracksLastTime = 0;
	
	public void applyCLICommands ( List <SocketCommand> commands ) {
		ArrayList<Path> tracksToPlay = new ArrayList<Path>();
		for ( SocketCommand command : commands ) {
			if ( command.getType() == SocketCommand.CommandType.SET_TRACKS ) {
				for ( File file : (List<File>) command.getObject() ) {
					if ( file.isDirectory() ) {
						tracksToPlay.addAll( Utils.getAllTracksInDirectory( file.toPath() ) );
						
					} else if ( Utils.isPlaylistFile( file.toPath() ) ) {
						//TODO: It's kind of lame to load the tracks here just to discard them and load them again a few seconds later
						//Maybe modify loadPlaylist so i can just ask for the specified paths, without loading tag data
						Playlist playlist = Playlist.loadPlaylist( file.toPath() );
						for ( Track track : playlist.getTracks() ) {
							tracksToPlay.add( track.getPath() );
						}
						
					} else if ( Utils.isMusicFile( file.toPath() ) ) {
						tracksToPlay.add( file.toPath() );
						
					} else {
						LOGGER.log( Level.INFO, "Recived non-music, non-playlist file, ignoring: " + file, new Exception() );
					}
				}
			}
		}
		
		if ( tracksToPlay.size() > 0 ) {
			if ( System.currentTimeMillis() - setTracksLastTime > 2000 ) {
				Platform.runLater( () -> {
					audioSystem.getCurrentList().setTracksPathList( tracksToPlay,
						new Runnable() {
							@Override
							public void run() {
								audioSystem.next( false );
							}
						}
					);
				});
			} else {
				Platform.runLater( () -> {
					audioSystem.getCurrentList().appendTracksPathList ( tracksToPlay );
				});
			}
			setTracksLastTime = System.currentTimeMillis();
		}

		for ( SocketCommand command : commands ) {
			if ( command.getType() == SocketCommand.CommandType.CONTROL ) {
				int action = (Integer)command.getObject();
				Platform.runLater( () -> {
					switch ( action ) {
						case SocketCommand.NEXT: 
							audioSystem.next();
							break;
						case SocketCommand.PREVIOUS:
							audioSystem.previous();
							break;
						case SocketCommand.PAUSE:
							audioSystem.pause();
							break;
						case SocketCommand.PLAY:
							audioSystem.unpause();
							break;
						case SocketCommand.TOGGLE_PAUSE:
							audioSystem.togglePause();
							break;
						case SocketCommand.STOP:
							audioSystem.stop( StopReason.USER_REQUESTED );
							break;
						case SocketCommand.TOGGLE_MINIMIZED:
							ui.toggleMinimized();
							break;
						case SocketCommand.VOLUME_DOWN:
							audioSystem.decrementVolume();
							break;
						case SocketCommand.VOLUME_UP:
							audioSystem.incrementVolume();
							break;
						case SocketCommand.SEEK_BACK:
							long target = audioSystem.getPositionMS() - 5000 ;
							if ( target < 0 ) target = 0;
							audioSystem.seekMS( target ); 
							break;
						case SocketCommand.SEEK_FORWARD:
							audioSystem.seekMS( audioSystem.getPositionMS() + 5000 ); 
							break;
						case SocketCommand.SHOW:
							ui.restoreWindow();
							break;
					}
				});
			} 
		}
	}

	public static void exit ( ExitCode exitCode ) {
		Platform.runLater( () -> ui.getMainStage().hide() );
		
		if ( globalHotkeys != null ) {
			globalHotkeys.prepareToExit();
		}
		
		ui.getTrayIcon().prepareToExit();
		
		if ( audioSystem != null && ui != null ) {
			EnumMap <Setting, ? extends Object> fromAudioSystem = audioSystem.getSettings();
			EnumMap <Setting, ? extends Object> fromUI = ui.getSettings();
			audioSystem.stop ( StopReason.USER_REQUESTED );
			audioSystem.releaseResources();
			persister.saveAllData( fromAudioSystem, fromUI );
		}
		System.exit ( exitCode.ordinal() );
	}
	
	@Override
	public void stop () {
		exit ( ExitCode.NORMAL );
	}
	
	@Override
	public void start ( Stage stage ) {
		long baseStartTime = System.currentTimeMillis();
		String loadTimeMessage = "Load Time Breakdown\n";
		try {
		
			long thisTaskStart = System.currentTimeMillis();
			startLogToBuffer();
			loadTimeMessage += "- Start Log to Buffer: " + (System.currentTimeMillis() - thisTaskStart + "\n");
			thisTaskStart = System.currentTimeMillis();
			
			thisTaskStart = System.currentTimeMillis();
			parseSystemProperties();
			loadTimeMessage += "- Parse System Properties: " + (System.currentTimeMillis() - thisTaskStart + "\n");
			thisTaskStart = System.currentTimeMillis();
			
			thisTaskStart = System.currentTimeMillis();
			determineOS();
			loadTimeMessage += "- Determine OS " + (System.currentTimeMillis() - thisTaskStart + "\n");
			thisTaskStart = System.currentTimeMillis();
			
			thisTaskStart = System.currentTimeMillis();
			setupRootDirectory(); 
			loadTimeMessage += "- Setup Root Directory: " + (System.currentTimeMillis() - thisTaskStart + "\n");
			thisTaskStart = System.currentTimeMillis();
			
			thisTaskStart = System.currentTimeMillis();
			setupConfigDirectory();
			loadTimeMessage += "- Setup Config Directory: " + (System.currentTimeMillis() - thisTaskStart + "\n");
			thisTaskStart = System.currentTimeMillis();
			
			thisTaskStart = System.currentTimeMillis();
			determineVersionInfo();
			loadTimeMessage += "- Read Version Info: " + (System.currentTimeMillis() - thisTaskStart + "\n");
			thisTaskStart = System.currentTimeMillis();

			thisTaskStart = System.currentTimeMillis();
			String[] args = getParameters().getRaw().toArray ( new String[0] );
			CLIParser parser = new CLIParser( );
			ArrayList <SocketCommand> commands = parser.parseCommands( args );
			loadTimeMessage += "- Parse Args: " + (System.currentTimeMillis() - thisTaskStart + "\n");
			thisTaskStart = System.currentTimeMillis();
			
			thisTaskStart = System.currentTimeMillis();
			SingleInstanceController singleInstanceController = new SingleInstanceController();

			if ( singleInstanceController.isFirstInstance() ) {
				thisTaskStart = System.currentTimeMillis();
				setupLogFile();
				loadTimeMessage += "- Setup Log File: " + (System.currentTimeMillis() - thisTaskStart + "\n");
				thisTaskStart = System.currentTimeMillis();
				
				thisTaskStart = System.currentTimeMillis();
				library = new Library();
				loadTimeMessage += "- Initialize Library: " + (System.currentTimeMillis() - thisTaskStart + "\n");
				thisTaskStart = System.currentTimeMillis();
				
				thisTaskStart = System.currentTimeMillis();
				audioSystem = new AudioSystem();
				loadTimeMessage += "- Initialize Audio System: " + (System.currentTimeMillis() - thisTaskStart + "\n");
				thisTaskStart = System.currentTimeMillis();
				
				thisTaskStart = System.currentTimeMillis();
				globalHotkeys = new GlobalHotkeys( getOS(), disableGlobalHotkeysRequestedByProperties );
				globalHotkeys.addListener( Hypnos::doHotkeyAction );
				loadTimeMessage += "- Initialize Hotkeys: " + (System.currentTimeMillis() - thisTaskStart + "\n");
				thisTaskStart = System.currentTimeMillis();
				
				thisTaskStart = System.currentTimeMillis();
				ui = new FXUI ( stage, library, audioSystem, globalHotkeys );
				audioSystem.setUI ( ui );
				library.setAudioSystem ( audioSystem );
				loadTimeMessage += "- Initialize FX UI: " + (System.currentTimeMillis() - thisTaskStart + "\n");
				thisTaskStart = System.currentTimeMillis();
				
				thisTaskStart = System.currentTimeMillis();
				persister = new Persister ( ui, library, audioSystem, globalHotkeys );
				loadTimeMessage += "- Initialize Persister: " + (System.currentTimeMillis() - thisTaskStart + "\n");
				thisTaskStart = System.currentTimeMillis();
				
				switch ( getOS() ) {
					case NIX:
					case OSX: {
						EnumMap <Setting, String> pendingSettings = persister.loadSettingsFromDisk();
						persister.loadCurrentList();
						ui.applySettingsBeforeWindowShown( pendingSettings );
						
						if ( pendingSettings.containsKey( Setting.LOADER_SPEED ) ) {
							Hypnos.setLoaderSpeed( LoaderSpeed.valueOf( pendingSettings.get( Setting.LOADER_SPEED ) ) );
							pendingSettings.remove( Setting.LOADER_SPEED );
						} else {
							Hypnos.setLoaderSpeed(LoaderSpeed.HIGH);
						}

						ui.setLibraryLabelsToLoading();
						ui.showMainWindow();
						
						Thread finishLoadingThread = new Thread ( () -> {
							Platform.runLater( () -> {
								ui.applySettingsAfterWindowShown( pendingSettings );
								persister.logUnusedSettings ( pendingSettings );
							});

							boolean sourcesLoaded = persister.loadRoots();
							if ( sourcesLoaded ) {
								persister.loadAlbumsAndTracks();
							}
							audioSystem.applySettings ( pendingSettings );
							
							persister.loadQueue();
							audioSystem.linkQueueToCurrentList();
							persister.loadHistory();
							persister.loadPlaylists();
							persister.loadHotkeys();
							
							Platform.runLater( () -> ui.getLibraryPane().updatePlaceholders() );
							
							ui.refreshHotkeyList();
							
							applyCLICommands( commands );
							singleInstanceController.startCLICommandListener ( this );

							library.setUI( ui );
							library.startThreads();
			
							LOGGER.info( "Hypnos finished loading." );
							
							UpdateChecker updater = new UpdateChecker();
							boolean updateAvailable = updater.updateAvailable();
							if ( updateAvailable ) LOGGER.info( "Updates available" );
							ui.setUpdateAvailable ( updateAvailable );
							
							try { Thread.sleep ( 2000 ); } catch ( InterruptedException e ) {}
							
							ui.fixTables();
						} );
						
						finishLoadingThread.setName ( "Hypnos Load Finisher for Nix" );
						finishLoadingThread.setDaemon( false );
						finishLoadingThread.start();
					} 
					break;
					
					case UNKNOWN:
					case WIN_10:
					case WIN_7:
					case WIN_8:
					case WIN_UNKNOWN:
					case WIN_VISTA:{
						thisTaskStart = System.currentTimeMillis();
						EnumMap <Setting, String> pendingSettings = persister.loadSettingsFromDisk();
						loadTimeMessage += "- Read Settings from Disk: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						thisTaskStart = System.currentTimeMillis();
						
						thisTaskStart = System.currentTimeMillis();
						persister.loadCurrentList();
						loadTimeMessage += "- Read Current List from Disk " + (System.currentTimeMillis() - thisTaskStart + "\n");
						thisTaskStart = System.currentTimeMillis();
						
						thisTaskStart = System.currentTimeMillis();
						audioSystem.applySettings ( pendingSettings );
						if ( pendingSettings.containsKey( Setting.LOADER_SPEED ) ) {
							Hypnos.setLoaderSpeed( LoaderSpeed.valueOf( pendingSettings.get( Setting.LOADER_SPEED ) ) );
							pendingSettings.remove( Setting.LOADER_SPEED );
						} else {
							Hypnos.setLoaderSpeed(LoaderSpeed.HIGH);
						}
						ui.applySettingsBeforeWindowShown( pendingSettings );
						ui.applySettingsAfterWindowShown( pendingSettings );
						persister.logUnusedSettings ( pendingSettings );
						loadTimeMessage += "- Apply Settings: " + (System.currentTimeMillis() - thisTaskStart + "\n");

						thisTaskStart = System.currentTimeMillis();
						boolean sourcesLoaded = persister.loadRoots();
						if ( sourcesLoaded ) {
							persister.loadAlbumsAndTracks();
						}
						loadTimeMessage += "- Load Albums and Tracks: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						thisTaskStart = System.currentTimeMillis();
						persister.loadQueue();
						loadTimeMessage += "- Load Queue from Disk: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						thisTaskStart = System.currentTimeMillis();
						audioSystem.linkQueueToCurrentList();
						loadTimeMessage += "- Link Queue to current list: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						thisTaskStart = System.currentTimeMillis();
						persister.loadHistory();
						loadTimeMessage += "- Load History: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						thisTaskStart = System.currentTimeMillis();
						persister.loadPlaylists();
						loadTimeMessage += "- Load Playlists: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						thisTaskStart = System.currentTimeMillis();
						persister.loadHotkeys();
						loadTimeMessage += "- Load Hotkeys: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						thisTaskStart = System.currentTimeMillis();
						applyCLICommands( commands );
						singleInstanceController.startCLICommandListener ( this );
						loadTimeMessage += "- Apply CLI Commands: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						thisTaskStart = System.currentTimeMillis();
						library.setUI( ui );
						library.startThreads();
						persister.startThread();
						loadTimeMessage += "- Start background threads: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						thisTaskStart = System.currentTimeMillis();
						ui.showMainWindow();
						loadTimeMessage += "- Show UI: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						thisTaskStart = System.currentTimeMillis();
						ui.getLibraryPane().updatePlaceholders();
						ui.fixTables();
						ui.settingsWindow.refreshHotkeyFields();
						loadTimeMessage += "- Post show UI fixes: " + (System.currentTimeMillis() - thisTaskStart + "\n");
						
						loadTimeMessage += "Total Load Time: " + (System.currentTimeMillis() - baseStartTime);
						
						LOGGER.info(loadTimeMessage);
						
						
						LOGGER.info( "Hypnos finished loading." );
						UpdateChecker updater = new UpdateChecker();
						if ( updater.updateAvailable() ) {
							LOGGER.info( "Updates available" );
						}
					} 
					break;

					case WIN_XP:
					default: {
						LOGGER.log(Level.SEVERE, "Operating System not supported, exiting.");
						exit(ExitCode.UNSUPPORTED_OS);
					}
				}
				
				
			} else {
				boolean gotResponse = singleInstanceController.sendCommandsThroughSocket( commands );
				if ( commands.size() > 0 ) {
					originalOut.println ( "Commands sent to currently running Hypnos." );
					
				} else if ( gotResponse ) {
					singleInstanceController.sendCommandsThroughSocket( Arrays.asList(
						new SocketCommand ( SocketCommand.CommandType.CONTROL, SocketCommand.SHOW )
					));
					originalOut.println ( "Hypnos is already running, brought to front." );
					
				} else {
					FXUI.notifyUserHypnosNonResponsive();
				}
				
				System.exit ( 0 ); //We don't use Hypnos.exit here intentionally. 
			}
			
		} catch ( Exception e ) {
			LOGGER.log( Level.SEVERE, e.getClass() + ":  Exception caught at top level of Hypnos. Exiting.", e );
			exit ( ExitCode.UNKNOWN_ERROR );
		}
	}

	public static void main ( String[] args ) {
		launch( args ); //This calls start()
	}
}

