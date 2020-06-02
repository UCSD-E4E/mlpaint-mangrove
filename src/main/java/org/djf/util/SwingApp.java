package org.djf.util;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;

import com.google.common.collect.Lists;


/** Swing base class utilities, with status bar */
public class SwingApp extends JFrame {
	
	
	public interface ActionListener2 extends ActionListener {
		@Override
		default void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
			
		}
	}
	

	/** status bar */
	protected JLabel status = new JLabel();
	
	/** set the status bar with a printf message, and echo to output */
	public void status(String printf, Object... args) {
		String msg = args.length == 0 ? printf : String.format(printf, args);
		status.setText(msg);
		status.setForeground(Color.BLACK);
		System.out.println(msg);
	}

	/** set the status bar with a printf message, and echo to output */
	public void statusRed() {
		status.setForeground(Color.RED);
	}
	

	
	protected ArrayList<Runnable> refreshCallbacks = Lists.newArrayList();

	/** run all the refresh callbacks */
	public void refresh() {
		refreshCallbacks.forEach(cb -> cb.run());
	}

	protected JMenuItem newMenuItem(String name, ActionListener fn) {
		JMenuItem rr = new JMenuItem(name);
		rr.addActionListener(fn);
		return rr;
	}

}
