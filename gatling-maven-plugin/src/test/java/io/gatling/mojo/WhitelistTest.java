package io.gatling.mojo;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.powermock.reflect.internal.WhiteboxImpl;

import java.util.ArrayList;
import java.util.List;

import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.support.membermodification.MemberMatcher.method;

/**
 *
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest(GatlingMojo.class)
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
    GatlingMojo spy = PowerMockito.spy(new GatlingMojo());

    Whitebox.setInternalState(spy, "includes", includesList);


    List<String> result = spy.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 0);
  }

  @Test
  public void testWithoutIncludesMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class"};

    String[] includesList = new String[] {"io.test.*"};
    GatlingMojo spy = PowerMockito.spy(new GatlingMojo());

    Whitebox.setInternalState(spy, "includes", includesList);


    List<String> result = spy.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 1);
  }

  @Test
  public void testWithoutExcludesMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class"};

    String[] excludesList = new String[] {"io.test.*"};
    GatlingMojo spy = PowerMockito.spy(new GatlingMojo());

    Whitebox.setInternalState(spy, "excludes", excludesList);


    List<String> result = spy.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 0);
  }

  @Test
  public void testWithoutExcludesAndIncludesMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class","io.anothertest.awesome.class","io.onemoretest.cool.class"};

    String[] excludesList = new String[] {"io.test.*"};
    String[] includesList = new String[] {"io.onemoretest.*"};

    GatlingMojo spy = PowerMockito.spy(new GatlingMojo());

    Whitebox.setInternalState(spy, "excludes", excludesList);
    Whitebox.setInternalState(spy, "includes", includesList);


    List<String> result = spy.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 1);
  }

  @Test
  public void testWithoutExcludesExactMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class","io.anothertest.awesome.class","io.onemoretest.cool.class"};

    String[] excludesList = new String[] {"io.test.great"};

    GatlingMojo spy = PowerMockito.spy(new GatlingMojo());

    Whitebox.setInternalState(spy, "excludes", excludesList);


    List<String> result = spy.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 2);
  }

  @Test
  public void testWithoutIncludesExactMatchingClassNames() throws Exception {
    String[] classFileList = new String[]{"io.test.great.class","io.test.great.work.class", "io.anothertest.awesome.class","io.onemoretest.cool.class"};

    String[] includesList = new String[] {"io.test.great"};

    GatlingMojo spy = PowerMockito.spy(new GatlingMojo());

    Whitebox.setInternalState(spy, "includes", includesList);


    List<String> result = spy.resolveIncludesAndExcludes(classFileList);
    Assert.assertTrue(result.size() == 1);
  }

}
