package tami.pen.wasuramoti.tests

import junit.framework.Assert._
import _root_.android.test.AndroidTestCase

class UnitTests extends AndroidTestCase {
  def testPackageIsCorrect {
    assertEquals("tami.pen.wasuramoti", getContext.getPackageName)
  }
}