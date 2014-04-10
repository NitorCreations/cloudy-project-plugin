package com.nitorcreations.cloudyplugin;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Properties;

import javax.inject.Named;

import org.apache.maven.plugin.MojoExecutionException;
import org.bouncycastle.util.io.Streams;

import com.google.common.io.Files;

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
			Class customizerClass = loader.parseClass(getResource(customizerResource));
			return (TemplateCustomizer)customizerClass.newInstance();
		} catch (IOException | InstantiationException | IllegalAccessException e) {
			throw new MojoExecutionException("Failed to run templage customizer script" , e);
		}
	}

	private String getResource(String resource) throws IOException {
		if (resource.startsWith("classpath:")) {
			InputStream in = getClass().getResourceAsStream("/" + resource.substring(10));
			if (in == null) throw new IOException("Classpath resource " + resource + " not found");
			return new String(Streams.readAll(in), Charset.forName("UTF-8"));
		} else {
			return Files.toString(new File(resource), Charset.forName("UTF-8"));
		}
	}

}
