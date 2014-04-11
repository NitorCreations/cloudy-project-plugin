package com.nitorcreations.cloudyplugin;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.jclouds.compute.domain.NodeMetadata;


@Mojo( name = "terminate",  aggregator = true )
public class TerminateMojo extends AbstractCloudyMojo
{

	public void execute() throws MojoExecutionException, MojoFailureException	{
		super.execute();
		NodeMetadata existingNode = compute.getNodeMetadata(instanceId);
		if (instanceId == null || existingNode == null || existingNode.getStatus() == NodeMetadata.Status.TERMINATED) {
			throw new MojoExecutionException("Developernode with tag " + instanceTag + " does not exists with id: " + instanceId);
		} else {
			getLog().info("Existing node with tag " + instanceTag + " with id " + instanceId + " found in local configuration.");
		}

		try (OutputStream out = new FileOutputStream(developerNodeFile)){
			compute.destroyNode(instanceId);
			developerNodes.remove(instanceTag);
			developerNodes.store(out, null);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to store developer node details", e);
		}
		project.getFile();
	}
}
