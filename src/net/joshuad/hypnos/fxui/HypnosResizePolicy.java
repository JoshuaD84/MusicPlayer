package net.joshuad.hypnos.fxui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableView;
import javafx.util.Callback;

/* Summary:
 * Min-width, Max-width, and not-resizable are respected absolutely. These take precedence over all other constraints
 * 
 * total width of columns tries to be the width of the table. i.e. columns fill the table, but not past 100%. 
 * 
 * Programmer can register "fixed width" columns so that they tend not to change size when the table is resized but the user can directly resize them
 * 
 * When the entire table's size is changed, the excess space is shared on a ratio basis between non-fixed-width, resizable columns
 * 
 * When the user manually resizes columns, use that new size as the pref width for the columns
 * 
 * If a single column is manually resized, the delta is shared on a ratio basis between non-fixed-width columns, but only columns the resized column's right side
 * 
 * If all of the columns are set to fixed-size or no-resize, extra space is given to / taken from all fixed width columns proportionally
 * 
 * If all of the columns are no-resize, then the columns will not fill the table's width exactly (except, of course, at one coincidental size). 
 *  
 * Finally, following the guidelines above, extra space is given to columns that aren't at their pref width and need that type of space 
 * (negative or positive) on a proportional basis first. 
 */


@SuppressWarnings({ "rawtypes" })
public class HypnosResizePolicy implements Callback <TableView.ResizeFeatures, Boolean> {
	
	Vector <TableColumn<?, ?>> fixedWidthColumns = new Vector <TableColumn<?, ?>> ();
	
	public void registerFixedWidthColumns ( TableColumn <?, ?> ... addMe ) {
		fixedWidthColumns.addAll ( Arrays.asList ( addMe ) );
	}
	
	public void unregisterFixedWidthColumn ( TableColumn <?, ?> column ) {
		fixedWidthColumns.remove ( column );
	}
	
