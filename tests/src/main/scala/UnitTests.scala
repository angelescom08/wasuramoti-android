package karuta.hpnpwd.wasuramoti.tests

import junit.framework.Assert._
import _root_.android.test.AndroidTestCase

class UnitTests extends AndroidTestCase {
  def testPackageIsCorrect {
    assertEquals("karuta.hpnpwd.wasuramoti", getContext.getPackageName)
  }
}