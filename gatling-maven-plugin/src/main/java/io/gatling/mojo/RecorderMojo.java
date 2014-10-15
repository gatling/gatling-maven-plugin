/**
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.mojo;

import java.nio.file.Paths;

import javax.swing.*;

import io.gatling.recorder.config.RecorderPropertiesBuilder;
import io.gatling.recorder.controller.RecorderController;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import scala.Option;

/**
 * Mojo to run Gatling Recorder.
 */
@Mojo(name = "recorder", defaultPhase = LifecyclePhase.INTEGRATION_TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class RecorderMojo extends AbstractMojo {

	/**
	 * Local port used by Gatling Proxy for HTTP.
	 */
	@Parameter(property = "gatling.recorder.localPort", alias = "lp")
	private Integer localPort;

	/**
	 * Outgoing proxy host.
	 */
	@Parameter(property = "gatling.recorder.proxyHost", alias = "ph")
	private String proxyHost;

	/**
	 * Outgoing proxy port for HTTP.
	 */
	@Parameter(property = "gatling.recorder.proxyPort", alias = "pp")
	private Integer proxyPort;

	/**
	 * Outgoing proxy port for HTTPS.
	 */
	@Parameter(property = "gatling.recorder.proxySslPort", alias = "pps")
	private Integer proxySSLPort;

	/**
	 * Uses <folderName> as the folder where generated simulations will be stored.
	 */
	@Parameter(property = "gatling.recorder.outputFolder", alias = "of", defaultValue = "${basedir}/src/test/scala")
	private String outputFolder;

	/**
	 * Uses <folderName> as the folder where request bodies are stored.
	 */
	@Parameter(property = "gatling.recorder.requestBodiesFolder", alias = "rbf", defaultValue = "${basedir}/src/test/resources/request-bodies")
	private String requestBodiesFolder;

	/**
	 * The name of the generated class.
	 */
	@Parameter(property = "gatling.recorder.className", alias = "cn")
	private String className;

	/**
	 * The package of the generated class.
	 */
	@Parameter(property = "gatling.recorder.package", alias = "pkg", defaultValue = "${project.groupId}")
	private String packageName;

	/**
	 * The encoding used in the recorder.
	 */
	@Parameter(property = "gatling.recorder.encoding", alias = "enc")
	private String encoding;

	/**
	 * The value of the "Follow Redirects" option.
	 */
	@Parameter(property = "gatling.recorder.followRedirect", alias = "fr")
	private Boolean followRedirect;

	/**
	 * The config file to load recorder settings from.
	 */
	@Parameter(property = "gatling.recorder.recorderConfigFile", defaultValue = "${basedir}/src/test/resources/recorder.conf")
	private String configFile;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		RecorderPropertiesBuilder props = new RecorderPropertiesBuilder();
		if (localPort != null) props.localPort(localPort);
		if (proxyHost != null) props.proxyHost(proxyHost);
		if (proxyPort != null) props.proxyPort(proxyPort);
		if (proxySSLPort != null) props.proxyPort(proxySSLPort);
		props.simulationOutputFolder(outputFolder);
		props.requestBodiesFolder(requestBodiesFolder);
		if (className != null) props.simulationClassName(className);
		if (packageName != null) props.simulationPackage(packageName);
		if (encoding != null) props.encoding(encoding);
		if (followRedirect != null) props.followRedirect(followRedirect);
		RecorderController.apply(props.build(), Option.apply(Paths.get(configFile)));
		while (JFrame.getFrames().length > 0) {
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException ex) {
				return;
			}
		}
	}
}
