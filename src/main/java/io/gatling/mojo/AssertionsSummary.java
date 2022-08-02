
/*
 * Copyright 2011-2022 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.mojo;

import static java.lang.Integer.parseInt;

import java.io.File;
import java.io.FileInputStream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

final class AssertionsSummary {
  private final int errors;
  private final int failures;

  AssertionsSummary(int errors, int failures) {
    this.errors = errors;
    this.failures = failures;
  }

  static AssertionsSummary fromAssertionsFile(File assertionsFile) throws Exception {
    XPathFactory xpathFactory = XPathFactory.newInstance();
    XPath xpath = xpathFactory.newXPath();

    try (FileInputStream is = new FileInputStream(assertionsFile)) {
      Node testsuite =
          (Node) xpath.evaluate("/testsuite", new InputSource(is), XPathConstants.NODE);
      String errors = xpath.evaluate("@errors", testsuite);
      String failures = xpath.evaluate("@failures", testsuite);
      return new AssertionsSummary(parseInt(errors), parseInt(failures));
    }
  }

  int getErrors() {
    return errors;
  }

  int getFailures() {
    return failures;
  }

  boolean hasFailures() {
    return errors + failures > 0;
  }
}
