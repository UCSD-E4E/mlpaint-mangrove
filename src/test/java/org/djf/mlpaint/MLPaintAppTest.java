package org.djf.mlpaint;

import static org.junit.Assert.*;

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.io.IOException;

import static org.assertj.swing.finder.WindowFinder.findFrame;
import static org.assertj.swing.launcher.ApplicationLauncher.*;

import javax.swing.SwingUtilities;

import java.awt.event.*;

import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.KeyPressInfo;
import org.assertj.swing.core.MouseButton;
import org.assertj.swing.core.Robot;
import org.assertj.swing.driver.JFileChooserDriver;
import org.assertj.swing.edt.FailOnThreadViolationRepaintManager;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JFileChooserFinder;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JFileChooserFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.assertj.swing.security.NoExitSecurityManagerInstaller;
import org.djf.util.SwingApp;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;


public class MLPaintAppTest extends AssertJSwingJUnitTestCase {
	  private FrameFixture window;
	  private Robot robot;
	  
	  private static NoExitSecurityManagerInstaller noExitSecurityManagerInstaller;
	  
	  @BeforeClass
	    public static void setUpClass() {
	        FailOnThreadViolationRepaintManager.install();
	        noExitSecurityManagerInstaller = NoExitSecurityManagerInstaller.installNoExitSecurityManager();
	    }
	  
	  @Override
	  protected void onSetUp() {
		// set testing parameter to true (doesn't automatically show image selector)
	    SwingApp frame = GuiActionRunner.execute(() -> new MLPaintApp(true));
	    // IMPORTANT: note the call to 'robot()'
	    // we must use the Robot from AssertJSwingJUnitTestCase
	    robot = robot();
	    window = new FrameFixture(robot, frame);
	    window.show(); // shows the frame to test
 

	  }
	  
	    @Override
	    protected void onTearDown() {
	        window.cleanUp();
	        // no test failure on exit
	        noExitSecurityManagerInstaller.uninstall();
	    }

	    @Test
	    public void checkLaunch() {
	    	window.requireVisible();
	    	window.requireEnabled();
	    	// size default, but can change
//	    	Dimension actualSize = new Dimension(1250, 800);
//	    	window.requireSize(actualSize);
	    }
	    
	    @Test
	    public void checkImageCancel() {
	    	window.menuItemWithPath("File", "Open image, labels...").click();
		    JFileChooserFixture fileChooser = JFileChooserFinder.findFileChooser("opener").withTimeout(1000).using(robot);
		    fileChooser.requireVisible();
		    fileChooser.cancel();
	    }
	    
	    @Test
	    public void checkImageLoad() {
	    	// can this work with relative paths?
	    	String directory = "/Users/hanakim/mlpaint-mangrove/src/test/java/testFiles";
	    	String filePath = directory + "/4a.png";
		    File testFile = new File(filePath);
		    KeyPressInfo i = KeyPressInfo.keyCode(KeyEvent.VK_DOWN);
	    	
	    	window.menuItemWithPath("File", "Open image, labels...").click();
		    JFileChooserFixture fileChooser = JFileChooserFinder.findFileChooser("opener").withTimeout(1000).using(robot);
		    fileChooser.requireVisible();
		    fileChooser.selectFile(testFile.getAbsoluteFile());
		    fileChooser.approve();
		    fileChooser.requireVisible();
	    }
	    
	    
	    @Test
	    public void checkCloseAndSave() {
	    	String directory = "/Users/hanakim/mlpaint-mangrove/src/test/java/testFiles";
	    	String filePath = directory + "/4a.png";
		    File testFile = new File(filePath);
		    KeyPressInfo i = KeyPressInfo.keyCode(KeyEvent.VK_DOWN);
		    
	    	window.menuItemWithPath("File", "Open image, labels...").click();
		    JFileChooserFixture fileChooser = JFileChooserFinder.findFileChooser("opener").withTimeout(1000).using(robot);
		    fileChooser.selectFile(testFile.getAbsoluteFile());
		    fileChooser.approve();
		    window.menuItemWithPath("File", "Exit the Program After Saving the Labels").click();
	    }
	    


}
