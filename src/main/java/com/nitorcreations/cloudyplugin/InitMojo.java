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

import static org.jclouds.compute.options.TemplateOptions.Builder.overrideLoginCredentials;
import static org.jclouds.scriptbuilder.domain.Statements.exec;

@Mojo( name = "init",  aggregator = true )
public class InitMojo extends AbstractCloudyMojo
{
	private final CustomizerResolver resolver;

	public InitMojo() {
		this.resolver = new GroovyCustomizerResolver();
	}

	@Override
    public void execute() throws MojoExecutionException, MojoFailureException	{
		super.execute();
		String groupName = project.getGroupId().replaceAll("[^a-zA-Z\\-]", "-") + "-" + project.getArtifactId().replaceAll("[^a-zA-Z\\-]", "-");
		if (instanceId != null) {
			NodeMetadata existingNode = compute.getNodeMetadata(instanceId);
			if (existingNode != null && existingNode.getStatus() != NodeMetadata.Status.TERMINATED) {
				throw new MojoExecutionException("Developernode with tag " + instanceTag + " already exists with id: " + instanceId);
			} 
			getLog().info("Existing node with tag " + instanceTag + " with id " + instanceId + " found in local configuration but not active in the backend service");
		}

		TemplateCustomizer customizer = resolver.resolveCustomizer(instanceTag, provider, currentDeveloper.getProperties());
		TemplateBuilder templateBuilder = compute.templateBuilder();
		customizer.customize(templateBuilder);
		NodeMetadata node;
		try (OutputStream out = new FileOutputStream(developerNodeFile)){
			node = getOnlyElement(compute.createNodesInGroup(groupName, 1, templateBuilder.build()));
			developerNodes.put(instanceTag, node.getId());
			developerNodes.store(out, null);
		} catch (RunNodesException e) {
			throw new MojoExecutionException("Failed to create node", e);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to store developer node details", e);
		}
		
		String preInstallScript = resolveSetting("preinstallscript", null);
		if (preInstallScript != null && !preInstallScript.isEmpty()) {
		    try {
                String content = getResource(preInstallScript);
                compute.runScriptOnNode(node.getId(), exec(content), 
                    overrideLoginCredentials(login).runAsRoot(true).wrapInInitScript(false));
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read preinstall script", e);
            }
		}
		PackageInstallerBuilder pkg = PackageInstallerBuilder.create(node.getOperatingSystem());
		String pkgs = resolveSetting("packages", null);
		if (pkgs != null && !pkgs.isEmpty()) {
		    getLog().info("Installing packages " + pkgs);
		    for (String next : pkgs.split(",")) {
		        pkg.addPackage(next);
		    }
            compute.runScriptOnNode(node.getId(), exec(pkg.build()), 
                overrideLoginCredentials(login).runAsRoot(true).wrapInInitScript(false));
		}

	}
}
