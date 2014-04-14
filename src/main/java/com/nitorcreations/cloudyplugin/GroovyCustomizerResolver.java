package com.nitorcreations.cloudyplugin;

import groovy.lang.GroovyClassLoader;

import java.io.IOException;
import java.util.Properties;

import javax.inject.Named;

import org.apache.maven.plugin.MojoExecutionException;

@Named
public class GroovyCustomizerResolver implements CustomizerResolver {

	public GroovyCustomizerResolver() {
	}
	
	@Override
	public TemplateCustomizer resolveCustomizer(String instanceTag,
			String provider, Properties developerProperties) throws MojoExecutionException {
		String customizerResource = "classpath:" + provider + "-customizer.groovy";
		if (developerProperties.getProperty(instanceTag + "-customizer") != null) {
			customizerResource = developerProperties.getProperty(instanceTag + "-customizer");
		} else if (developerProperties.getProperty(provider + "-customizer") != null) {
			customizerResource = developerProperties.getProperty(provider + "-customizer");
		}
		try (GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader())) {
			@SuppressWarnings("rawtypes")
			Class customizerClass = loader.parseClass(AbstractCloudyMojo.getResource(customizerResource));
			return (TemplateCustomizer)customizerClass.newInstance();
		} catch (IOException | InstantiationException | IllegalAccessException e) {
			throw new MojoExecutionException("Failed to run templage customizer script" , e);
		}
	}

}
