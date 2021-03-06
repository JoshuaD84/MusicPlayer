package net.joshuad.hypnos.fxui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import net.joshuad.hypnos.CurrentList.DefaultRepeatMode;
import net.joshuad.hypnos.CurrentList.DefaultShuffleMode;
import net.joshuad.hypnos.Hypnos;
import net.joshuad.hypnos.hotkeys.GlobalHotkeys;
import net.joshuad.hypnos.hotkeys.GlobalHotkeys.Hotkey;
import net.joshuad.hypnos.library.Library;
import net.joshuad.hypnos.library.Playlist;
import net.joshuad.hypnos.library.TagError;
import net.joshuad.hypnos.library.Track;
import net.joshuad.hypnos.hotkeys.HotkeyState;
import net.joshuad.hypnos.audio.AudioSystem;
import net.joshuad.hypnos.fxui.DraggedTrackContainer.DragSource;

public class SettingsWindow extends Stage {

	private static final Logger LOGGER = Logger.getLogger( SettingsWindow.class.getName() );
	
	private FXUI ui;
	private Library library;
	private GlobalHotkeys hotkeys;
	private AudioSystem audioSystem;
	
	private TabPane tabPane;
	private Tab globalHotkeysTab;
	
	private EnumMap <Hotkey, TextField> hotkeyFields = new EnumMap <Hotkey, TextField> ( Hotkey.class );
	
	private ChoiceBox <String> albumShuffleChoices;
	private ChoiceBox <String> albumRepeatChoices;
	private ChoiceBox <String> trackShuffleChoices;
	private ChoiceBox <String> trackRepeatChoices;
	private ChoiceBox <String> playlistShuffleChoices;
	private ChoiceBox <String> playlistRepeatChoices;
	
	private TextField userInput;
	private PasswordField passwordInput;
	
	private ToggleButton lightTheme, darkTheme;
	private ToggleGroup themeToggleGroup;
	
	private Hyperlink updateLink;
	
	private TableView <TagError> tagTable;
	
	private List <TextField> globalHotkeyFields = new ArrayList<> ();
	
	SettingsWindow( FXUI ui, Library library, GlobalHotkeys hotkeys, AudioSystem audioSystem ) {
		super();
		
		this.ui = ui;
		this.library = library;
		this.hotkeys = hotkeys;
		this.audioSystem = audioSystem;

		initModality( Modality.NONE );
		initOwner( ui.getMainStage() );		
		setWidth ( 700 );
		setHeight ( 650 );
		setTitle( "Config and Info" );
		Pane root = new Pane();
		Scene scene = new Scene( root );
		
		tabPane = new TabPane();
		
		Tab settingsTab = setupSettingsTab( root, ui );

		globalHotkeysTab = setupGlobalHotkeysTab( root );
		if ( hotkeys.isDisabled() ) {
			globalHotkeysTab.setDisable( true );
			globalHotkeysTab.setTooltip( new Tooltip ( hotkeys.getReasonDisabled() ) );
		}
		
		Tab hotkeysTab = setupHotkeysTab( root );
		Tab logTab = setupLogTab( root );
		Tab tagTab = setupTagTab( root );
		Tab lastFMTab = setupLastFMTab( root );
		Tab aboutTab = setupAboutTab( root ); 
		
		tabPane.getTabs().addAll( settingsTab, hotkeysTab, globalHotkeysTab, logTab, tagTab, lastFMTab, aboutTab );
		
		tabPane.getSelectionModel().selectedItemProperty().addListener(
		    new ChangeListener<Tab>() {
		        @Override
		        public void changed(ObservableValue<? extends Tab> observable, Tab oldValue, Tab newValue) {
		            if ( newValue == globalHotkeysTab ) {
		            	hotkeys.beginEditMode();
		            } else if ( oldValue == globalHotkeysTab ) {
		            	hotkeys.endEditMode();
		            }
		        }
		    }
		);
		
		this.showingProperty().addListener( ( obs, previousValue, isNowShowing ) -> {
			if ( isNowShowing ) {
				if ( tabPane.getSelectionModel().getSelectedItem() == globalHotkeysTab ) {
					hotkeys.beginEditMode();
				}
			} else {
				hotkeys.endEditMode();
			}
		});
		
		tabPane.prefWidthProperty().bind( root.widthProperty() );
		tabPane.prefHeightProperty().bind( root.heightProperty() );

		VBox primaryPane = new VBox();
		primaryPane.getChildren().addAll( tabPane );
		root.getChildren().add( primaryPane );
		setScene( scene );
		refreshHotkeyFields();
		
		setOnCloseRequest ( ( WindowEvent event ) -> {
			Hypnos.getPersister().saveHotkeys();
			Hypnos.getPersister().saveSettings();
		});
		
		try {
			getIcons().add( new Image( new FileInputStream ( Hypnos.getRootDirectory().resolve( "resources" + File.separator + "icon.png" ).toFile() ) ) );
		} catch ( FileNotFoundException e ) {
			LOGGER.log( Level.WARNING, "Unable to load program icon: resources/icon.png", new NullPointerException() );
		}
		
		this.setOnShowing( ( event ) -> { 
			String username = audioSystem.getLastFM().getUsername();
			String passwordMD5 = audioSystem.getLastFM().getPasswordMD5();
			if ( !username.isBlank( ) ) {
				userInput.setText( username );
				passwordInput.setText( passwordMD5 );
			}
		});
		
		scene.addEventFilter( KeyEvent.KEY_PRESSED, new EventHandler <KeyEvent>() {
			@Override
			public void handle ( KeyEvent e ) {
				if ( e.getCode() == KeyCode.ESCAPE ) {

					boolean editingGlobalHotkey = false;
					for ( TextField field : globalHotkeyFields ) {
						if ( field.isFocused() ) {
							editingGlobalHotkey = true;
						}
					}
					
					if ( !editingGlobalHotkey ) {
						close();
						e.consume();
					}
				}
			}
		});
	}	
	