	@Override
	public Boolean call ( TableView.ResizeFeatures feature ) {
			
		TableView table = feature.getTable();

		if ( table == null || table.getWidth() == 0 ) return false;
		
		TableColumn columnToResize = feature.getColumn();
		List <TableColumn> columns = table.getVisibleLeafColumns();
		
		//There seem to be two modes: Either the entire table is resized, or the user is resizing a single column
		//This boolean will soon tell us know which mode we're in. 
		boolean singleColumnResizeMode = false;
		
		double targetDelta = feature.getDelta();

		if ( columnToResize != null && columnToResize.isResizable() ) {
			//Now we know we're in the mode where a single column is being resized. 
			singleColumnResizeMode = true;
			
			//We want to grow that column by targetDelta, but making sure we stay within the bounds of min and max. 
			double targetWidth = columnToResize.getWidth() + feature.getDelta();
			if ( targetWidth >= columnToResize.getMaxWidth() ) targetWidth = columnToResize.getMaxWidth();
			else if ( targetWidth <= columnToResize.getMinWidth() ) targetWidth = columnToResize.getMinWidth();
			targetDelta = targetWidth - columnToResize.getWidth();
		}
		
		double spaceToDistribute = calculateSpaceAvailable ( table ) - targetDelta;
		
		if ( Math.abs ( spaceToDistribute ) < 1 ) return false;
		
		//First we try to distribute the space to columns that aren't at their pref width
		//but always obeying not-resizable, min-width, and max-width
		//The space is distributed on a ratio basis, so columns that want to be bigger grow faster, etc.
		
		List <TableColumn> columnsNotAtPref = new ArrayList<> ();

		for ( TableColumn column : columns ) {
			boolean resizeThisColumn = false;
			
			//Choose to resize columns that aren't at their pref width and need the type of space we have (negative or positive) to get there
			//We do this pref - width > 1 thing instead of pref > width because very small differences don't matter
			//but they cause a bug where the column widths jump around wildly. 
			if ( spaceToDistribute > 0 && column.getPrefWidth() - column.getWidth() > 1 ) resizeThisColumn = true;
			if ( spaceToDistribute < 0 && column.getWidth() - column.getPrefWidth() > 1 ) resizeThisColumn = true;

			//but never resize columns that aren't resizable, are the current manul resizing column
			//or are to the left of the current manual resize column
			if ( !column.isResizable() ) resizeThisColumn = false; 
			if ( singleColumnResizeMode && column == columnToResize ) resizeThisColumn = false; 
			if ( singleColumnResizeMode && columns.indexOf( column ) < columns.indexOf( columnToResize ) ) resizeThisColumn = false; 
			
			if ( resizeThisColumn ) {
				columnsNotAtPref.add( column );
			}
		}
					
		distributeSpaceRatioToPref ( columnsNotAtPref, spaceToDistribute );
		
		
		//See how much space we have left after distributing to satisfy preferences. 
		spaceToDistribute = calculateSpaceAvailable ( table ) - targetDelta;
		
		if ( Math.abs( spaceToDistribute ) >= 1 ) {
			
			//Now we distribute remaining space across the rest of the columns, excluding as follows:
			
			List <TableColumn> columnsToReceiveSpace = new ArrayList <> ();
			for ( TableColumn column : columns ) {
				boolean resizeThisColumn = true;
				
				//Never resize columns that aren't resizable
				if ( !column.isResizable() ) resizeThisColumn = false; 
				
				//Never make columns more narrow than their min width
				if ( spaceToDistribute < 0 && column.getWidth() <= column.getMinWidth() ) resizeThisColumn = false; 
				
				//Never make columns wider than their max width
				if ( spaceToDistribute > 0 && column.getWidth() >= column.getMaxWidth() ) resizeThisColumn = false; 
				
				//If the extra space is the result of an individual column being resized, don't include that column
				//when distributing the extra space
				if ( singleColumnResizeMode && column == columnToResize ) resizeThisColumn = false; 
				
				//Exclude fixed-width columns, for now. We may add them back in later if needed. 
				if ( fixedWidthColumns.contains( column ) ) resizeThisColumn = false;
				
				//Exclude columns to the left of the resized column in the case of a single column manual resize
				if ( singleColumnResizeMode && columns.indexOf( column ) < columns.indexOf( columnToResize ) ) resizeThisColumn = false; 
				
				if ( resizeThisColumn ) {
					columnsToReceiveSpace.add( column );
				}
			}
			
			if ( columnsToReceiveSpace.size() == 0 ) {
				if ( singleColumnResizeMode ) {
					//If there are no eligible columns and we're doing a manual resize, we can break our fixed-width exclusion
					// and distribute the space to only the first fixed-width column to the right, this time allowing fixedWidth columns to be changed. 
					
					for ( int k = columns.indexOf( columnToResize ) + 1 ; k < columns.size() ; k++ ) {
						TableColumn candidate = columns.get( k );
						boolean resizeThisColumn = true;
						
						//Never resize columns that aren't resizable
						if ( !candidate.isResizable() ) resizeThisColumn = false; 
						
						//Never make columns more narrow than their min width
						if ( spaceToDistribute < 0 && candidate.getWidth() <= candidate.getMinWidth() ) resizeThisColumn = false; 
						
						//Never make columns wider than their max width
						if ( spaceToDistribute > 0 && candidate.getWidth() >= candidate.getMaxWidth() ) resizeThisColumn = false; 
						
						if ( resizeThisColumn ) {
							columnsToReceiveSpace.add ( candidate );
							//We only want one column, so we break after we find one. 
							break;
						}
					}
				
				} else {
					//If we're in full table resize mode and all of the columns are set to fixed-size or no-resize, 
					//extra space is given to / taken from all fixed width columns proportionally
					for ( TableColumn column : columns ) {
						if ( fixedWidthColumns.contains( column ) && column.isResizable() ) {
							columnsToReceiveSpace.add( column );
						}
					}
				}
			}
			
			//Now we distribute the space amongst all eligible columns. It is still possible for there to be no eligible columns, in that case, nothing happens. 
			distributeSpaceRatio ( columnsToReceiveSpace, spaceToDistribute );
		}
		
		if ( singleColumnResizeMode ) { 
			//Now if the user is manually resizing one column, we adjust that column's width to include whatever space we made / destroyed
			//with the above operations. 
			//I found it is better to do this at the end when the actual space create/destroyed is known, rather than doing at the top and then
			//trying to get that much space from the other columns. Sometimes the other columns resist, and this creates a much smoother user experience. 
			setColumnWidth( columnToResize, columnToResize.getWidth() + calculateSpaceAvailable ( table ) );
			
			//If it's a manual resize, set pref-widths to these user specified widths. 
			//The user manually set them now, so they like this size. Try to respect them on next resize.
			for ( TableColumn column : columns ) {
				column.setPrefWidth( column.getWidth() );
			}
		} 

		return true;
	}
	
