package org.djf.util;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

import javax.swing.*;

import com.google.common.collect.Lists;


/** Swing base class utilities, with status bar */
public class SwingApp extends JFrame {

	/** An ActionListener that allows throwing an Exception --> statusRed(msg); */
	public interface ActionListener2  {
		void actionPerformed(String command, ActionEvent ev) throws Exception;
	}


	
	
	/** shared status bar */
	public static JLabel status = new JLabel();


	/** current directory */
	public static Path directory = Paths.get(".");


	public static ArrayList<Runnable> refreshCallbacks = Lists.newArrayList();

	/** run all the refresh callbacks */
	public void refresh() {
		refreshCallbacks.forEach(cb -> cb.run());
		repaint();
	}



	/** set the status bar with a printf message in normal black ink, and echo to output */
	public static void status(String printf, Object... args) {
		String msg = args.length == 0 ? printf : String.format(printf, args);
		status.setText(msg);
		status.setForeground(Color.BLACK);
		System.out.println(msg);
	}

	/** set the status bar & make it red */
	public static void statusRed(String printf, Object... args) {
		status(printf, args);
		status.setForeground(Color.RED);
	}
	
	public static long reportTime(long previous, String printf, Object... args) {
		long now = System.currentTimeMillis();
		String msg2 = String.format(printf, args);
		String msg1 = String.format("%,d ms   %s", now - previous, msg2);
		status(msg1);
		return now;
	}
	

	
	/** Run later in thread, reporting any exceptions via statusRed() & printStackTrace().
	 * Returns a CompletableFuture that includes timing information.
	 */
	public static void runForeground(Callable<?> runInGuiThread) {
		SwingUtilities.invokeLater(() -> {
	        try {
	            runInGuiThread.call();
	        } catch (Throwable ex) {
	            ex.printStackTrace();
	            statusRed(ex.getMessage());
			}
		});
	}

	public static void runForeground(Runnable runInGuiThread) {
		runForeground(() -> {
			runInGuiThread.run();
			return null;
		});
	}

	/** Run in background thread (not Swing thread), reporting any exceptions via statusRed() & printStackTrace().
	 * Returns a CompletableFuture that includes timing information.
	 * Also enqueues in U.bgTasks, should you want to U.bgWait() for all to finish, without keeping your own pointer.
	 */
	public static <T> CompletableFuture<T> runBackground(Callable<T> runInBackgroundThread) {
		CompletableFuture<T> future = new CompletableFuture<T>();
		CompletableFuture.runAsync(() -> {
	        try {
	            T result = runInBackgroundThread.call();
				future.complete(result);
	        } catch (Throwable ex) {
	        	ex.printStackTrace();
	        	statusRed(ex.getMessage());
	            future.completeExceptionally(ex);
			}
		});
		return future;
	}


	
	/** new named menu, containing all the menu items, dropping any nulls */
	public static JMenu newMenu(String name, JMenuItem... items) {
		JMenu rr = new JMenu(name);
		for (JMenuItem item: items) {
			if (item != null) {
				rr.add(item);
			} else {
				rr.add(new JSeparator());
			}
		}
		return rr;
	}

	/** new menu item for named command */
	public static JMenuItem newMenuItem(String command, ActionListener2 action) {
		return new JMenuItem(newAction(command, action));
	}

	
	/** new named menu item, with the provided action function
	 * If command string ends with "|alt D" establish keyboard shortcut.
	 * <pre>
	 *     <modifiers>* (<typedID> | <pressedReleasedID>)
	 *     modifiers := shift | control | ctrl | meta | alt | altGraph
	 *     typedID := typed <typedKey>
	 *     typedKey := string of length 1 giving Unicode character.
	 *     pressedReleasedID := (pressed | released) key
	 *     key := KeyEvent key code name, i.e. the name following "VK_". 
	 */
	public static AbstractAction newAction(String command, ActionListener2 action) {
		String name = Utils.before(command, "|");
		String shortcut = Utils.after(command, "|");
		AbstractAction rr = new AbstractAction(name) {
            public void actionPerformed(ActionEvent ev) {
                if (action==null) return;
    			try {
    				action.actionPerformed(command, ev);
    			} catch (Exception ex) {
    				statusRed(ex.getMessage());
    				ex.printStackTrace();
    			}
            }
        };
        if (!shortcut.isEmpty()) {
        	rr.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(shortcut));
		}
		return rr;
	}

	
	
	
	public static void restoreDirectory(Class<?> clazz) {
		Preferences prefs = Preferences.userNodeForPackage(clazz);
		Path path = Paths.get(prefs.get("directory", ""));
		if (Files.isDirectory(path)) {// only set if directory currently exists (otherwise fails silently)
			directory = path;// start in current directory
		}
	}
	
	/** store the current directory for next time we run the program */
	public static void storeDirectory(Class<?> clazz) {
		Preferences prefs = Preferences.userNodeForPackage(clazz);
        prefs.put("directory", directory.toFile().getAbsolutePath());
	}

}
