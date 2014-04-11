package com.nitorcreations.cloudyplugin;

import static com.google.common.collect.Iterables.getOnlyElement;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;


@Mojo( name = "init",  aggregator = true )
public class InitMojo extends AbstractCloudyMojo
{
	private final CustomizerResolver resolver;

	public InitMojo() {
		this.resolver = new GroovyCustomizerResolver();
	}

	public void execute() throws MojoExecutionException, MojoFailureException	{
		super.execute();
		String groupName = project.getGroupId().replaceAll("[^a-zA-Z\\-]", "-") + "-" + project.getArtifactId().replaceAll("[^a-zA-Z\\-]", "-");
		if (instanceId != null) {
			NodeMetadata existingNode = compute.getNodeMetadata(instanceId);
			if (existingNode != null && existingNode.getStatus() != NodeMetadata.Status.TERMINATED) {
				throw new MojoExecutionException("Developernode with tag " + instanceTag + " already exists with id: " + instanceId);
			} else {
				getLog().info("Existing node with tag " + instanceTag + " with id " + instanceId + " found in local configuration but not active in the backend service");
			}
		}

		TemplateCustomizer customizer = resolver.resolveCustomizer(instanceTag, provider, currentDeveloper.getProperties());
		TemplateBuilder templateBuilder = compute.templateBuilder();
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
}
