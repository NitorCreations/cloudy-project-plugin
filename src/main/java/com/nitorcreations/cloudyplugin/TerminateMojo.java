package com.nitorcreations.cloudyplugin;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;

@Mojo(name = "terminate", aggregator = true)
public class TerminateMojo extends AbstractCloudyMojo {
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        if (instanceId != null) {
            NodeMetadata existingNode = compute.getNodeMetadata(instanceId);
            if (existingNode == null || existingNode.getStatus() == NodeMetadata.Status.TERMINATED) {
                removeNode();
                throw new MojoExecutionException("Developernode with tag " + instanceTag + " does not exists with id: " + instanceId);
            }
            getLog().info("Existing node with tag " + instanceTag + " with id " + instanceId + " found in local configuration.");
        } else {
            getLog().info("Existing node with tag " + instanceTag + " not found in local configuration.");
            return;
        }
        try {
            compute.destroyNode(instanceId);
        } catch (Throwable e) {
            getLog().info("Error in deleting node: " + e.getMessage());
            getLog().debug(e);
        }
        if (waitForStatus(instanceId, Status.TERMINATED, 180000)) {
            removeNode();
        } else {
            throw new MojoExecutionException("Failed to terminate node " + instanceId);
        }
    }

    private void removeNode() throws MojoExecutionException {
        try (OutputStream out = new FileOutputStream(developerNodeFile)) {
            developerNodes.remove(instanceTag);
            developerNodes.store(out, null);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to store developer node details", e);
        }
    }
}
