package karuta.hpnpwd.wasuramoti.uitest;

import java.util.Map;
import java.util.HashMap;

import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiObjectNotFoundException;
import com.android.uiautomator.core.UiScrollable;
import com.android.uiautomator.core.UiSelector;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

public class LaunchSettings extends UiAutomatorTestCase {
  private Map<String,String> parseButtonBottomText(String str){
    Map<String,String> map = new HashMap<String,String>();
    for(String s:str.split(";")){
      String [] ss = s.split("=");
      map.put(ss[0],ss[1]);
    }
    return map;
  }
  public void testReadButton() throws UiObjectNotFoundException {
    UiObject readButton = new UiObject(new UiSelector().description("ReadButton"));
    UiObject readButtonBottom = new UiObject(new UiSelector().description("ReadButtonBottom"));
    Map<String,String> info = parseButtonBottomText(readButtonBottom.getText());
    for(int i = 300; i < 350; i += 10){
      System.out.println(Integer.toString(i));
      readButton.click();
      try{
        Thread.sleep(i+Integer.parseInt(info.get("len")));
      }catch(InterruptedException e){
        Thread.currentThread().interrupt();
      }
      readButton.click();
    }
  }
}
