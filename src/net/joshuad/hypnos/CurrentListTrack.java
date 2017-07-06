package net.joshuad.hypnos;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.util.ArrayList;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

	public class CurrentListTrack extends Track {
		
		private static final long serialVersionUID = 1L;
		
		transient boolean isCurrentTrack = false; //TODO: Make this private
		
		transient ArrayList <Integer> queueIndex = new ArrayList <Integer> ();
		
		private transient StringProperty display = new SimpleStringProperty ( "" );
		
		public CurrentListTrack ( Path source ) throws IOException {
			super ( source );
			updateDisplayString();
		}
	
		public CurrentListTrack ( Track source ) throws IOException {
			super ( source.getPath() );
			updateDisplayString();
		}
		
		
		public void setIsCurrentTrack ( boolean isCurrentTrack ) {
			this.isCurrentTrack = isCurrentTrack;
			updateDisplayString();
		}
		
		public boolean getIsCurrentTrack ( ) {
			return isCurrentTrack;
		}
		
		public void clearQueueIndex () {
			if ( queueIndex == null ) queueIndex = new ArrayList <Integer> ();//TODO: Do this better. 
			queueIndex.clear();
			updateDisplayString();
		}

		public void addQueueIndex ( Integer index ) {
			if ( queueIndex == null ) queueIndex = new ArrayList <Integer> ();//TODO: Do this better. 
			queueIndex.add ( index );
			updateDisplayString();
		}
		
		public void updateDisplayString() {
			//TODO: Do this better. 
			if ( display == null ) display = new SimpleStringProperty ( "" );
			if ( queueIndex == null ) queueIndex = new ArrayList <Integer> ();//TODO: Do this better. 
			
			if ( isCurrentTrack ) {
				if ( queueIndex.size() == 1 && queueIndex.get( 0 ) == 1  ) {
					display.set( "🔁" );
				} else if ( queueIndex.size() > 1 && queueIndex.get( 0 ) == 1  ) {
					display.set( "🔁+" );
				} else if ( queueIndex.size() > 0 ) {
					display.set( "▶+" );
				} else {
					display.set( "▶" );
				}

			} else if ( queueIndex.size() == 1 ) {
				display.set( queueIndex.get( 0 ).toString() );
			} else if ( queueIndex.size() >= 1 ) {
				display.set( queueIndex.get( 0 ).toString() + "+" );
			} else {
				display.set( "" );
			}
		}
		
		public void setDisplay( String newDisplay ) {
			display.set( newDisplay );
		}
		
		public String getDisplay () {
			return display.get();
		}
		
		public StringProperty displayProperty () {
			return display;
		}
		
		private void readObject ( ObjectInputStream in ) throws IOException, ClassNotFoundException {
			in.defaultReadObject();
			display = new SimpleStringProperty ( "" );
			updateDisplayString();
		}
	}
