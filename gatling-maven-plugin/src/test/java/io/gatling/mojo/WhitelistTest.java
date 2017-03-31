package io.gatling.mojo;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


import java.util.List;


/**
 *
 */

@RunWith(JUnit4.class)
public class WhitelistTest {

  @Test
  public void testWithoutAnyIncludesOrExcludes() throws Exception {
    GatlingMojo gatlingMojo = new GatlingMojo();
    String[] classFileList = new String[]{"io.test.great.class"};

    List<String> result = gatlingMojo.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 1);
  }

  @Test
  public void testWithoutIncludesNotMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class"};
    String[] includesList = new String[] {"io.notcool.*"};
    GatlingMojo mojo = new GatlingMojo();

    mojo.setIncludes(includesList);

    List<String> result = mojo.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.isEmpty());
  }

  @Test
  public void testWithoutIncludesMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class"};
    String[] includesList = new String[] {"io.test.*"};
    GatlingMojo mojo = new GatlingMojo();

    mojo.setIncludes(includesList);

    List<String> result = mojo.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 1);
  }

  @Test
  public void testWithoutExcludesMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class"};
    String[] excludesList = new String[] {"io.test.*"};
    GatlingMojo mojo = new GatlingMojo();

    mojo.setExcludes(excludesList);

    List<String> result = mojo.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.isEmpty());
  }

  @Test
  public void testWithoutExcludesAndIncludesMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class","io.anothertest.awesome.class","io.onemoretest.cool.class"};
    String[] excludesList = new String[] {"io.test.*"};
    String[] includesList = new String[] {"io.onemoretest.*"};
    GatlingMojo mojo = new GatlingMojo();

    mojo.setExcludes(excludesList);
    mojo.setIncludes(includesList);

    List<String> result = mojo.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 1);
  }

  @Test
  public void testWithoutExcludesExactMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class","io.anothertest.awesome.class","io.onemoretest.cool.class"};
    String[] excludesList = new String[] {"io.test.great"};
    GatlingMojo mojo = new GatlingMojo();

    mojo.setExcludes(excludesList);

    List<String> result = mojo.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 2);
  }

  @Test
  public void testWithoutIncludesExactMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class","io.test.great.work.class", "io.anothertest.awesome.class","io.onemoretest.cool.class"};
    String[] includesList = new String[] {"io.test.great"};
    GatlingMojo mojo = new GatlingMojo();

    mojo.setIncludes(includesList);

    List<String> result = mojo.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 1);
  }
}