	private Tab setupHotkeysTab ( Pane root ) {
		Tab hotkeyTab = new Tab ( "Hotkeys" );
		hotkeyTab.setClosable( false );
		VBox hotkeyPane = new VBox();
		hotkeyTab.setContent( hotkeyPane );
		hotkeyPane.setAlignment( Pos.CENTER );
		hotkeyPane.setPadding( new Insets ( 10 ) );
		
		Label headerLabel = new Label ( "Hotkeys" );
		headerLabel.setPadding( new Insets ( 0, 0, 10, 0 ) );
		headerLabel.setWrapText( true );
		headerLabel.setTextAlignment( TextAlignment.CENTER );
		headerLabel.setStyle( "-fx-font-size: 20px; -fx-font-weight: bold" );
		hotkeyPane.getChildren().add( headerLabel );
		
		TextArea hotkeyView = new TextArea();
		hotkeyView.setEditable( false );
		hotkeyView.prefHeightProperty().bind( root.heightProperty() );
		hotkeyView.setWrapText( false );
		hotkeyView.getStyleClass().add( "monospaced" );
		hotkeyView.setText( hotkeyText );
		
		hotkeyPane.getChildren().addAll( hotkeyView );
		
		return hotkeyTab;
	}
		
	
	private Tab setupGlobalHotkeysTab ( Pane root ) {
		
		GridPane globalContent = new GridPane();
		globalContent.setAlignment( Pos.TOP_CENTER );
		globalContent.setPadding( new Insets ( 10 ) );
		globalContent.setVgap( 2 );
				
		Tab hotkeysTab = new Tab ( "Global Hotkeys" );
		hotkeysTab.setClosable( false );
		hotkeysTab.setContent( globalContent );
		
		int row = 0;
	
		Label headerLabel = new Label ( "Global Hotkeys" );
		headerLabel.setPadding( new Insets ( 0, 0, 10, 0 ) );
		headerLabel.setWrapText( true );
		headerLabel.setTextAlignment( TextAlignment.CENTER );
		headerLabel.setStyle( "-fx-alignment: center; -fx-font-size: 20px; -fx-font-weight: bold" );
		globalContent.add( headerLabel, 0, row, 2, 1 );
		GridPane.setHalignment( headerLabel, HPos.CENTER );
		row++;
		
		Label descriptionLabel = new Label ( "These hotkeys will also work when Hypnos is minimized or out of focus." );
		descriptionLabel.setPadding( new Insets ( 0, 0, 20, 0 ) );
		descriptionLabel.setWrapText( true );
		descriptionLabel.setTextAlignment( TextAlignment.CENTER );
		globalContent.add( descriptionLabel, 0, row, 2, 1 );
		GridPane.setHalignment( descriptionLabel, HPos.CENTER );
		row++;
		
		for ( Hotkey hotkey : Hotkey.values() ) {
			Label label = new Label ( hotkey.getLabel() );
			GridPane.setHalignment( label, HPos.RIGHT );
			label.setPadding( new Insets ( 0, 20, 0, 0 ) );
			
			TextField field = new TextField ();
			field.setStyle( "-fx-alignment: center" ); //TODO: Put this in the stylesheet
			field.setPrefWidth( 200 );
			globalHotkeyFields.add ( field );
			
			field.setOnKeyPressed( ( KeyEvent keyEvent ) -> { 
				String hotkeyText = HotkeyState.getDisplayText( keyEvent );
				
				if ( keyEvent.getCode().equals ( KeyCode.UNDEFINED ) ) {
					//Do nothing, it's handled during key release
				
				} else if ( keyEvent.getCode().equals( KeyCode.ESCAPE ) ) {
					field.setText( "" );
					hotkeys.clearHotkey ( hotkey );
					
				} else if ( keyEvent.getCode().isModifierKey() ) {
					field.setText( hotkeyText );
					hotkeys.clearHotkey ( hotkey );
					
				} else if ( keyEvent.getCode().equals( KeyCode.TAB ) ) {
					//Do nothing, javafx automatically focus cycles
					
				} else {
					boolean registered = hotkeys.registerFXHotkey( hotkey, keyEvent );
					if ( registered ) {
						field.setText( hotkeyText );
						refreshHotkeyFields();
					}
				}

				field.positionCaret( hotkeyText.length() );
				keyEvent.consume();
			});
			
			field.addEventFilter(KeyEvent.KEY_TYPED, new EventHandler<KeyEvent>() {
			    @Override
			    public void handle(KeyEvent event) {
			    	event.consume();                    
			    }
			});
			
			field.focusedProperty().addListener( ( obs, oldVal, newVal ) -> {
				//This fixes a particular bug on linux
				//Have a hotkey (meta + X) registered with another program, then press 
				//1. Meta, 2. X, 3. Release Meta, 4. Release X. 
				// If you do that, you'll get the key area frozen at Meta +
				refreshHotkeyFields();
				//TODO: on linux I could use stop this to show a "registered with system already" thing
				//not sure if it'll work on configurations other than xubuntu
			} );
			
			field.setOnKeyReleased( ( KeyEvent keyEvent ) -> {
				if ( keyEvent.getCode().equals ( KeyCode.UNDEFINED ) ) {
					HotkeyState state = hotkeys.createJustPressedState ( keyEvent );
					boolean registered = hotkeys.registerHotkey( hotkey, state );
					if ( registered ) {
						field.setText( state.getDisplayText() );
						refreshHotkeyFields();
					}
				} 

				refreshHotkeyFields();
				field.positionCaret( 0 );
			});
			
			globalContent.add( label, 0, row );
			globalContent.add( field, 1, row );
			
			hotkeyFields.put( hotkey, field );
			
			row++;
		}
		
		Label clearHotkeyLabel = new Label ( "(Use ESC to erase a global hotkey)" );
		clearHotkeyLabel.setPadding( new Insets ( 20, 0, 20, 0 ) );
		clearHotkeyLabel.setWrapText( true );
		clearHotkeyLabel.setTextAlignment( TextAlignment.CENTER );
		globalContent.add( clearHotkeyLabel, 0, row, 2, 1 );
		GridPane.setHalignment( clearHotkeyLabel, HPos.CENTER );
		row++;
		
		if ( Hypnos.globalHotkeysDisabled() ) {
			Label disabledNote = new Label ( "Hotkeys are currently disabled by system. See the log for more info." );
			globalContent.add( disabledNote, 0, row, 2, 1 );
			disabledNote.setPadding( new Insets ( 0, 0, 20, 0 ) );
			GridPane.setHalignment( disabledNote, HPos.CENTER );
			row++;
		}
		
		return hotkeysTab;
	}
	
	public void updateSettingsBeforeWindowShown() {
		
		switch ( audioSystem.getCurrentList().getDefaultTrackRepeatMode() ) {
			case NO_CHANGE:
				trackRepeatChoices.getSelectionModel().select( 0 );
				break;
			case PLAY_ONCE:
				trackRepeatChoices.getSelectionModel().select( 1 );
				break;
			case REPEAT:
				trackRepeatChoices.getSelectionModel().select( 2 );
				break;
			
		}
		
		switch ( audioSystem.getCurrentList().getDefaultAlbumRepeatMode() ) {
			case NO_CHANGE:
				albumRepeatChoices.getSelectionModel().select( 0 );
				break;
			case PLAY_ONCE:
				albumRepeatChoices.getSelectionModel().select( 1 );
				break;
			case REPEAT:
				albumRepeatChoices.getSelectionModel().select( 2 );
				break;
			
		}
		
		switch ( audioSystem.getCurrentList().getDefaultPlaylistRepeatMode() ) {
			case NO_CHANGE:
				playlistRepeatChoices.getSelectionModel().select( 0 );
				break;
			case PLAY_ONCE:
				playlistRepeatChoices.getSelectionModel().select( 1 );
				break;
			case REPEAT:
				playlistRepeatChoices.getSelectionModel().select( 2 );
				break;
			
		}
		
		switch ( audioSystem.getCurrentList().getDefaultTrackShuffleMode() ) {
			case NO_CHANGE:
				trackShuffleChoices.getSelectionModel().select( 0 );
				break;
			case SEQUENTIAL:
				trackShuffleChoices.getSelectionModel().select( 1 );
				break;
			case SHUFFLE:
				trackShuffleChoices.getSelectionModel().select( 2 );
				break;
			
		}
		
		switch ( audioSystem.getCurrentList().getDefaultAlbumShuffleMode() ) {
			case NO_CHANGE:
				albumShuffleChoices.getSelectionModel().select( 0 );
				break;
			case SEQUENTIAL:
				albumShuffleChoices.getSelectionModel().select( 1 );
				break;
			case SHUFFLE:
				albumShuffleChoices.getSelectionModel().select( 2 );
				break;
			
		}
	
		switch ( audioSystem.getCurrentList().getDefaultPlaylistShuffleMode() ) {
			case NO_CHANGE:
				playlistShuffleChoices.getSelectionModel().select( 0 );
				break;
			case SEQUENTIAL:
				playlistShuffleChoices.getSelectionModel().select( 1 );
				break;
			case SHUFFLE:
				playlistShuffleChoices.getSelectionModel().select( 2 );
				break;
			
		}
		
		if ( ui.isDarkTheme() ) {
			themeToggleGroup.selectToggle( darkTheme );
		} else {
			themeToggleGroup.selectToggle( lightTheme );
		}
	}
	
	public void refreshHotkeyFields() {
		for ( Hotkey key : Hotkey.values() ) {
			TextField field = hotkeyFields.get( key );
			if ( field == null ) continue;
			field.setText( hotkeys.getDisplayText( key ) );
		}			
	}
	
