package karuta.hpnpwd.wasuramoti.uitest;

import java.util.Map;
import java.util.HashMap;

import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiObjectNotFoundException;
import com.android.uiautomator.core.UiScrollable;
import com.android.uiautomator.core.UiSelector;
import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;
// To take screenshot, you should turn on "Use Host GPU" if you are using emulator.
public class TakeScreenshotAll extends UiAutomatorTestCase {
  private static final String OUTPUT_DIR = "/data/local/tmp/wasuramoti_screenshot/";
  public void testMain() throws Exception {
    new java.io.File(OUTPUT_DIR).mkdir();
    for(int i = 0; i < 101; i++){
      // we should use screencap instead of UiDevice.takeScreenshot() since latter is only supported at API >= 17
      Process process = Runtime.getRuntime().exec("screencap -p "+OUTPUT_DIR+"wsrmt_"+String.format("%03d",i+1)+".png");
      process.waitFor();
      new UiObject(new UiSelector().description("yomi_info")).swipeRight(10);
      Thread.sleep(500);
    }
  }
}
