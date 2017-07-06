package net.joshuad.hypnos.fxui;

import java.awt.Desktop;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import net.joshuad.hypnos.Library;
import net.joshuad.hypnos.Playlist;
import net.joshuad.hypnos.Track;
import net.joshuad.hypnos.audio.AudioSystem;
import net.joshuad.hypnos.fxui.DraggedTrackContainer.DragSource;

public class HistoryWindow extends Stage {
	
	private FXUI ui;
	private AudioSystem player;
	private Library library;

	private TableView <Track> historyTable;
	
	public HistoryWindow ( FXUI ui, Library library, AudioSystem player ) {
		super();
		this.ui = ui;
		this.player = player;
		this.library = library;
		
		initModality( Modality.NONE );
		initOwner( ui.getMainStage() );
		setTitle( "History" );
		setWidth( 600 );
		setHeight ( 400 );
		Pane root = new Pane();
		Scene scene = new Scene( root );
		VBox primaryPane = new VBox();

		historyTable = new TableView<Track>();
		Label emptyLabel = new Label( "History is empty." );
		emptyLabel.setPadding( new Insets( 20, 10, 20, 10 ) );
		emptyLabel.setWrapText( true );
		emptyLabel.setTextAlignment( TextAlignment.CENTER );

		historyTable.setColumnResizePolicy( TableView.CONSTRAINED_RESIZE_POLICY );
		historyTable.setPlaceholder( emptyLabel );
		historyTable.setItems( player.getHistory().getItems() );
		historyTable.getSelectionModel().setSelectionMode( SelectionMode.MULTIPLE );

		historyTable.setOnKeyPressed( new EventHandler <KeyEvent>() {
			@Override
			public void handle ( final KeyEvent keyEvent ) {
				if ( keyEvent.getCode().equals( KeyCode.DELETE ) ) {
					ObservableList <Integer> indexes = historyTable.getSelectionModel().getSelectedIndices();
					for ( int index : indexes ) { //TODO: removeAll
						player.getQueue().remove( index );
					}
				}
			}
		});

		TableColumn numberColumn = new TableColumn( "#" );
		TableColumn artistColumn = new TableColumn( "Artist" );
		TableColumn albumColumn = new TableColumn( "Album" );
		TableColumn titleColumn = new TableColumn( "Title" );
		
		numberColumn.setMaxWidth( 10000 );
		artistColumn.setMaxWidth( 30000 );
		albumColumn.setMaxWidth ( 30000 );
		titleColumn.setMaxWidth ( 30000 );
		
		ContextMenu trackContextMenu = new ContextMenu();
		MenuItem playMenuItem = new MenuItem( "Play" );
		MenuItem apendMenuItem = new MenuItem( "Append" );
		MenuItem enqueueMenuItem = new MenuItem( "Enqueue" );
		MenuItem editTagMenuItem = new MenuItem( "Edit Tag(s)" );
		MenuItem browseMenuItem = new MenuItem( "Browse Folder" );
		Menu addToPlaylistMenuItem = new Menu( "Add to Playlist" );
		MenuItem removeMenuItem = new MenuItem( "Remove from History" );
		trackContextMenu.getItems().addAll( 
			playMenuItem, apendMenuItem, enqueueMenuItem,
			editTagMenuItem, browseMenuItem, addToPlaylistMenuItem, removeMenuItem 
		);
		
		MenuItem newPlaylistButton = new MenuItem( "<New>" );

		historyTable.setRowFactory( tv -> {
			TableRow <Track> row = new TableRow <>();
			row.setContextMenu( trackContextMenu );
			
			row.setOnMouseClicked( event -> {
				if ( event.getClickCount() == 2 && (!row.isEmpty()) ) {
					Track playMe = historyTable.getSelectionModel().getSelectedItem();
					player.setTrack ( playMe );
					player.play();
				}
			});
			
			row.setOnDragDetected( event -> {
				if ( !row.isEmpty() ) {
					ArrayList <Integer> indices = new ArrayList <Integer>( historyTable.getSelectionModel().getSelectedIndices() );
					ArrayList <Track> tracks = new ArrayList <Track>( historyTable.getSelectionModel().getSelectedItems() );
					DraggedTrackContainer dragObject = new DraggedTrackContainer( indices, tracks, null, null, DragSource.HISTORY );
					Dragboard db = row.startDragAndDrop( TransferMode.COPY );
					db.setDragView( row.snapshot( null, null ) );
					ClipboardContent cc = new ClipboardContent();
					cc.put( FXUI.DRAGGED_TRACKS, dragObject );
					db.setContent( cc );
					event.consume();

				}
			} );
			
			return row;
		});

		addToPlaylistMenuItem.getItems().add( newPlaylistButton );

		newPlaylistButton.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent e ) {
				ui.promptAndSavePlaylist ( historyTable.getSelectionModel().getSelectedItems(), false );
			}
		});

		EventHandler<ActionEvent> addToPlaylistHandler = new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				Playlist playlist = (Playlist) ((MenuItem) event.getSource()).getUserData();
				ui.addToPlaylist ( historyTable.getSelectionModel().getSelectedItems(), playlist );
			}
		};
		
		//TODO: I don't know if this is right; 
		library.getPlaylistSorted().addListener( ( ListChangeListener.Change <? extends Playlist> change ) -> {
			ui.updatePlaylistMenuItems( addToPlaylistMenuItem.getItems(), addToPlaylistHandler );
		});

		ui.updatePlaylistMenuItems( addToPlaylistMenuItem.getItems(), addToPlaylistHandler );
		
		playMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				player.setTracks( historyTable.getSelectionModel().getSelectedItems() );
				player.play();
			}
		});
		
		enqueueMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				player.getQueue().addAllTracks( historyTable.getSelectionModel().getSelectedItems() );
			}
		});

		apendMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				player.appendTracks ( historyTable.getSelectionModel().getSelectedItems() );
			}
		});
		
		editTagMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				List<Track> tracks = historyTable.getSelectionModel().getSelectedItems();
				
				ui.tagWindow.setTracks( tracks, null );
				ui.tagWindow.show();
			}
		});
		

		removeMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			@Override
			public void handle ( ActionEvent event ) {
				synchronized ( historyTable.getItems() ) {
					List<Integer> selectedIndices = historyTable.getSelectionModel().getSelectedIndices();
					
					ArrayList<Integer> removeMeIndices = new ArrayList<Integer> ( selectedIndices );
					
					for ( int k = removeMeIndices.size() - 1; k >= 0 ; k-- ) {
						//TODO: probably put the remove code somewhere else. 
						historyTable.getItems().remove( removeMeIndices.get( k ).intValue() );
					}
				}
			}
		});

		browseMenuItem.setOnAction( new EventHandler <ActionEvent>() {
			// TODO: This is the better way, once openjdk and openjfx supports
			// it: getHostServices().showDocument(file.toURI().toString());
			@Override
			public void handle ( ActionEvent event ) {
				SwingUtilities.invokeLater( new Runnable() {
					public void run () {
						try {
							Track selectedTrack = historyTable.getSelectionModel().getSelectedItem();
							if ( selectedTrack != null ) {
								Desktop.getDesktop().open( selectedTrack.getPath().getParent().toFile() );
							}
						} catch ( IOException e ) {
							e.printStackTrace();
						}
					}
				} );
			}
		} );
		
		
		numberColumn.setCellValueFactory( new Callback <CellDataFeatures <Track, Track>, ObservableValue <Track>>() {
			@Override
			public ObservableValue <Track> call ( CellDataFeatures <Track, Track> p ) {
				return new ReadOnlyObjectWrapper( p.getValue() );
			}
		});

		numberColumn.setCellFactory( new Callback <TableColumn <Track, Track>, TableCell <Track, Track>>() {
			@Override
			public TableCell <Track, Track> call ( TableColumn <Track, Track> param ) {
				return new TableCell <Track, Track>() {
					@Override
					protected void updateItem ( Track item, boolean empty ) {
						super.updateItem( item, empty );

						if ( this.getTableRow() != null && item != null ) {
							setText( this.getTableRow().getIndex() + 1 + "" );
						} else {
							setText( "" );
						}
					}
				};
			}
		});
		numberColumn.setSortable(false);
		artistColumn.setSortable(false);
		albumColumn.setSortable(false);
		titleColumn.setSortable(false);
		
		artistColumn.setCellValueFactory( new PropertyValueFactory <Track, String>( "Artist" ) );
		titleColumn.setCellValueFactory( new PropertyValueFactory <Track, String>( "Title" ) );
		albumColumn.setCellValueFactory( new PropertyValueFactory <Track, String>( "FullAlbumTitle" ) );
		
		historyTable.getColumns().addAll( numberColumn, artistColumn, albumColumn, titleColumn );

		historyTable.prefWidthProperty().bind( root.widthProperty() );
		historyTable.prefHeightProperty().bind( root.heightProperty() );
		
		primaryPane.getChildren().addAll( historyTable );
		root.getChildren().add( primaryPane );
		setScene( scene );
		
	}

}
