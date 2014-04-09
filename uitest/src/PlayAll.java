package karuta.hpnpwd.wasuramoti.uitest;

import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiObjectNotFoundException;
import com.android.uiautomator.core.UiScrollable;
import com.android.uiautomator.core.UiSelector;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

public class PlayAll extends UiAutomatorTestCase {
  private Map<String,String> parseButtonBottomText(String str){
    Map<String,String> map = new HashMap<String,String>();
    for(String s:str.split(";")){
      String [] ss = s.split("=");
      map.put(ss[0],ss[1]);
    }
    return map;
  }
  public void testReadButton() throws UiObjectNotFoundException {
    try{
      UiObject readButton = new UiObject(new UiSelector().description("ReadButton"));
      UiObject mainDebugInfo = new UiObject(new UiSelector().description("MainDebugInfo"));
      getUiDevice().pressMenu();
      Thread.sleep(1000);
      UiObject shuffle = new UiObject(new UiSelector().text("シャッフル"));
      shuffle.waitForExists(2000);
      shuffle.clickAndWaitForNewWindow();
      UiObject ok = new UiObject(new UiSelector().text("OK"));
      ok.click();
      Thread.sleep(2000);
      Random generator = new Random();
      for(int i = 0; i < 100; i ++){
        Map<String,String> info = parseButtonBottomText(mainDebugInfo.getText());
        System.out.println(Integer.toString(i));
        readButton.click();
        int span = 2000 + generator.nextInt(20000);
        Thread.sleep(span+Integer.parseInt(info.get("len")));
          Thread.currentThread().interrupt();
      }
      UiObject ok2 = new UiObject(new UiSelector().text("OK"));
      ok2.waitForExists(2000);
      ok2.click();
      getUiDevice().pressHome();
    }catch(InterruptedException e){
   }
  }
}
