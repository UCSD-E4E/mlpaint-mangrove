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

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import com.google.common.collect.Lists;


/** Swing base class utilities, with status bar */
public class SwingApp extends JFrame {

	/** An ActionListener that allows throwing an Exception --> statusRed(msg); */
	public interface ActionListener2  {
		void actionPerformed(String command, ActionEvent ev) throws Exception;
	}


	
	
	
	
	/** current directory */
	public static Path directory = Paths.get(".");


	/** status bar */
	protected static JLabel status = new JLabel();
	
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
	
	/** Run later in JavaFx thread, reporting any exceptions via statusRed() & printStackTrace().
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


	
	protected ArrayList<Runnable> refreshCallbacks = Lists.newArrayList();

	/** run all the refresh callbacks */
	public void refresh() {
		refreshCallbacks.forEach(cb -> cb.run());
	}

	/** new named menu, containing all the menu items, dropping any nulls */
	protected JMenu newMenu(String name, JMenuItem... items) {
		JMenu rr = new JMenu(name);
		for (JMenuItem item: items) {
			if (item != null) {
				rr.add(item);
			}
		}
		return rr;
	}

	/** new named menu item, with the provided action function
	 * MAYDO: if command string ends with "|alt D" establish keyboard shortcut. 
	 */
	protected JMenuItem newMenuItem(String command, ActionListener2 fn) {
		JMenuItem rr = new JMenuItem(command);
		rr.addActionListener(ev -> {
			try {
				fn.actionPerformed(command, ev);
			} catch (Exception ex) {
				statusRed(ex.getMessage());
				ex.printStackTrace();
			}
		});
		return rr;
	}

	
	public static long reportTime(long previous, String printf, Object... args) {
		long now = System.currentTimeMillis();
		String msg2 = String.format(printf, args);
		String msg1 = String.format("%,d ms for %s", now - previous, msg2);
		status(msg1);
		return now;
	}
	
	
	
	protected void restoreDirectory(Class<?> clazz) {
		Preferences prefs = Preferences.userNodeForPackage(clazz);
		Path path = Paths.get(prefs.get("directory", ""));
		if (Files.isDirectory(path)) {// only set if directory currently exists (otherwise fails silently)
			directory = path;// start in current directory
		}
	}
	
	/** store the current directory for next time we run the program */
	protected void storeDirectory(Class<?> clazz) {
		Preferences prefs = Preferences.userNodeForPackage(clazz);
        prefs.put("directory", directory.toFile().getAbsolutePath());
	}

}
