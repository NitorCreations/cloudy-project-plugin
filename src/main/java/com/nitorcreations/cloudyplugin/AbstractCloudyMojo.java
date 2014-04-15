package com.nitorcreations.cloudyplugin;

import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.maven.model.Developer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.bouncycastle.util.io.Streams;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.ssh.jsch.config.JschSshClientModule;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.inject.Module;
import com.nitorcreations.cloudyplugin.loggin.config.MavenLoggingModule;

public class AbstractCloudyMojo extends AbstractMojo {

	@Parameter( defaultValue = "${project}", required = true )
	protected MavenProject project;

	@Parameter ( property = "developerId", required = false )
	private String developerId;

	@Parameter ( property = "securityConfiguration", required = false, defaultValue =  "~/.m2/settings-security.xml")
	private String securityConfiguration;

	@Parameter( defaultValue = "default", property = "instanceTag", required = true )
	protected String instanceTag;

	@Parameter( property = "packages", required = false )
    protected String packages;

	@Component
    private SecDispatcher securityDispatcher;

    protected Developer currentDeveloper;
	protected String provider;
	protected String identity;
	protected String credential;
	protected String instanceId;
    protected ComputeServiceContext context;
	protected ComputeService compute;
	protected LoginCredentials login;
	protected Properties developerNodes = new Properties();
	protected File developerNodeFile;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (securityDispatcher instanceof DefaultSecDispatcher) {
			((DefaultSecDispatcher)securityDispatcher).setConfigurationFile(securityConfiguration);
		}
		String user = System.getProperty("user.name", "");
		login = LoginCredentials.builder().user(user).build();
		if (developerId == null) {
			developerId = user;
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
		} 
		
		getLog().info("Using properties from developer " + currentDeveloper.getId());
		
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
		compute = initComputeService();
		File pom = project.getFile();
		developerNodeFile = new File(pom.getParentFile(), "." + currentDeveloper.getId() + "-nodes");
		if (developerNodeFile.exists()) {
			try (InputStream in = new FileInputStream(developerNodeFile)){
				developerNodes.load(in);
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to read developer node data", e);
			}
		}

		if (developerNodes.getProperty(instanceTag) != null) {
			instanceId = developerNodes.getProperty(instanceTag);
		}
	}

	protected ComputeService initComputeService() throws MojoExecutionException {
		Properties properties = new Properties();
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(provider + ".defaultOverrides")) {
			properties.load(in);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to read provider default overrides", e);
		}

		if (currentDeveloper.getProperties().getProperty(instanceTag + "-overrides") != null) {
			try (InputStream in = new FileInputStream(currentDeveloper.getProperties().getProperty(instanceTag + "-overrides"))) {
				properties.load(in);
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to read overrides for instance tag " + instanceTag, e);
			}
		}

		long scriptTimeout = TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES);
		properties.setProperty(TIMEOUT_SCRIPT_COMPLETE, scriptTimeout + "");

		Iterable<Module> modules = ImmutableSet.<Module> of(
				new JschSshClientModule(),
				new MavenLoggingModule(getLog()),
				new EnterpriseConfigurationModule());

		ContextBuilder builder = ContextBuilder.newBuilder(provider)
				.credentials(identity, credential)
				.modules(modules)
				.overrides(properties);
		context = builder.buildView(ComputeServiceContext.class);
		return context.getComputeService();
	}
	
	protected String resolveSetting(String name, String defaultValue) {
	    if (currentDeveloper.getProperties().getProperty(instanceTag + "-" + name) != null) {
	        return currentDeveloper.getProperties().getProperty(instanceTag + "-" + name);
	    } 
	    if (currentDeveloper.getProperties().getProperty(name) != null) {
	        return currentDeveloper.getProperties().getProperty(name);
	    }
        try {
            Field field = getClass().getField(name);
            Object value = field.get(this);
            if (value != null) return value.toString();
        } catch (IllegalAccessException | NoSuchFieldException | SecurityException | IllegalArgumentException e) {
            // Oh well...
        }
        return defaultValue;
	}

    public static String getResource(String resource) throws IOException {
    	if (resource.startsWith("classpath:")) {
    	    String res = resource.substring(10);
    	    if (!res.startsWith("/")) {
    	        res = "/" + res;
    	    }
    		try (InputStream in = AbstractCloudyMojo.class.getResourceAsStream(res)) {
    		    if (in == null) throw new IOException("Classpath resource " + resource + " not found");
    		    return new String(Streams.readAll(in), Charset.forName("UTF-8"));
    		}
    	}
    	return Files.toString(new File(resource), Charset.forName("UTF-8"));
    }
    
    protected boolean waitForStatus(String nodeId, Status status, long timeout) {
        long end = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < end) {
            NodeMetadata state = compute.getNodeMetadata(nodeId); 
            if ((state == null && status == Status.TERMINATED) ||
                (state != null && state.getStatus() == status)       ) {
                return true;
            }
        }
        return false;
    }
}