	private Tab setupSettingsTab ( Pane root, FXUI ui ) {
		
		Tab settingsTab = new Tab ( "Settings" );
		settingsTab.setClosable( false );
		VBox settingsPane = new VBox( 20 );
		settingsTab.setContent ( settingsPane );
		settingsPane.setAlignment( Pos.TOP_CENTER );		
		settingsPane.setPadding( new Insets ( 10 ) );
		
		Label headerLabel = new Label ( "Settings" );
		headerLabel.setPadding( new Insets ( 0, 0, 10, 0 ) );
		headerLabel.setWrapText( true );
		headerLabel.setTextAlignment( TextAlignment.CENTER );
		headerLabel.setStyle( "-fx-font-size: 20px; -fx-font-weight: bold" );
		settingsPane.getChildren().add( headerLabel );

		Insets labelInsets = new Insets ( 10, 10, 10, 10 );
		Insets checkBoxInsets = new Insets ( 10, 10, 10, 10 );
		
		Label themeLabel = new Label ( "Theme: " );
		themeLabel.setPadding( labelInsets );
		
		lightTheme = new ToggleButton ( "Light" );
		darkTheme = new ToggleButton ( "Dark" );
		
		themeToggleGroup = new ToggleGroup();
		lightTheme.setToggleGroup( themeToggleGroup );
		darkTheme.setToggleGroup( themeToggleGroup );
		
		lightTheme.setPrefWidth( 150 );
		darkTheme.setPrefWidth( 150 );
		
		lightTheme.setSelected( true );
		
		themeToggleGroup.selectedToggleProperty().addListener( new ChangeListener <Toggle>() {
			public void changed ( ObservableValue <? extends Toggle> oldValue, Toggle toggle, Toggle newValue ) {
				if ( newValue == null ) {
					//Do nothing
				} else if ( newValue == lightTheme ) {
					ui.applyLightTheme();
					
				} else if ( newValue == darkTheme ) {
					ui.applyDarkTheme();
				}
			}
		});
		
		HBox themeBox = new HBox();
		themeBox.setAlignment( Pos.TOP_CENTER );	
		themeBox.getChildren().addAll( themeLabel, lightTheme, darkTheme );
		
		Label warnLabel = new Label ( "Warn before erasing unsaved playlists" );
		warnLabel.setPadding( labelInsets );
		
		CheckBox warnCheckBox = new CheckBox ();
		warnCheckBox.setPadding( checkBoxInsets );
		warnCheckBox.selectedProperty().bindBidirectional( ui.promptBeforeOverwriteProperty() );
				
		HBox warnBox = new HBox();
		warnBox.setAlignment( Pos.TOP_CENTER );
		warnBox.getChildren().addAll( warnCheckBox, warnLabel );
		
		Label updateInUILabel = new Label ( "Show update available notification in main window" );
		updateInUILabel.setPadding( labelInsets );
		
		CheckBox updateInUICheckBox = new CheckBox ();
		updateInUICheckBox.setPadding( checkBoxInsets );
		updateInUICheckBox.selectedProperty().bindBidirectional( ui.showUpdateAvailableInUIProperty() );
				
		HBox updateInUIBox = new HBox();
		updateInUIBox.setAlignment( Pos.TOP_CENTER );
		updateInUIBox.getChildren().addAll( updateInUICheckBox, updateInUILabel );
						
		GridPane shuffleGrid = new GridPane();
		shuffleGrid.setPadding( new Insets ( 0, 0, 0, 0 ) );
		shuffleGrid.setHgap( 15 );
		shuffleGrid.setVgap( 5 );
		
		int row = 0;
		
		Label shuffleLabel = new Label ( "Shuffle" );
		GridPane.setHalignment( shuffleLabel, HPos.CENTER );
		shuffleGrid.add( shuffleLabel, 1, row );
		
		Label repeatLabel = new Label ( "Repeat" );
		GridPane.setHalignment( repeatLabel, HPos.CENTER );
		shuffleGrid.add( repeatLabel, 2, row );

		row++;
		
		final ObservableList<String> shuffleOptions = FXCollections.observableArrayList( "No Change", "Sequential", "Shuffle" );
		final ObservableList<String> repeatOptions = FXCollections.observableArrayList( "No Change", "Play Once", "Repeat" );
		
		Label albumsLabel = new Label ( "Default setting for albums:" );
		GridPane.setHalignment( albumsLabel, HPos.RIGHT );
		shuffleGrid.add ( albumsLabel, 0, row );
		
		albumShuffleChoices = new ChoiceBox <String>( shuffleOptions );
		shuffleGrid.add ( albumShuffleChoices, 1, row );
		albumShuffleChoices.getSelectionModel().select( 1 );
		albumShuffleChoices.getSelectionModel().selectedIndexProperty().addListener( new ChangeListener<Number>() {
			@Override
			public void changed ( ObservableValue <? extends Number> observableValue, Number oldValue, Number newValue ) {
				switch ( newValue.intValue() ) {
					case 0: 
						audioSystem.getCurrentList().setDefaultAlbumShuffleMode ( DefaultShuffleMode.NO_CHANGE );
						break;
						
					case 1:
						audioSystem.getCurrentList().setDefaultAlbumShuffleMode ( DefaultShuffleMode.SEQUENTIAL );
						break;
					
					case 2:
						audioSystem.getCurrentList().setDefaultAlbumShuffleMode ( DefaultShuffleMode.SHUFFLE );
						break;
				}
			}
		});
		
		albumRepeatChoices = new ChoiceBox <String>( repeatOptions );
		shuffleGrid.add ( albumRepeatChoices, 2, row );
		albumRepeatChoices.getSelectionModel().select( 1 );
		albumRepeatChoices.getSelectionModel().selectedIndexProperty().addListener( new ChangeListener<Number>() {
			@Override
			public void changed ( ObservableValue <? extends Number> observableValue, Number oldValue, Number newValue ) {
				switch ( newValue.intValue() ) {
					case 0: 
						audioSystem.getCurrentList().setDefaultAlbumRepeatMode( DefaultRepeatMode.NO_CHANGE );
						break;
						
					case 1:
						audioSystem.getCurrentList().setDefaultAlbumRepeatMode ( DefaultRepeatMode.PLAY_ONCE );
						break;
					
					case 2:
						audioSystem.getCurrentList().setDefaultAlbumRepeatMode ( DefaultRepeatMode.REPEAT );
						break;
				}
			}
		});
		
		row++;
		
		Label trackLabel = new Label ( "Default setting for tracks:" );
		GridPane.setHalignment( trackLabel, HPos.RIGHT );
		shuffleGrid.add ( trackLabel, 0, row );
		
		trackShuffleChoices = new ChoiceBox <String>( shuffleOptions );
		shuffleGrid.add ( trackShuffleChoices, 1, row );
		trackShuffleChoices.getSelectionModel().select( 0 );
		trackShuffleChoices.getSelectionModel().selectedIndexProperty().addListener( new ChangeListener<Number>() {
			@Override
			public void changed ( ObservableValue <? extends Number> observableValue, Number oldValue, Number newValue ) {
				switch ( newValue.intValue() ) {
					case 0: 
						audioSystem.getCurrentList().setDefaultTrackShuffleMode( DefaultShuffleMode.NO_CHANGE );
						break;
						
					case 1:
						audioSystem.getCurrentList().setDefaultTrackShuffleMode ( DefaultShuffleMode.SEQUENTIAL );
						break;
					
					case 2:
						audioSystem.getCurrentList().setDefaultTrackShuffleMode ( DefaultShuffleMode.SHUFFLE );
						break;
				}
			}
		});
		
		trackRepeatChoices = new ChoiceBox <String>( repeatOptions );
		shuffleGrid.add ( trackRepeatChoices, 2, row );
		trackRepeatChoices.getSelectionModel().select( 0 );
		trackRepeatChoices.getSelectionModel().selectedIndexProperty().addListener( new ChangeListener<Number>() {
			@Override
			public void changed ( ObservableValue <? extends Number> observableValue, Number oldValue, Number newValue ) {
				switch ( newValue.intValue() ) {
					case 0: 
						audioSystem.getCurrentList().setDefaultTrackRepeatMode( DefaultRepeatMode.NO_CHANGE );
						break;
						
					case 1:
						audioSystem.getCurrentList().setDefaultTrackRepeatMode ( DefaultRepeatMode.PLAY_ONCE );
						break;
					
					case 2:
						audioSystem.getCurrentList().setDefaultTrackRepeatMode ( DefaultRepeatMode.REPEAT );
						break;
				}
			}
		});
		
		row++;
		
		Label playlistLabel = new Label ( "Default setting for playlists:" );
		GridPane.setHalignment( playlistLabel, HPos.RIGHT );
		shuffleGrid.add ( playlistLabel, 0, row );
		
		playlistShuffleChoices = new ChoiceBox <String>( shuffleOptions );
		shuffleGrid.add ( playlistShuffleChoices, 1, row );
		playlistShuffleChoices.getSelectionModel().select( 2 );
		playlistShuffleChoices.getSelectionModel().selectedIndexProperty().addListener( new ChangeListener<Number>() {
			@Override
			public void changed ( ObservableValue <? extends Number> observableValue, Number oldValue, Number newValue ) {
				switch ( newValue.intValue() ) {
					case 0: 
						audioSystem.getCurrentList().setDefaultPlaylistShuffleMode( DefaultShuffleMode.NO_CHANGE );
						break;
						
					case 1:
						audioSystem.getCurrentList().setDefaultPlaylistShuffleMode ( DefaultShuffleMode.SEQUENTIAL );
						break;
					
					case 2:
						audioSystem.getCurrentList().setDefaultPlaylistShuffleMode ( DefaultShuffleMode.SHUFFLE );
						break;
				}
			}
		});
		
		playlistRepeatChoices = new ChoiceBox <String>( repeatOptions );
		shuffleGrid.add ( playlistRepeatChoices, 2, row );
		playlistRepeatChoices.getSelectionModel().select( 2 );
		playlistRepeatChoices.getSelectionModel().selectedIndexProperty().addListener( new ChangeListener<Number>() {
			@Override
			public void changed ( ObservableValue <? extends Number> observableValue, Number oldValue, Number newValue ) {
				switch ( newValue.intValue() ) {
					case 0: 
						audioSystem.getCurrentList().setDefaultPlaylistRepeatMode( DefaultRepeatMode.NO_CHANGE );
						break;
						
					case 1:
						audioSystem.getCurrentList().setDefaultPlaylistRepeatMode ( DefaultRepeatMode.PLAY_ONCE );
						break;
					
					case 2:
						audioSystem.getCurrentList().setDefaultPlaylistRepeatMode ( DefaultRepeatMode.REPEAT );
						break;
				}
			}
		});
		
		albumShuffleChoices.getStyleClass().add("shuffleRepeatChoices");
		albumRepeatChoices.getStyleClass().add("shuffleRepeatChoices");
		trackShuffleChoices.getStyleClass().add("shuffleRepeatChoices");
		trackRepeatChoices.getStyleClass().add("shuffleRepeatChoices");
		playlistShuffleChoices.getStyleClass().add("shuffleRepeatChoices");
		playlistRepeatChoices.getStyleClass().add("shuffleRepeatChoices");
		row++;

		shuffleGrid.setAlignment( Pos.TOP_CENTER );	
		
		Label showSystemTrayLabel = new Label ( "Show in System Tray" );
		showSystemTrayLabel.setPadding( labelInsets );
		
		CheckBox showSystemTrayCheckBox = new CheckBox ();
		showSystemTrayCheckBox.setPadding( checkBoxInsets );
		showSystemTrayCheckBox.selectedProperty().bindBidirectional( ui.showSystemTrayProperty() );
		
		Label closeToTrayLabel = new Label ( "Close to System Tray" );
		closeToTrayLabel.setPadding( labelInsets );
		
		CheckBox closeToTrayCheckBox = new CheckBox ();
		closeToTrayCheckBox.setPadding( checkBoxInsets );
		closeToTrayCheckBox.selectedProperty().bindBidirectional( ui.closeToSystemTrayProperty() );
		
		Label minToTrayLabel = new Label ( "Minimize to System Tray" );
		minToTrayLabel.setPadding( labelInsets );
		
		CheckBox minToTrayCheckBox = new CheckBox ();
		minToTrayCheckBox.setPadding( checkBoxInsets );
		minToTrayCheckBox.selectedProperty().bindBidirectional( ui.minimizeToSystemTrayProperty() );
		
		if ( ui.getTrayIcon().systemTraySupportedProperty().get() ) {
			ui.showSystemTrayProperty().addListener( ( ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue ) -> {
				if ( newValue ) {
					closeToTrayLabel.setDisable( false );
					closeToTrayCheckBox.setDisable( false );
					minToTrayLabel.setDisable( false );
					minToTrayCheckBox.setDisable( false );
				} else {
					closeToTrayLabel.setDisable( true );
					closeToTrayCheckBox.setSelected( false );
					closeToTrayCheckBox.setDisable( true );
					minToTrayLabel.setDisable( true );
					minToTrayCheckBox.setSelected( false );
					minToTrayCheckBox.setDisable( true );
				}
			});
			
			
			if ( ui.showSystemTrayProperty().get() ) {
				closeToTrayLabel.setDisable( false );
				closeToTrayCheckBox.setDisable( false );
				minToTrayLabel.setDisable( false );
				minToTrayCheckBox.setDisable( false );
			} else {
				closeToTrayLabel.setDisable( true );
				closeToTrayCheckBox.setSelected( false );
				closeToTrayCheckBox.setDisable( true );
				minToTrayLabel.setDisable( true );
				minToTrayCheckBox.setSelected( false );
				minToTrayCheckBox.setDisable( true );
			}
		}
				
		HBox systemTrayBox = new HBox();
		systemTrayBox.setAlignment( Pos.TOP_CENTER );
		systemTrayBox.getChildren().addAll( showSystemTrayCheckBox, showSystemTrayLabel, 
			minToTrayCheckBox, minToTrayLabel, closeToTrayCheckBox, closeToTrayLabel );

		//We create this container so we can disable systemTrayBox but still have a tooltip on it
		HBox systemTrayBoxContainer = new HBox(); 
		systemTrayBoxContainer.setAlignment( Pos.TOP_CENTER );
		systemTrayBoxContainer.getChildren().add( systemTrayBox );
		
		settingsPane.getChildren().addAll( shuffleGrid, themeBox, warnBox, updateInUIBox, systemTrayBoxContainer );
		
		if ( !ui.getTrayIcon().isSupported() ) {
			systemTrayBox.setDisable( true );
			Tooltip.install( systemTrayBoxContainer, new Tooltip ( 
				"System Tray not supported for your desktop environment, sorry!" ) 
			);
		}
		
		return settingsTab;
	}
	
