package com.nitorcreations.cloudyplugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.jclouds.compute.domain.NodeMetadata;

@Mojo(name = "nodeinfo", aggregator = true)
public class NodeInfoMojo extends AbstractCloudyMojo {
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        if (instanceId != null) {
            NodeMetadata existingNode = compute.getNodeMetadata(instanceId);
            if (existingNode == null || existingNode.getStatus() == NodeMetadata.Status.TERMINATED) {
                throw new MojoExecutionException("Developernode with tag " + instanceTag + " does not exists with id: " + instanceId);
            }
            getLog().info("Existing node with tag " + instanceTag + " with id " + instanceId + " found in local configuration.");
            if (getLog().isInfoEnabled()) {
                getLog().info(prettyPrint(existingNode.toString()));
            } else {
                System.out.print(prettyPrint(existingNode.toString()));
            }
        } else {
            throw new MojoExecutionException("Existing node with tag " + instanceTag + " not found in local configuration.");
        }
    }
}
