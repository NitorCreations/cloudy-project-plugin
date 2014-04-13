package com.nitorcreations.cloudyplugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.jclouds.compute.domain.NodeMetadata;


@Mojo( name = "nodeinfo",  aggregator = true )
public class RebootMojo extends AbstractCloudyMojo
{

	public void execute() throws MojoExecutionException, MojoFailureException	{
		super.execute();
		if (instanceId != null) {
			NodeMetadata existingNode = compute.getNodeMetadata(instanceId);
			if (existingNode == null || existingNode.getStatus() == NodeMetadata.Status.TERMINATED) {
				throw new MojoExecutionException("Developernode with tag " + instanceTag + " does not exists with id: " + instanceId);
			} else {
				getLog().info("Existing node with tag " + instanceTag + " with id " + instanceId + " found in local configuration.");
				if (getLog().isInfoEnabled()) {
					getLog().info(prettyPrint(existingNode.toString()));
				} else {
					System.out.print(prettyPrint(existingNode.toString()));
				}
			}
			
		} else {
			throw new MojoExecutionException("Existing node with tag " + instanceTag + " not found in local configuration.");
		}
	}
	private String prettyPrint(String nodeInfo) {
		StringBuilder ret = new StringBuilder();
		int level = 0;
		for (String next : nodeInfo.split(",\\s")) {
			for (int i=0; i<level;i++) {
				ret.append("   ");
			}
			int levelUps= occurrances(next, '{');
			int levelDowns= occurrances(next, '}');
			level = level + levelUps - levelDowns;
			ret.append(next).append("\n");
		}
		return ret.toString();
	}
	private int occurrances(String next, char find) {
		int ret=0;
		for (int i=0; i<next.length(); i++) {
			if (next.charAt(i) == find) ret++;
		}
		return ret;
	}
}
