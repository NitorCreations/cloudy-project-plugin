package com.nitorcreations.cloudyplugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.jclouds.compute.domain.NodeMetadata;

@Mojo(name = "safenodeinfo", aggregator = true)
public class SafeNodeInfoMojo extends AbstractCloudyMojo {
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        if (instanceId == null) {
            getLog().info("Existing node with tag " + instanceTag + " not found, initializing.");
            initNode();
        }
        NodeMetadata nodeMetadata = compute.getNodeMetadata(instanceId);
        if (nodeMetadata == null || nodeMetadata.getStatus() == NodeMetadata.Status.TERMINATED) {
            throw new MojoExecutionException("Developernode with tag " + instanceTag + " does not exists with id: " + instanceId);
        }
        getLog().info("Existing node with tag " + instanceTag + " with id " + instanceId + " found in local configuration.");
        if (nodeMetadata.getStatus() == NodeMetadata.Status.SUSPENDED) {
            getLog().info("Node is suspended, resuming.");
            nodeMetadata = resumeNode();
        }
        if (getLog().isInfoEnabled()) {
            getLog().info(prettyPrint(nodeMetadata.toString()));
        } else {
            System.out.print(prettyPrint(nodeMetadata.toString()));
        }
    }
}
