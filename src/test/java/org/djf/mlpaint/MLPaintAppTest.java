package org.djf.mlpaint;

import static org.junit.Assert.*;

import javax.swing.SwingUtilities;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;

import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.djf.util.SwingApp;
import org.junit.Before;
import org.junit.Test;

public class MLPaintAppTest extends AssertJSwingJUnitTestCase {
	  private FrameFixture window;

	  @Override
	  protected void onSetUp() {
	    SwingApp frame = GuiActionRunner.execute(() -> new MLPaintApp(true));
	    // IMPORTANT: note the call to 'robot()'
	    // we must use the Robot from AssertJSwingJUnitTestCase
	    window = new FrameFixture(robot(), frame);
	    window.show(); // shows the frame to test
	  }
	  
	    @Override
	    protected void onTearDown() {
	        window.cleanUp();
	    }

	  @Test
	  public void checkTestLabel() {
	    window.label("test").requireEnabled();
	  }

}
