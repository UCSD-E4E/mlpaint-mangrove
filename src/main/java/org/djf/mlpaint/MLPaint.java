package org.djf.mlpaint;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.djf.util.SwingApp;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;


/** Magic Label Paint ~ ML Paint
 * 
 */
public class MLPaint extends SwingApp {
	
	/** current image */
	private BufferedImage image;
	
	/** layers:  filename & image itself.   First one is the color image. */
	private LinkedHashMap<Path,BufferedImage> layers;

	private BufferedImage masterImage;
	
	
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
		file.add(newMenuItem("Label positive", this::label));
		file.add(newMenuItem("Label negative", this::label));
		file.add(newMenuItem("Delete labeled area", this::label));
		
		JMenuBar rr = new JMenuBar();
		rr.add(file);
		rr.add(label);
		rr.add(view);
		return rr;
	}
	
	protected void openImage(String command, ActionEvent ev) throws IOException {
		JFileChooser jfc = new JFileChooser();
		jfc.setDialogTitle("Open images...");
		jfc.setCurrentDirectory(directory.toFile());
		jfc.setMultiSelectionEnabled(true);
		int rr = jfc.showOpenDialog(this);
		if (rr != JFileChooser.APPROVE_OPTION) {
			return;
		}
		directory = jfc.getCurrentDirectory().toPath();
		//MAYDO: remember it for future runs of the program
		
		File[] files = jfc.getSelectedFiles();

		LinkedHashMap<Path, BufferedImage> layers = Maps.newLinkedHashMap();
		long t = System.currentTimeMillis();
		for (File file: files) {
			BufferedImage img = ImageIO.read(file);
			t = reportTime(t, "loaded %s", file);
			layers.put(file.toPath(), img);
			if (file.toString().contains("_RGB")) {
				this.masterImage = img;
			}
		}
		this.layers = layers;
	}

	protected void saveLabels(String command, ActionEvent ev) {
	}
	
	protected void exit(String command, ActionEvent ev) {
		//TODO: if latest changes not saved
		// JOptionDialog "Do you want to save your labels first?
		// save or just quit
	}
	
	protected void label(String command, ActionEvent ev) {
	}
	
	protected void open(String command, ActionEvent ev) {
	}
	
	protected void open(String command, ActionEvent ev) {
	}
	
	protected void open(String command, ActionEvent ev) {
	}
	
	protected void open(ActionEvent ev) {
	}
	
	public static void main(String[] args) {
		//MAYDO: process startup arguments
		SwingUtilities.invokeLater(() -> new MLPaint());
	}
}
