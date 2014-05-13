package com.nitorcreations.cloudyplugin;

import static com.google.common.collect.Iterables.getOnlyElement;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE;
import static org.jclouds.compute.options.TemplateOptions.Builder.overrideLoginCredentials;
import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.ssh.jsch.config.JschSshClientModule;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.inject.Module;
import com.nitorcreations.cloudyplugin.logging.config.MavenLoggingModule;

public class AbstractCloudyMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", required = true)
    protected MavenProject project;
    @Parameter(property = "developerId", required = false)
    private String developerId;
    @Parameter(property = "securityConfiguration", required = false, defaultValue = "~/.m2/settings-security.xml")
    private String securityConfiguration;
    @Parameter(defaultValue = "default", property = "instanceTag", required = true)
    protected String instanceTag;
    @Parameter(property = "packages", required = false)
    protected String packages;
    @Parameter(property = "preinstallscript", required = false)
    protected String preinstallscript;
    @Parameter(property = "postinstallscript", required = false)
    protected String postinstallscript;
    @Parameter(property = "properties", required = false)
    protected Map<String, String> properties;
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
    protected final CustomizerResolver resolver = new GroovyCustomizerResolver();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (securityDispatcher instanceof DefaultSecDispatcher) {
            ((DefaultSecDispatcher) securityDispatcher).setConfigurationFile(securityConfiguration);
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
            if (developerId.equals(next.getId()) || matchAlias.matcher(userAliases).find()) {
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
        if (provider == null || provider.isEmpty() || identity == null || identity.isEmpty() || credential == null || credential.isEmpty()) {
            throw new MojoExecutionException("Some provider information missing - provider: '" + provider + "' indetity: '" + identity + "' credential: '" + credential + "'");
        }
        compute = initComputeService();
        File pom = project.getFile();
        developerNodeFile = new File(pom.getParentFile(), "." + currentDeveloper.getId() + "-nodes");
        if (developerNodeFile.exists()) {
            try (InputStream in = new FileInputStream(developerNodeFile)) {
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
        Properties overrideProperties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(provider + ".defaultOverrides")) {
            overrideProperties.load(in);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read provider default overrides", e);
        }
        List<String> otherOverrides = resolveAllSettings("overrides");
        for (int i = otherOverrides.size() - 1; i >= 0; i--) {
            try (InputStream in = new FileInputStream(otherOverrides.get(i))) {
                overrideProperties.load(in);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read overrides for instance tag " + instanceTag, e);
            }
        }
        long scriptTimeout = TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES);
        overrideProperties.setProperty(TIMEOUT_SCRIPT_COMPLETE, scriptTimeout + "");
        Iterable<Module> modules = ImmutableSet.<Module> of(new JschSshClientModule(), new MavenLoggingModule(getLog()), new EnterpriseConfigurationModule());
        ContextBuilder builder = ContextBuilder.newBuilder(provider).credentials(identity, credential).modules(modules).overrides(overrideProperties);
        context = builder.buildView(ComputeServiceContext.class);
        return context.getComputeService();
    }

    protected List<String> resolveAllSettings(String name) {
        ArrayList<String> ret = new ArrayList<>();
        if (currentDeveloper.getProperties().getProperty(instanceTag + "-" + name) != null) {
            ret.add(currentDeveloper.getProperties().getProperty(instanceTag + "-" + name));
        }
        if (currentDeveloper.getProperties().getProperty(provider + "-" + name) != null) {
            ret.add(currentDeveloper.getProperties().getProperty(provider + "-" + name));
        }
        if (currentDeveloper.getProperties().getProperty(name) != null) {
            ret.add(currentDeveloper.getProperties().getProperty(name));
        }
        try {
            Field field = getClass().getField(name);
            Object value = field.get(this);
            if (value != null) {
                ret.add(value.toString());
            }
        } catch (IllegalAccessException | NoSuchFieldException | SecurityException | IllegalArgumentException e) {
            // Oh well...
        }
        if (properties != null && properties.get(instanceTag + "-" + name) != null) {
            ret.add(properties.get(instanceTag + "-" + name));
        }
        if (properties != null && properties.get(provider + "-" + name) != null) {
            ret.add(properties.get(provider + "-" + name));
        }
        if (properties != null && properties.get(name) != null) {
            ret.add(properties.get(name));
        }
        return ret;
    }

    protected String resolveSetting(String name, String defaultValue) {
        List<String> ret = resolveAllSettings(name);
        if (ret.size() != 0) {
            return ret.get(0);
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
                if (in == null) {
                    throw new IOException("Classpath resource " + resource + " not found");
                }
                return new String(Streams.readAll(in), Charset.forName("UTF-8"));
            }
        }
        return Files.toString(new File(resource), Charset.forName("UTF-8"));
    }

    protected boolean waitForStatus(String nodeId, Status status, long timeout) {
        long end = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < end) {
            NodeMetadata state = compute.getNodeMetadata(nodeId);
            if ((state == null && status == Status.TERMINATED) || (state != null && state.getStatus() == status)) {
                return true;
            }
        }
        return false;
    }

    protected void runConfiguredScript(String parameterName) throws MojoExecutionException {
        String scriptResource = resolveSetting(parameterName, null);
        if (scriptResource != null && !scriptResource.isEmpty()) {
            try {
                String content = getResource(scriptResource);
                compute.runScriptOnNode(instanceId, exec(content), overrideLoginCredentials(login).runAsRoot(true).wrapInInitScript(false));
            } catch (Throwable e) {
                throw new MojoExecutionException("Failed to run " + parameterName, e);
            }
        }
    }

    protected void installPackages(String packageList) {
        if (packageList == null || packageList.isEmpty()) {
            return;
        }
        NodeMetadata node = compute.getNodeMetadata(instanceId);
        PackageInstallerBuilder pkg = PackageInstallerBuilder.create(node.getOperatingSystem());
        getLog().info("Installing packages " + packageList);
        for (String next : packageList.split(",")) {
            pkg.addPackage(next);
        }
        compute.runScriptOnNode(instanceId, exec(pkg.build()), overrideLoginCredentials(login).runAsRoot(true).wrapInInitScript(false));
    }

    protected String prettyPrint(final String nodeInfo) {
        StringBuilder ret = new StringBuilder();
        int level = 0;
        for (String next : nodeInfo.split(",\\s")) {
            for (int i = 0; i < level; i++) {
                ret.append("   ");
            }
            int levelUps = occurrances(next, '{');
            int levelDowns = occurrances(next, '}');
            level = level + levelUps - levelDowns;
            ret.append(next).append("\n");
        }
        return ret.toString();
    }

    private int occurrances(final String next, final char find) {
        int ret = 0;
        for (int i = 0; i < next.length(); i++) {
            if (next.charAt(i) == find) {
                ret++;
            }
        }
        return ret;
    }

    protected void initNode() throws MojoExecutionException {
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
        try (OutputStream out = new FileOutputStream(developerNodeFile)) {
            node = getOnlyElement(compute.createNodesInGroup(groupName, 1, templateBuilder.build()));
            instanceId = node.getId();
            developerNodes.put(instanceTag, instanceId);
            developerNodes.store(out, null);
        } catch (RunNodesException e) {
            throw new MojoExecutionException("Failed to create node", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to store developer node details", e);
        }
        runConfiguredScript("preinstallscript");
        installPackages(resolveSetting("packages", null));
        runConfiguredScript("postinstallscript");
    }

    protected NodeMetadata resumeNode() throws MojoExecutionException {
        try {
            compute.resumeNode(instanceId);
        } catch (Throwable e) {
            getLog().info("Error in resuming node: " + e.getMessage());
            getLog().debug(e);
        }
        NodeMetadata nodeMetadata = compute.getNodeMetadata(instanceId);
        if (nodeMetadata == null || nodeMetadata.getStatus() != NodeMetadata.Status.RUNNING) {
            throw new MojoExecutionException("Failed to resume node " + instanceId);
        }
        return nodeMetadata;
    }
}