	private Tab setupLogTab( Pane root ) {
		Tab logTab = new Tab ( "Log" );
		logTab.setClosable( false );
		VBox logPane = new VBox();
		logTab.setContent( logPane );
		logPane.setAlignment( Pos.CENTER );
		logPane.setPadding( new Insets( 10 ) );
		
		Label headerLabel = new Label( "Log" );
		headerLabel.setPadding( new Insets( 0, 0, 10, 0 ) );
		headerLabel.setWrapText( true );
		headerLabel.setTextAlignment( TextAlignment.CENTER );
		headerLabel.setStyle( "-fx-font-size: 20px; -fx-font-weight: bold" );
		logPane.getChildren().add( headerLabel );

		TextArea logView = new TextArea();
		logView.setEditable( false );
		logView.prefHeightProperty().bind( root.heightProperty() );
		logView.getStyleClass().add( "monospaced" );
		logView.setWrapText( true );
		
		Thread logReader = new Thread( () -> {
			try ( 
				BufferedReader reader = new BufferedReader( new FileReader( Hypnos.getLogFile().toFile() ) ); 
			){
				while ( true ) {
					String line = reader.readLine();
					if ( line == null ) {
						try {
							Thread.sleep( 500 );
						} catch ( InterruptedException ie ) {
						}
					} else {
						Platform.runLater( () -> {
							logView.appendText( line + "\n" );
						});
					}
				}
			} catch ( Exception e ) {
				LOGGER.log( Level.WARNING, "Unable to link log window to log file.", e );
			}
		});
		
		logReader.setName( "Log UI Text Loader" );
		logReader.setDaemon( true );
		logReader.start();
		
		Button exportButton = new Button ( "Export Log File" );
		
		exportButton.setOnAction( ( ActionEvent event ) -> {
			
			FileChooser fileChooser = new FileChooser();
			FileChooser.ExtensionFilter fileExtensions = new FileChooser.ExtensionFilter( 
				"Log Files", Arrays.asList( "*.log", "*.txt" ) );
			
			fileChooser.getExtensionFilters().add( fileExtensions );
			fileChooser.setTitle( "Export Log File" );
			fileChooser.setInitialFileName( "hypnos.log" );
			File targetFile = fileChooser.showSaveDialog( this );
			
			if ( targetFile == null ) return; 
	
			if ( !targetFile.toString().toLowerCase().endsWith(".log") && !targetFile.toString().toLowerCase().endsWith(".txt") ) {
				targetFile = targetFile.toPath().resolveSibling ( targetFile.getName() + ".log" ).toFile();
			}
			
			try {
				Files.copy( Hypnos.getLogFile(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
			} catch ( Exception ex ) {
				ui.notifyUserError ( ex.getClass().getCanonicalName() + ": Unable to export log file." );
				LOGGER.log( Level.WARNING, "Unable to export log file.", ex );
			}
		});
		
		Button previousLog = new Button ( "View Last Log" );
		previousLog.setOnAction( e -> ui.openFileNatively ( Hypnos.getLogFileBackup() ) );
			
		HBox controlBox = new HBox();
		controlBox.setAlignment( Pos.TOP_CENTER );
		controlBox.getChildren().addAll( exportButton, previousLog );
		controlBox.setPadding( new Insets ( 5, 0, 0, 0 ) );
		
		logPane.getChildren().addAll( logView, controlBox );
		
		return logTab;
	}
	
	private Tab setupTagTab ( Pane root ) {
		Tab tab = new Tab ( "Tags" );
		tab.setClosable( false );
		VBox pane = new VBox();
		tab.setContent ( pane );
		pane.setAlignment( Pos.TOP_CENTER );
		pane.setPadding( new Insets ( 10 ) );
		
		Label headerLabel = new Label ( "Tag Errors" );
		headerLabel.setPadding( new Insets ( 0, 0, 10, 0 ) );
		headerLabel.setWrapText( true );
		headerLabel.setTextAlignment( TextAlignment.CENTER );
		headerLabel.setStyle( "-fx-font-size: 20px; -fx-font-weight: bold" );
		pane.getChildren().add( headerLabel );
		
		TableColumn<TagError, String> pathColumn = new TableColumn<TagError, String> ( "Location" );
		TableColumn<TagError, String> filenameColumn = new TableColumn<TagError, String> ( "Filename" );
		TableColumn<TagError, String> messageColumn = new TableColumn<TagError, String> ( "Error Message" );

		pathColumn.setCellValueFactory( new PropertyValueFactory <TagError, String>( "FolderDisplay" ) );
		filenameColumn.setCellValueFactory( new PropertyValueFactory <TagError, String>( "FilenameDisplay" ) );
		messageColumn.setCellValueFactory( new PropertyValueFactory <TagError, String>( "Message" ) );

		pathColumn.setMaxWidth( 30000 );
		filenameColumn.setMaxWidth ( 40000 );
		messageColumn.setMaxWidth( 30000 );
		
		pathColumn.setReorderable( false );
		filenameColumn.setReorderable ( false );
		messageColumn.setReorderable( false );

		tagTable = new TableView <TagError> ();
		tagTable.getColumns().addAll( pathColumn, filenameColumn, messageColumn );
		tagTable.getSortOrder().addAll( pathColumn, messageColumn, filenameColumn );
		tagTable.setPlaceholder( new Label( "There are no tag errors." ) );

		library.getTagErrorsSorted().comparatorProperty().bind( tagTable.comparatorProperty() );
		
		tagTable.setEditable( false );
		tagTable.getSelectionModel().setSelectionMode( SelectionMode.MULTIPLE );
		tagTable.setColumnResizePolicy( TableView.CONSTRAINED_RESIZE_POLICY );
		tagTable.setItems( library.getTagErrorsSorted() );
		tagTable.prefWidthProperty().bind( pane.widthProperty() );
		tagTable.prefHeightProperty().bind( pane.heightProperty() );
		
		tagTable.getSelectionModel().selectedItemProperty().addListener( ( obs, oldSelection, newSelection ) -> {
			if ( newSelection != null ) {
				ui.trackSelected ( newSelection.getTrack() );
			}
		});
		
		Menu lastFMMenu = new Menu( "LastFM" );
		MenuItem loveMenuItem = new MenuItem ( "Love" );
		MenuItem unloveMenuItem = new MenuItem ( "Unlove" );
		MenuItem scrobbleMenuItem = new MenuItem ( "Scrobble" );
		lastFMMenu.getItems().addAll ( loveMenuItem, unloveMenuItem, scrobbleMenuItem );
		lastFMMenu.setVisible ( false );
		lastFMMenu.visibleProperty().bind( ui.showLastFMWidgets );
		
		loveMenuItem.setOnAction( ( event ) -> {
			ui.audioSystem.getLastFM().loveTrack( tagTable.getSelectionModel().getSelectedItem().getTrack() );
		});
		
		unloveMenuItem.setOnAction( ( event ) -> {
			ui.audioSystem.getLastFM().unloveTrack( tagTable.getSelectionModel().getSelectedItem().getTrack() );
		});
		
		scrobbleMenuItem.setOnAction( ( event ) -> {
			ui.audioSystem.getLastFM().scrobbleTrack( tagTable.getSelectionModel().getSelectedItem().getTrack() );
		});
		
		ContextMenu contextMenu = new ContextMenu();
		MenuItem playMenuItem = new MenuItem( "Play" );
		MenuItem playNextMenuItem = new MenuItem( "Play Next" );
		MenuItem appendMenuItem = new MenuItem( "Append" );
		MenuItem enqueueMenuItem = new MenuItem( "Enqueue" );
		MenuItem editTagMenuItem = new MenuItem( "Edit Tag(s)" );
		MenuItem infoMenuItem = new MenuItem( "Info" );
		MenuItem lyricsMenuItem = new MenuItem( "Lyrics" );
		MenuItem goToAlbumMenuItem = new MenuItem( "Go to Album" );
		MenuItem browseMenuItem = new MenuItem( "Browse Folder" );
		Menu addToPlaylistMenuItem = new Menu( "Add to Playlist" );
		contextMenu.getItems().addAll( 
				playMenuItem, playNextMenuItem, appendMenuItem, enqueueMenuItem, 
				editTagMenuItem, infoMenuItem, lyricsMenuItem, goToAlbumMenuItem, 
				browseMenuItem, addToPlaylistMenuItem, lastFMMenu );
		
		MenuItem newPlaylistButton = new MenuItem( "<New>" );

		addToPlaylistMenuItem.getItems().add( newPlaylistButton );

		newPlaylistButton.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent e ) {
				List <Track> tracks = new ArrayList <Track> ();
				
				for ( TagError error : tagTable.getSelectionModel().getSelectedItems() ) {
					tracks.add( error.getTrack() );
				}
				
				ui.promptAndSavePlaylist ( tracks );
			}
		});

