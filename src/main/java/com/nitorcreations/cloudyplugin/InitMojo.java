package com.nitorcreations.cloudyplugin;

import static com.google.common.collect.Iterables.getOnlyElement;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.ssh.jsch.config.JschSshClientModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;


/**
 *
 */
@Mojo( name = "init",  aggregator = true )
public class InitMojo extends AbstractCloudyMojo
{
	@Parameter( defaultValue = "default", property = "instanceTag", required = true )
	private String instanceTag;

	private final CustomizerResolver resolver;
	
	public InitMojo() {
		this.resolver = new GroovyCustomizerResolver();
	}
	@Inject
	public InitMojo(CustomizerResolver resolver) {
		super();
		this.resolver = resolver;
	}

	public void execute() throws MojoExecutionException, MojoFailureException	{
		super.execute();
		String groupName = project.getGroupId().replaceAll("[^a-zA-Z\\-]", "-") + "-" + project.getArtifactId().replaceAll("[^a-zA-Z\\-]", "-");
		ComputeService compute = initComputeService(provider, identity, credential);
		TemplateBuilder templateBuilder = compute.templateBuilder();
		File pom = project.getFile();
		File developerNodeFile = new File(pom.getParentFile(), "." + currentDeveloper.getId() + "-nodes");
		Properties developerNodes = new Properties();
		if (developerNodeFile.exists()) {
			try (InputStream in = new FileInputStream(developerNodeFile)){
				developerNodes.load(in);
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to read developer node data", e);
			}
		}
		
		if (developerNodes.getProperty(instanceTag) != null) {
			String instanceId = developerNodes.getProperty(instanceTag);
			NodeMetadata existingNode = compute.getNodeMetadata(instanceId);
			if (existingNode != null && existingNode.getStatus() != NodeMetadata.Status.TERMINATED) {
				throw new MojoExecutionException("Developernode with tag " + instanceTag + " already exists with id: " + instanceId);
			} else {
				getLog().info("Existing node with tag " + instanceTag + " with id " + instanceId + " found in local configuration but not active in the backend service");
			}
		}
		
		TemplateCustomizer customizer = resolver.resolveCustomizer(instanceTag, provider, currentDeveloper.getProperties());
		customizer.customize(templateBuilder);
		try (OutputStream out = new FileOutputStream(developerNodeFile)){
			NodeMetadata node = getOnlyElement(compute.createNodesInGroup(groupName, 1, templateBuilder.build()));
			developerNodes.put(instanceTag, node.getId());
			developerNodes.store(out, null);
		} catch (RunNodesException e) {
			throw new MojoExecutionException("Failed to create node", e);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to store developer node details", e);
		}
		project.getFile();
	}

	private ComputeService initComputeService(String provider, String identity, String credential) throws MojoExecutionException {
		Properties properties = new Properties();
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(provider + ".defaultOverrides")) {
			properties.load(in);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to read provider default overrides", e);
		}

		if (currentDeveloper.getProperties().getProperty(instanceTag + "-overrides") != null) {
			try (InputStream in = new FileInputStream(currentDeveloper.getProperties().getProperty(instanceTag + "-overrides"))) {
				properties.load(in);
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to read overrides for instance tag " + instanceTag, e);
			}
		}

		long scriptTimeout = TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES);
		properties.setProperty(TIMEOUT_SCRIPT_COMPLETE, scriptTimeout + "");

		Iterable<Module> modules = ImmutableSet.<Module> of(
				new JschSshClientModule(),
				new SLF4JLoggingModule(),
				new EnterpriseConfigurationModule());

		ContextBuilder builder = ContextBuilder.newBuilder(provider)
				.credentials(identity, credential)
				.modules(modules)
				.overrides(properties);

		ComputeService ret = builder.buildView(ComputeServiceContext.class).getComputeService();
		return ret;
	}
}
