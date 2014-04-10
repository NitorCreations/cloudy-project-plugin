package com.nitorcreations.cloudyplugin;

import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.maven.model.Developer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

public class AbstractCloudyMojo extends AbstractMojo {

	@Parameter( defaultValue = "${project}", required = true )
	protected MavenProject project;

	@Parameter ( property = "developerId", required = false )
	private String developerId;
	
	@Parameter ( property = "securityConfiguration", required = false, defaultValue =  "~/.m2/settings-security.xml")
	private String securityConfiguration;

    @Component
    private SecDispatcher securityDispatcher;
    
    protected Developer currentDeveloper;
	protected String provider;
	protected String identity;
	protected String credential;
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (securityDispatcher instanceof DefaultSecDispatcher) {
			((DefaultSecDispatcher)securityDispatcher).setConfigurationFile(securityConfiguration);
		}
		if (developerId == null) {
			developerId = System.getProperty("user.name", "");
		}
		if (developerId.isEmpty()) {
			throw new MojoExecutionException("Failed to get a developer id. Please define one with -DdeveloperId=foo");
		}
		Pattern matchAlias = Pattern.compile("(,|^)" + developerId + "(,|$)");
		for (Developer next : project.getDevelopers()) {
			String userAliases = next.getProperties().getProperty("useraliases", "");
			if (developerId.equals(next.getId()) || matchAlias.matcher(userAliases).find() ) {
				currentDeveloper = next;
				break;
			}
		}
		if (currentDeveloper == null) {
			throw new MojoExecutionException("No matching developer entry found. Add a developer entry with id " + developerId + " or the same as 'useralias' property for a developer");
		} else {
			getLog().info("Using properties from developer " + currentDeveloper.getId());
		}
		Properties developerProperties = currentDeveloper.getProperties();
		provider = developerProperties.getProperty("provider");
		try {
			identity = securityDispatcher.decrypt(developerProperties.getProperty("identity"));
			credential = securityDispatcher.decrypt(developerProperties.getProperty("credential"));
		} catch (SecDispatcherException e) {
			e.printStackTrace();
		}
		if (provider == null || provider.isEmpty() || 
				identity == null || identity.isEmpty() ||
				credential == null || credential.isEmpty()) {
			throw new MojoExecutionException("Some provider information missing - provider: '" + provider +
					"' indetity: '" + identity + "' credential: '" + credential + "'");
		}
	}
}
