package org.djf.mlpaint;

import org.djf.util.SwingApp;
import org.djf.util.Utils;
import javax.swing.*;
import java.awt.*;

/** A single object to keep track of action things, buttons, menuitems, etc.
 *
 * If command string ends with "|alt D" establish keyboard shortcut.
 * <pre>
 *     <modifiers>* (<typedID> | <pressedReleasedID>)
 *     modifiers := shift | control | ctrl | meta | alt | altGraph
 *     typedID := typed <typedKey>
 *     typedKey := string of length 1 giving Unicode character.
 *     pressedReleasedID := (pressed | released) key
 *     key := KeyEvent key code name, i.e. the name following "VK_".
 */
public class ActionTracker {
	//Name of button, item
	public final String name;
	public final String keyStroke;
	public final AbstractAction action;
	public final JMenuItem menuItem;

	public JCheckBox lbdelk;

	public ActionTracker(String command, SwingApp.ActionListener2 myAction) {
		this.name = Utils.before(command, "|");
		this.keyStroke = Utils.after(command, "|");
		this.action = SwingApp.newAction(command, myAction);
		this.menuItem = SwingApp.newMenuItem(this.action);
	}

	/** Add a JButton with key-trigger to a Box. */
	public void addAsButton(Box controls) {
		String text = this.name;
		String keyStroke = this.keyStroke;

		JButton button = new JButton( text );
		button.addActionListener( this.action);
		button.setFocusable(false);
		//button.setBorder( new LineBorder(Color.BLACK) );
		//button.setPreferredSize( new Dimension(250, 10) );
		controls.add( button );

		InputMap inputMap = controls.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		inputMap.put(KeyStroke.getKeyStroke(keyStroke), keyStroke);
		controls.getActionMap().put(keyStroke, this.action);
	}

	/** Add a JCheckBox with key-trigger to a Box.
	 * https://stackoverflow.com/a/33739732/13773745*/
	public void addAsCheckBox(Box controls) {
		String text = this.name;
		String keyStroke = this.keyStroke;

		JCheckBox checkBox = new JCheckBox( text );
		checkBox.addActionListener( this.action);
		//button.setBorder( new LineBorder(Color.BLACK) );
		//button.setPreferredSize( new Dimension(250, 10) );
		controls.add( checkBox );

		InputMap inputMap = controls.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		inputMap.put(KeyStroke.getKeyStroke(keyStroke), keyStroke);
		controls.getActionMap().put(keyStroke, this.action);
	}


	//FROM SwingApp.java:
//	/** new named menu item, with the provided action function
//	 * If command string ends with "|alt D" establish keyboard shortcut.
//	 * <pre>
//	 *     <modifiers>* (<typedID> | <pressedReleasedID>)
//	 *     modifiers := shift | control | ctrl | meta | alt | altGraph
//	 *     typedID := typed <typedKey>
//	 *     typedKey := string of length 1 giving Unicode character.
//	 *     pressedReleasedID := (pressed | released) key
//	 *     key := KeyEvent key code name, i.e. the name following "VK_".
//	 */
//	public static AbstractAction newAction(String command, SwingApp.ActionListener2 action) {
//		String name = Utils.before(command, "|");
//		String shortcut = Utils.after(command, "|");
//		AbstractAction rr = new AbstractAction(name) {
//			public void actionPerformed(ActionEvent ev) {
//				if (action==null) return;
//				try {
//					action.actionPerformed(command, ev);
//				} catch (Exception ex) {
//					statusRed(ex.getMessage());
//					ex.printStackTrace();
//				}
//			}
//		};
//		if (!shortcut.isEmpty()) {
//			rr.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(shortcut));
//		}
//		return rr;
//	}

}
