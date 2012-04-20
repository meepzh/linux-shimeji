package com.group_finity.mascot.x11;

import java.awt.Rectangle;
import jnacontrib.x11.api.X.Display;
import jnacontrib.x11.api.X.X11Exception;
import jnacontrib.x11.api.X.Window;
import com.group_finity.mascot.environment.Area;
import com.group_finity.mascot.environment.WindowContainer;
import com.group_finity.mascot.environment.Environment;
import com.sun.jna.platform.unix.X11;
import java.io.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;
import java.util.Random;

class X11Environment extends Environment {
	
// The X display. See X.java 
	private Display display = new Display();

// Hashtable for storing the active windows
	public WindowContainer IE = new WindowContainer();

// Randomly chosen window for jump action targeting
	public Area activeIE = new Area();

// Current screen, never changes after initial assignment
// Environment.java and ComplexArea.java handle detection 
// and dual monitor behavior
	public static final Area workArea = new Area();

	private boolean checkTitles = true;
	private boolean cleanUp, newRandom = false;

// Counter variable used for stuff we want less 
// frequently than each tick. Initialize at 400 to
// force an early activeIE selection.
	private int q = 400;
	private int z = 0;
	private Number markedForDeletion;

// Variables for configuration options
	private int xoffset,yoffset,wmod,hmod = 0;
	private ArrayList<String> titles = new ArrayList<String>();


// Random number generator for choosing a window for jump actions
	private Random RNG = new Random();

// Storage for Window ID's, only used for comparison when removing 
// user-terminated windows
	private ArrayList<Number> curActiveWin = new ArrayList<Number>();

		
// init() - set work area and read configuration files	
	X11Environment() {
		workArea.set(getWorkAreaRect());
		try {
			FileInputStream fstream = new FileInputStream("window.conf");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			int z = 0;
			while ((strLine = br.readLine()) != null) {
				z++;
				switch (z) {
					case 1: break;
					case 2: this.xoffset = Integer.parseInt(strLine.trim()); break;
					case 3: this.yoffset = Integer.parseInt(strLine.trim()); break;
					case 4: this.wmod = Integer.parseInt(strLine.trim()); break;
					case 5: this.hmod = Integer.parseInt(strLine.trim()); break;
					default : break;
				}
			}
			br.close();
			in.close();
			fstream.close();
		} catch (Exception e) {}
		try {
			FileInputStream fstream = new FileInputStream("titles.conf");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null) {
				titles.add(strLine.trim());
			}
			br.close();
			in.close();
			fstream.close();
		} catch (Exception e) {}
		if (titles.size() == 0) checkTitles = false;
	}
	

// tick() - executed every x milliseconds, defined in Manager.java
	@Override
	public void tick() {
		super.tick();
		update();
	// New jump action target window every 500 ticks
		if (q == 500) {
			getRandomIE();
			q = 0;
		}
		q++;
	// Perform cleanup of user-terminated windows every 10th tick
		if (q%10==0) cleanUp = true;
	}

// update() - window handling, executed each tick
	private void update() {
		Window[] allWindows = null;
		Window ie = null;
		int x,y,w,h,id;
		Rectangle r = new Rectangle();
		Area a = new Area();
		if (cleanUp) curActiveWin = new ArrayList<Number>();
		if (display == null) return;
		try {
		// Retrieve all windows from the X Display
			allWindows = display.getWindows();
			uguu:	
				for (int i=0;i<allWindows.length;i++) {
				// Break for-loop if the window title does not match config.
					if (checkTitles) {
						if (!isIE(allWindows[i].getTitle())) continue uguu;
					}
				// Get window attributes.
					id = allWindows[i].getID();
					w = allWindows[i].getGeometry().width + wmod;
					h = allWindows[i].getGeometry().height + hmod;
					x = allWindows[i].getBounds().x + xoffset;
					y = allWindows[i].getBounds().y + yoffset;
				// Check if the window already exists in our container.
					if (IE.containsKey(id)) {
						a = IE.get(id);
						r = a.toRectangle();
						Rectangle newRect = new Rectangle(x,y,w,h);
					// Check if the window has been moved. Break for-loop
					// if it has not.
						try {
							if (r.getLocation() == newRect.getLocation()) {
								if (cleanUp) curActiveWin.add(id);
								continue uguu;
							}
						} catch (Exception e) {}
					// Window moved, set new dimensions and update entry
						a.set(newRect);
						IE.put(id,a);
						if (cleanUp) curActiveWin.add(id);
						continue uguu;
					}
				// New window, add it to container
					r = new Rectangle(x,y,w,h);
					a = new Area();
					a.set(r);
					a.setVisible(true);
					IE.put(id,a);
					if (cleanUp) curActiveWin.add(id);
				}
		} catch (X11Exception e) {}
	// Remove user-terminated windows from the container
		if (cleanUp) {
			if (z==0) {
				Iterator<Number> keys = IE.keySet().iterator();
				while (keys.hasNext()) {
					Number i = keys.next();
					if (!curActiveWin.contains(i)) {
					// Move each mascot on the window by +1,+1 for ejection
						IE.get(i).ejectMascots();
					// Mark the window for deletion on next tick
						markedForDeletion = i;
					}
				}	
			}
			if (z==1 && markedForDeletion != null) {
				IE.remove(markedForDeletion);
				markedForDeletion = null;
			}
			z++;
			if (z==2) {
				cleanUp = false;
				z=0;
			}
		}
			

	}
	
	private boolean isIE(String titlebar) {
		for (int i=0;i<titles.size();i++) {
			if (titlebar.contains(titles.get(i))) return true;
		}
		return false;
	} 

	private Rectangle getWorkAreaRect() {
		Rectangle r1 = getScreen().toRectangle();
		return r1;
	}

// getRandomIE() - assign a new randomly selected activeIE
// for jump action targeting.
	private void getRandomIE() {
		if (IE.size() == 0) return;
		int max = RNG.nextInt(IE.size());
		Iterator<Number> iter = IE.keySet().iterator();
		int i = 0;
		while (i<max) {
			iter.next();
			i++;
		}
		activeIE = IE.get(iter.next());
	}

	@Override
	public Area getActiveIE() {
		return this.activeIE;
	}
	
	@Override
	public WindowContainer getIE() {
		return this.IE;
	}
	
	@Override
	public Area getWorkArea() {
		return this.workArea;
	}

}
	