		EventHandler<ActionEvent> addToPlaylistHandler = new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				List <Track> tracks = new ArrayList <Track> ();
				
				for ( TagError error : tagTable.getSelectionModel().getSelectedItems() ) {
					tracks.add( error.getTrack() );
				}
				
				Playlist playlist = (Playlist) ((MenuItem) event.getSource()).getUserData();
				ui.addToPlaylist ( tracks, playlist );
			}
		};

		library.getPlaylistsSorted().addListener( ( ListChangeListener.Change <? extends Playlist> change ) -> {
			ui.updatePlaylistMenuItems( addToPlaylistMenuItem.getItems(), addToPlaylistHandler );
		} );

		ui.updatePlaylistMenuItems( addToPlaylistMenuItem.getItems(), addToPlaylistHandler );
		
		playMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				List <Track> tracks = new ArrayList <Track> ();
				
				for ( TagError error : tagTable.getSelectionModel().getSelectedItems() ) {
					tracks.add( error.getTrack() );
				}
				
				if ( tracks.size() == 1 ) {
					audioSystem.playItems( tracks );
					
				} else if ( tracks.size() > 1 ) {
					if ( ui.okToReplaceCurrentList() ) {
						audioSystem.playItems( tracks );
					}
				}
			}
		});
		
		playNextMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				List <Track> tracks = new ArrayList <Track> ();
				
				for ( TagError error : tagTable.getSelectionModel().getSelectedItems() ) {
					tracks.add( error.getTrack() );
				}
				
				if ( tracks.size() == 1 ) {
					audioSystem.getQueue().queueAllTracks( tracks, 0 );
					
				} 
			}
		});

		appendMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				List <Track> tracks = new ArrayList <Track> ();
				
				for ( TagError error : tagTable.getSelectionModel().getSelectedItems() ) {
					tracks.add( error.getTrack() );
				}
				
				audioSystem.getCurrentList().appendTracks ( tracks );
			}
		});
		
		enqueueMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				List <Track> tracks = new ArrayList <Track> ();
				
				for ( TagError error : tagTable.getSelectionModel().getSelectedItems() ) {
					tracks.add( error.getTrack() );
				}
				
				audioSystem.getQueue().queueAllTracks( tracks );
			}
		});
		
		editTagMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				List <Track> tracks = new ArrayList <Track> ();
				
				for ( TagError error : tagTable.getSelectionModel().getSelectedItems() ) {
					if ( error != null ) {
						tracks.add( error.getTrack() );
					}
				}
				
				ui.tagWindow.setTracks( tracks, null );
				ui.tagWindow.show();
			}
		});
		
		
		infoMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				ui.trackInfoWindow.setTrack( tagTable.getSelectionModel().getSelectedItem().getTrack() );
				ui.trackInfoWindow.show();
			}
		});
		
		lyricsMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				ui.lyricsWindow.setTrack( tagTable.getSelectionModel().getSelectedItem().getTrack() );
				ui.lyricsWindow.show();
			}
		});
		
		goToAlbumMenuItem.setOnAction( ( event ) -> {
			ui.goToAlbumOfTrack ( tagTable.getSelectionModel().getSelectedItem().getTrack() );
		});

		browseMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			// PENDING: This is the better way, once openjdk and openjfx supports
			// it: getHostServices().showDocument(file.toURI().toString());
			@Override
			public void handle ( ActionEvent event ) {
				ui.openFileBrowser( tagTable.getSelectionModel().getSelectedItem().getPath() );
			}
		});

		tagTable.setOnKeyPressed( ( KeyEvent e ) -> {
			if ( e.getCode() == KeyCode.ESCAPE 
			&& !e.isAltDown() && !e.isControlDown() && !e.isShiftDown() && !e.isMetaDown() ) {
				tagTable.getSelectionModel().clearSelection();
				
			} else if ( e.getCode() == KeyCode.L
			&& !e.isAltDown() && !e.isControlDown() && !e.isShiftDown() && !e.isMetaDown() ) {
				lyricsMenuItem.fire();
				
			} else if ( e.getCode() == KeyCode.Q 
			&& !e.isAltDown() && !e.isControlDown() && !e.isShiftDown() && !e.isMetaDown() ) {
				enqueueMenuItem.fire();
				
			} else if ( e.getCode() == KeyCode.G && e.isControlDown()
			&& !e.isAltDown() && !e.isShiftDown() && !e.isMetaDown() ) {
				goToAlbumMenuItem.fire();
				
			} else if ( e.getCode() == KeyCode.Q && e.isShiftDown()
			&& !e.isAltDown() && !e.isControlDown()  && !e.isMetaDown() ) {
				playNextMenuItem.fire();
							
			} else if ( e.getCode() == KeyCode.F2 
			&& !e.isAltDown() && !e.isControlDown() && !e.isShiftDown() && !e.isMetaDown() ) {
				editTagMenuItem.fire();
				
			} else if ( e.getCode() == KeyCode.F3
			&& !e.isControlDown() && !e.isAltDown() && !e.isShiftDown() && !e.isMetaDown() ) {
				infoMenuItem.fire();
				e.consume();
				
			} else if ( e.getCode() == KeyCode.F4
			&& !e.isControlDown() && !e.isAltDown() && !e.isShiftDown() && !e.isMetaDown() ) {
				browseMenuItem.fire();
				e.consume();
				
			} else if ( e.getCode() == KeyCode.ENTER
			&& !e.isAltDown() && !e.isControlDown() && !e.isShiftDown() && !e.isMetaDown() ) {
				playMenuItem.fire();
				e.consume();
				
			} else if ( e.getCode() == KeyCode.ENTER && e.isShiftDown()
			&& !e.isAltDown() && !e.isControlDown() && !e.isMetaDown() ) {
				List <TagError> errors = tagTable.getSelectionModel().getSelectedItems();
				List <Track> tracks = new ArrayList<> ();
				
				for ( TagError error : errors ) tracks.add ( error.getTrack() );
				
				ui.audioSystem.getCurrentList().insertTracks( 0, tracks );
				e.consume();
				
			} else if ( e.getCode() == KeyCode.ENTER && e.isControlDown() 
			&& !e.isShiftDown() && !e.isAltDown() && !e.isMetaDown() ) {
				appendMenuItem.fire();
				e.consume();
			}
		});
		
		tagTable.setRowFactory( tv -> {
			TableRow <TagError> row = new TableRow <>();

			row.itemProperty().addListener( (obs, oldValue, newValue ) -> {
				if ( newValue != null ) {
					row.setContextMenu( contextMenu );
				} else {
					row.setContextMenu( null );
				}
			});
			
			row.setOnContextMenuRequested( event -> { 
				goToAlbumMenuItem.setDisable( row.getItem().getTrack().getAlbum() == null );
			});
			
			row.setOnMouseClicked( event -> {
				if ( event.getClickCount() == 2 && (!row.isEmpty()) ) {
					ui.tagWindow.setTracks( Arrays.asList ( row.getItem().getTrack() ), null );
					ui.tagWindow.show();
				}
			});
			
			row.setOnDragDetected( event -> {
				if ( !row.isEmpty() ) {
					ArrayList <Integer> indices = new ArrayList <Integer>( tagTable.getSelectionModel().getSelectedIndices() );
					ArrayList <Track> tracks = new ArrayList <Track>( );
					for ( Integer index : indices ) {
						TagError error = tagTable.getItems().get( index );
						if ( error != null ) {
							tracks.add( error.getTrack() );
						}
					}
					DraggedTrackContainer dragObject = new DraggedTrackContainer( indices, tracks, null, null, null, DragSource.TAG_ERROR_LIST );
					Dragboard db = row.startDragAndDrop( TransferMode.COPY );
					db.setDragView( row.snapshot( null, null ) );
					ClipboardContent cc = new ClipboardContent();
					cc.put( FXUI.DRAGGED_TRACKS, dragObject );
					db.setContent( cc );
					event.consume();
				}
			});
			
			return row;
		});
		
		pane.getChildren().addAll( tagTable );
		
		return tab;
	}
	
	private Tab setupLastFMTab( Pane root ) {
		
		Label headerLabel = new Label( "LastFM" );
		headerLabel.setPadding( new Insets( 0, 0, 10, 0 ) );
		headerLabel.setWrapText( true );
		headerLabel.setTextAlignment( TextAlignment.CENTER );
		headerLabel.setStyle( "-fx-font-size: 20px; -fx-font-weight: bold" );
		
		userInput = new TextField();
		passwordInput = new PasswordField();
		CheckBox scrobbleCheckbox = new CheckBox();
		CheckBox showInUICheckbox = new CheckBox();
		Label scrobbleLabel = new Label ( "Scrobble Automatically:" );
		Label scrobbleTime = new Label ( "At Beginning" );
		Label showInUILabel = new Label ( "Show LastFM in UI:" );
		scrobbleLabel.setPadding( new Insets ( 0, 0, 0, 60 ) );
		scrobbleTime.setPadding( new Insets ( 0, 0, 0, 60 ) );
		showInUILabel.setPadding( new Insets ( 0, 0, 0, 60 ) );
		
		Slider scrobbleTimeSlider = new Slider ( 0, 1, 0 );
		scrobbleTimeSlider.setMajorTickUnit( .25 );
		scrobbleTimeSlider.setMinorTickCount( 0 );
		scrobbleTimeSlider.valueProperty().addListener( (observable, oldValue, newValue) -> {
			if ( newValue.doubleValue() == 0 ) {
				scrobbleTime.setText( "At Beginning" );
				audioSystem.setScrobbleTime ( 0 );
			} else if ( newValue.doubleValue() == 1 ) {
				scrobbleTime.setText( "After Finish" );
				audioSystem.setScrobbleTime ( 1d );
			} else {
				scrobbleTime.setText( "After playing " + new DecimalFormat( "##%").format ( newValue.doubleValue() ) );
				audioSystem.setScrobbleTime ( newValue.doubleValue() );
			}
		});
		
		scrobbleCheckbox.selectedProperty().bindBidirectional( audioSystem.doLastFMScrobbleProperty() );
		showInUICheckbox.selectedProperty().bindBidirectional( ui.showLastFMWidgets );
		scrobbleTimeSlider.valueProperty().bindBidirectional( audioSystem.scrobbleTimeProperty() );

		Button connectButton = new Button( "Connect" );
		connectButton.setOnAction( (ActionEvent e) -> {
			audioSystem.getLastFM().setCredentials( userInput.getText(), passwordInput.getText() );
			audioSystem.getLastFM().connect();
		});
		
		Button disconnectButton = new Button( "Disconnect" );
		disconnectButton.setOnAction( (ActionEvent e) -> {
			audioSystem.getLastFM().disconnectAndForgetCredentials();
			userInput.clear();
			passwordInput.clear();
		});
		
		HBox connectPane = new HBox();
		connectPane.setAlignment( Pos.CENTER );
		connectPane.getChildren().addAll( connectButton, disconnectButton );
		GridPane.setHalignment( connectPane, HPos.CENTER );
		
		GridPane loginPane = new GridPane();
		loginPane.setHgap( 5 );
		loginPane.setVgap( 5 );
		loginPane.add( new Label ( "Username:"), 0, 0 );
		loginPane.add( userInput, 1, 0 );
		loginPane.add( new Label ( "Password:"), 0, 1 );
		loginPane.add( passwordInput, 1, 1 );
		
		loginPane.add( connectPane, 0, 2, 2, 1 );

		loginPane.add( showInUILabel, 2, 0 );
		loginPane.add( showInUICheckbox, 3, 0 );
		
		loginPane.add( scrobbleLabel, 2, 1 );
		loginPane.add( scrobbleCheckbox, 3, 1 );

		loginPane.add( scrobbleTime, 2, 2 );
		loginPane.add( scrobbleTimeSlider, 3, 2 );
		
		TextArea logView = new TextArea();
		logView.setEditable( false );
		logView.setWrapText( true );
		logView.prefHeightProperty().bind( root.heightProperty() );
		logView.prefWidthProperty().bind( root.widthProperty() );
		logView.getStyleClass().add( "monospaced" );
		
		Timeline lastFMUpdater = new Timeline();
		lastFMUpdater.setCycleCount( Timeline.INDEFINITE );
		KeyFrame updateFrame = new KeyFrame( Duration.millis(1000), ae -> {
			String string = audioSystem.getLastFM().getLog().toString();
			if ( !string.equals( logView.getText() ) ) {
				logView.setText( string );
			}
		});
		
		lastFMUpdater.getKeyFrames().add( updateFrame );
		lastFMUpdater.play();

		Tab lastFMTab = new Tab ( "LastFM" );
		lastFMTab.setClosable( false );
		
		VBox logPane = new VBox();
		logPane.setAlignment( Pos.TOP_CENTER );
		lastFMTab.setContent( logPane );
		logPane.setPadding( new Insets( 10 ) );
		logPane.setSpacing( 20 );
		logPane.prefHeightProperty().bind( root.heightProperty() );
		logPane.prefWidthProperty().bind( root.widthProperty() );
		
		Hyperlink lastFMLink = new Hyperlink ( "last.fm" );
		lastFMLink.setStyle( "-fx-text-fill: #0A95C8" );
		lastFMLink.setOnAction( e-> ui.openWebBrowser( "http://last.fm" ) );
		
		logPane.getChildren().addAll( headerLabel, loginPane, logView, lastFMLink );
		
		return lastFMTab;
	}
	
	private Tab setupAboutTab ( Pane root ) {
		Tab aboutTab = new Tab ( "About" );
		aboutTab.setClosable( false );
		VBox aboutPane = new VBox();
		aboutTab.setContent( aboutPane );
		aboutPane.setStyle( "-fx-background-color: wheat" );
		aboutPane.getStyleClass().add( "aboutPaneBack" );
		
		aboutPane.setAlignment( Pos.CENTER );
		Label name = new Label ( "Hypnos Music Player" );
		name.setStyle( "-fx-font-size: 36px; -fx-text-fill: #020202" );
		name.getStyleClass().add( "aboutPaneText" );
		
		String url = "http://www.hypnosplayer.org";
		Hyperlink website = new Hyperlink ( url );
		website.setTooltip( new Tooltip ( url ) );
		website.setOnAction( event -> ui.openWebBrowser( url ) );
		website.setStyle( "-fx-font-size: 20px; -fx-text-fill: #0A95C8" );
		aboutPane.getStyleClass().add( "aboutPaneURL" );

		String labelString = Hypnos.getVersion() + ", Build: " + Hypnos.getBuild() + "\n" + Hypnos.getBuildDate() ;
		Label versionNumber = new Label ( labelString );
		versionNumber.setTextAlignment( TextAlignment.CENTER );
		versionNumber.setStyle( "-fx-font-size: 16px; -fx-text-fill: #020202" );
		versionNumber.setPadding( new Insets ( 0, 0, 20, 0 ) );
		versionNumber.getStyleClass().add( "aboutPaneText" );
		
		Image image = null;
		try {
			image = new Image( new FileInputStream ( Hypnos.getRootDirectory().resolve( "resources" + File.separator + "icon.png" ).toFile() ) );
		} catch ( Exception e1 ) {
			LOGGER.log( Level.INFO, "Unable to load hypnos icon for settings -> about tab.", e1 );
		}
		ImageView logo = new ImageView( image );
		logo.setFitWidth( 200 );
		logo.setPreserveRatio( true );
		logo.setSmooth( true );
		logo.setCache( true );
        
		HBox authorBox = new HBox();
		authorBox.setAlignment( Pos.CENTER );
		authorBox.setPadding ( new Insets ( 20, 0, 0, 0 ) );
		authorBox.setStyle( "-fx-font-size: 16px; -fx-background-color: transparent;" );
		Label authorLabel = new Label ( "Author:" );
		authorLabel.getStyleClass().add( "aboutPaneText" );
		authorLabel.setStyle( "-fx-text-fill: #020202" );
		Hyperlink authorLink = new Hyperlink ( "Joshua Hartwell" );
		
		String authorURL = "http://joshuad.net";

		authorLink.setTooltip( new Tooltip ( authorURL ) );
		
		authorLink.setStyle( "-fx-text-fill: #0A95C8" );
		authorLink.setOnAction( ( ActionEvent e ) -> {
			ui.openWebBrowser( authorURL );
		});
		authorLink.getStyleClass().add( "aboutPaneURL" );
		
		authorBox.getChildren().addAll( authorLabel, authorLink );
		
		
		HBox sourceBox = new HBox();
		sourceBox.setStyle( "-fx-background-color: transparent" );
		sourceBox.setAlignment( Pos.CENTER );
		sourceBox.setStyle( "-fx-background-color: transparent" );
		Label sourceLabel = new Label ( "Source Code:" );
		sourceLabel.setStyle( "-fx-text-fill: #020202" );
		sourceLabel.getStyleClass().add( "aboutPaneText" );
		Hyperlink sourceLink = new Hyperlink ( "GitHub" );
		sourceLink.getStyleClass().add( "aboutPaneURL" );

		String githubURL = "https://github.com/JoshuaD84/HypnosMusicPlayer";
		sourceLink.setTooltip( new Tooltip ( githubURL ) );
		
		sourceLink.setStyle( "-fx-text-fill: #0A95C8" );
		sourceLink.setOnAction( ( ActionEvent e ) -> {
			ui.openWebBrowser( githubURL );
		});

		sourceBox.getChildren().addAll( sourceLabel, sourceLink );
		
		HBox licenseBox = new HBox();
		licenseBox.setStyle( "-fx-background-color: transparent" );
		licenseBox.setAlignment( Pos.CENTER );
		Label licenseLabel = new Label ( "License:" );
		licenseLabel.setStyle( "-fx-text-fill: #020202" );
		licenseLabel.getStyleClass().add( "aboutPaneText" );
		Hyperlink licenseLink = new Hyperlink ( "GNU GPLv3" );
		licenseLink.setStyle( "-fx-text-fill: #0A95C8" );
		licenseLink.getStyleClass().add( "aboutPaneURL" );
		
		String gplurl = "https://www.gnu.org/licenses/gpl-3.0-standalone.html";
		licenseLink.setTooltip ( new Tooltip ( gplurl ) );
		licenseLink.setOnAction( ( ActionEvent e ) -> {
			ui.openWebBrowser( gplurl );
		});
		
		licenseBox.getChildren().addAll( licenseLabel, licenseLink );
		
		HBox bugBox = new HBox();
		bugBox.setStyle( "-fx-background-color: transparent" );
		bugBox.setAlignment( Pos.CENTER );
		Label bugLabel = new Label ( "Report a Bug:" );
		bugLabel.setStyle( "-fx-text-fill: #020202" );
		bugLabel.getStyleClass().add( "aboutPaneText" );
		
		Hyperlink bugGitLink = new Hyperlink ( "on GitHub" );
		bugGitLink.setStyle( "-fx-text-fill: #0A95C8" );
		bugGitLink.getStyleClass().add( "aboutPaneURL" );
		
		String bugGitHubURL = "https://github.com/JoshuaD84/HypnosMusicPlayer/issues";
		bugGitLink.setTooltip ( new Tooltip ( bugGitHubURL ) );
		bugGitLink.setOnAction( ( ActionEvent e ) -> {
			ui.openWebBrowser( bugGitHubURL );
		});
		
		bugBox.getChildren().addAll( bugLabel, bugGitLink );

		String updateURL = "http://hypnosplayer.org";
		updateLink = new Hyperlink ( "Update Available!" );
		updateLink.setStyle( "-fx-font-size: 20px; -fx-text-fill: #0A95C8" );
		updateLink.getStyleClass().add( "aboutPaneURL" );
		updateLink.setPadding( new Insets ( 15, 0, 0, 0 ) );
		updateLink.setVisible ( false );
		updateLink.setOnAction( e -> ui.openWebBrowser( updateURL ) );
		updateLink.setTooltip ( new Tooltip ( updateURL ) );
		updateLink.visibleProperty().bind( ui.updateAvailableProperty() );
		
		
		aboutPane.getChildren().addAll ( name, website, versionNumber, logo, 
			authorBox, sourceBox, licenseBox, bugBox, updateLink );
		
		return aboutTab;
	}
	
	private String hotkeyText = 
		"Main Window\n" +
		"    Back 5 seconds                           Numpad 1\n" +
		"    Stop                                     Numpad 2\n" +	
		"    Forward 5 seconds                        Numpad 3\n" +
		"    Previous                                 Numpad 4\n" +
		"    Play / Pause                             Numpad 5\n" +			
		"    Next                                     Numpad 6\n" +
		"    Volume Down                              Numpad 7\n" +
		"    Mute / Unmute                            Numpad 8\n" +
		"    Volume Up                                Numpad 9\n" +
		"    Toggle Shuffle Mode                      S\n" +	
		"    Toggle Repeat Mode                       R\n" +
		"    Show Lyrics on Current Track             Shift + L\n" +
		"    Toggle Library Collapsed                 Ctrl + L\n" +
		"    Toggle Artwork Collapsed                 Ctrl + ;\n" +
		"    Show Queue                               Ctrl + Q\n" +
		"    Show History                             Ctrl + H\n" +
		"    Show Config                              Ctrl + P\n" +
		"    Save Current List as Playlist            Ctrl + S\n" +	
		"    Open files                               Ctrl + O\n" +
		"    Export Current List as Playlist          Ctrl + E\n" +	
		"    Filter the Current List                  F\n" +
		"    Filter the First Tab in Library          1 or Ctrl + 1\n" +
		"    Filter the Second Tab in Library         2 or Ctrl + 2\n" +
		"    Filter the Third Tab in Library          3 or Ctrl + 3\n" +	
		"    Filter the Fourth Tab in Library         4 or Ctrl + 4\n" +	
		"    Filter whichever table has focus         Ctrl + F\n" +							
		"\n" +
		"Playlists, Albums, Tracks\n" +
		"    Queue Item                               Q\n" + 	 					
		"    play next (front of queue)               Shift + Q\n" + 				
		"    Tag Editor / Rename Playlist             F2\n" +						
		"    Track List / Info                        F3\n" +						
		"    File System Browser                      F4\n" +						
		"    Play Selected Item(s)                    Enter\n" +					
		"    Append Item to current list              Ctrl + Enter\n" +				
		"    Prepend to current List                  Shift + Enter\n" +				     			
		"\n" +	
		"Playlist Library\n" +
		"    Delete selected Playlists                Del\n" +						
		"\n" +
		"Any Track\n" +
		"    Show Lyrics                              L\n" +
		"    Select the track's album in library      Ctrl + G\n" + 
		"\n" +
		"All Tables\n" +
		"    Deselect                                 Esc\n" +
		"\n" +	
		"Filter Text Boxes\n" +
		"    Clear Filter                             Esc\n" +
		"\n" +	
		"Current List\n" +
		"    Delete selected Tracks                   Del\n" +
		"    Crop to selected Tracks                  Shift + Del\n" +				
		"\n" +	
		"Tag Window\n" +
		"    Save and Previous                        Ctrl + Right\n" +
		"    Save and Next                            Ctrl + Left\n" +				
		"\n" +
		"Popup Windows\n" +
		"    Close Window                             Esc";
}
