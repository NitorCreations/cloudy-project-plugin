package com.nitorcreations.cloudyplugin;

import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;

public interface CustomizerResolver {
	public TemplateCustomizer resolveCustomizer(String instanceTag, String provider, Properties developerProperties) throws MojoExecutionException;
}