	private void distributeSpaceRatioToPref ( List<TableColumn> columns, double spaceToDistribute ) {
		
		double spaceWanted = 0;
		for ( TableColumn column : columns ) spaceWanted += column.getPrefWidth() - column.getWidth();
		
		if ( spaceWanted < spaceToDistribute ) {
			for ( TableColumn column : columns ) {			
				double targetWidth = column.getPrefWidth();
				if ( targetWidth >= column.getMaxWidth() ) targetWidth = column.getMaxWidth();
				else if ( targetWidth <= column.getMinWidth() ) targetWidth = column.getMinWidth();
				setColumnWidth( column, targetWidth );
			}
		} else {
			for ( TableColumn column : columns ) {			
				double targetWidth = column.getWidth() + spaceToDistribute * ( column.getPrefWidth() / spaceWanted );
				if ( targetWidth >= column.getMaxWidth() ) targetWidth = column.getMaxWidth();
				else if ( targetWidth <= column.getMinWidth() ) targetWidth = column.getMinWidth();
				setColumnWidth( column, targetWidth );
			}
		}	
	}
	
	private void distributeSpaceRatio ( List<TableColumn> columns, double space ) {
		
		double totalWidth = 0;
		for ( TableColumn column : columns ) totalWidth += column.getWidth();
		
		for ( TableColumn column : columns ) {
			double targetWidth = column.getWidth() + space * ( column.getWidth() / totalWidth );
			if ( targetWidth >= column.getMaxWidth() ) targetWidth = column.getMaxWidth();
			else if ( targetWidth <= column.getMinWidth() ) targetWidth = column.getMinWidth();
			setColumnWidth( column, targetWidth );
		}
	}
	
	private void setColumnWidth ( TableColumn column, double targetWidth ) {
		try {
			Method method = TableColumnBase.class.getDeclaredMethod( "doSetWidth", double.class );
			method.setAccessible( true );
			method.invoke( column, targetWidth );
		} catch ( NoSuchMethodException | InvocationTargetException | IllegalAccessException e ) {
			e.printStackTrace();
		}
	}

	private double calculateSpaceAvailable ( TableView table ) {
		List <TableColumn> columns = table.getVisibleLeafColumns();
		double spaceToDistribute = table.getWidth() - getScrollbarWidth ( table ) - 4; 
	
		//That -4 is annoying. I'm sure there's a way to actually get that value
		//See thread here: https://stackoverflow.com/questions/47852175/how-to-spread-the-total-width-amongst-all-columns-on-table-construction
		//Which is also implemented below in calculateSpaceAvailableNew
		//Unfortunately, that solution didn't work on manual resizes. 
		
		for ( TableColumn column : columns ) spaceToDistribute -= column.getWidth();
		return spaceToDistribute;
	}
	
	private double getScrollbarWidth ( TableView table ) {
		double scrollBarWidth = 0;
		Set <Node> nodes = table.lookupAll( ".scroll-bar" );
		for ( final Node node : nodes ) {
			if ( node instanceof ScrollBar ) {
				ScrollBar sb = (ScrollBar) node;
				if ( sb.getOrientation() == Orientation.VERTICAL ) {
					if ( sb.isVisible() ) {
						scrollBarWidth = sb.getWidth();
					}
				}
			}
		}
		
		return scrollBarWidth;
	}
	
	/*private double calculateSpaceAvailableNew ( TableView table ) {
		Region region = null;
		Set<Node> nodes = table.lookupAll(".clipped-container");
		for (Node node : nodes) {
		    if (node instanceof Region) {
		        region = (Region) node;
		    }
		}
		
		return region.getWidth();
	}*/
}
