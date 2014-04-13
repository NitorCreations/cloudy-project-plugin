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
		if (instanceId != null) {
			NodeMetadata existingNode = compute.getNodeMetadata(instanceId);
			if (existingNode == null || existingNode.getStatus() == NodeMetadata.Status.TERMINATED) {
				throw new MojoExecutionException("Developernode with tag " + instanceTag + " does not exists with id: " + instanceId);
			} else {
				getLog().info("Existing node with tag " + instanceTag + " with id " + instanceId + " found in local configuration.");
			}
		} else {
			getLog().info("Existing node with tag " + instanceTag + " not found in local configuration.");
			return;
		}
		try  {
			compute.destroyNode(instanceId);
		} catch (Throwable e) {
			getLog().info("Error in deleting node: " + e.getMessage());
			getLog().debug(e);
		}

		try (OutputStream out = new FileOutputStream(developerNodeFile)){
			NodeMetadata deletedNode = compute.getNodeMetadata(instanceId);
			if (deletedNode == null || deletedNode.getStatus() == NodeMetadata.Status.TERMINATED) {
  			  compute.destroyNode(instanceId);
			  developerNodes.remove(instanceTag);
			  developerNodes.store(out, null);
			} else {
				throw new MojoExecutionException("Failed to terminate node " + instanceId);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to store developer node details", e);
		}
	}
}
