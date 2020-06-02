package org.djf.mlpaint;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.djf.util.SwingApp;


/** Magic Label Paint ~ ML Paint
 * 
 */
public class MLPaint extends SwingApp {
	
	/** current directory */
	Path directory = Paths.get(".");
	
	
	public MLPaint() {
		super();
		setTitle("ML Paint, version 2020.06.02b");// update version number periodically
		setJMenuBar(makeMenus());
		setContentPane(makeContentPane());
		setBounds(40,40,1000,800);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
	}

	private JPanel makeContentPane() {
		JPanel rr = new JPanel(new BorderLayout());
		//
		return rr;
	}

	private JMenuBar makeMenus() {
		JMenu file = new JMenu("File");
		file.add(newMenuItem("Open image...", this::openImage));
		file.add(newMenuItem("Save labels...", this::saveLabels));
		file.add(newMenuItem("Exit", this::exit));
		
		
		JMenu view = new JMenu("View");
		
		JMenu label = new JMenu("Label");
		
		
		JMenuBar rr = new JMenuBar();
		rr.add(file);
		rr.add(label);
		rr.add(view);
		return rr;
	}
	
	protected void openImage(ActionEvent ev) throws IOException {
		JFileChooser jfc = new JFileChooser();
		jfc.setDialogTitle("Open image...");
		jfc.setCurrentDirectory(directory.toFile());
		int rr = jfc.showOpenDialog(this);
		if (rr != JFileChooser.APPROVE_OPTION) {
			return;
		}
		directory = jfc.getCurrentDirectory().toPath();
		//MAYDO: remember it for future runs
		File f = jfc.getSelectedFile();
		BufferedImage img = null;
		img = ImageIO.read(new File("strawberry.jpg"));
	}

	protected void saveLabels(ActionEvent ev) {
	}
	
	protected void exit(ActionEvent ev) {
		//TODO: if latest changes not saved
		// JOptionDialog "Do you want to save your labels first?
		// save or just quit
	}
	
	protected void open(ActionEvent ev) {
	}
	
	protected void open(ActionEvent ev) {
	}
	
	protected void open(ActionEvent ev) {
	}
	
	protected void open(ActionEvent ev) {
	}
	
	protected void open(ActionEvent ev) {
	}
	
	public static void main(String[] args) {
		//MAYDO: process startup arguments
		SwingUtilities.invokeLater(() -> new MLPaint());
	}
}
