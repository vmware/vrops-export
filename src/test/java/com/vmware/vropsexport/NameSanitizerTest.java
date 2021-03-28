package com.vmware.vropsexport;

import org.junit.Assert;
import org.junit.Test;

public class NameSanitizerTest {
  @Test
  public void testReplacingNameSanitizer() {
    final NameSanitizer ns = new ReplacingNameSanitizer("$|^%!?", 'p');
    Assert.assertEquals(
        "peter piper picked a peck of pickled peppers",
        ns.transform("$eter |iper ^icked a %eck of !ickled !e??ers"));
    Assert.assertEquals(
        "peter piper picked a peck of pickled peppers",
        ns.transform("peter piper picked a peck of pickled peppers"));
    Assert.assertTrue( // When no replacements are needed, the same object should be returned.
        "peter piper picked a peck of pickled peppers"
            == ns.transform("peter piper picked a peck of pickled peppers"));
  }
}
