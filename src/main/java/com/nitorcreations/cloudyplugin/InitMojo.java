package com.nitorcreations.cloudyplugin;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.jclouds.aws.ec2.reference.AWSEC2Constants.PROPERTY_EC2_AMI_QUERY;
import static org.jclouds.aws.ec2.reference.AWSEC2Constants.PROPERTY_EC2_CC_AMI_QUERY;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE;
import static org.jclouds.compute.options.TemplateOptions.Builder.overrideLoginCredentials;
import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.File;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jclouds.ssh.jsch.config.JschSshClientModule;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.inject.Module;

@Mojo(name = "init")
public class InitMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", required = true)
    private MavenProject project;
    @Parameter(defaultValue = "none", property = "ciHost", required = true)
    private String ciHost;
    @Parameter(property = "identity", required = true)
    private String identity;

    @Override
    public void execute() throws MojoExecutionException {
        if ("none".equals(ciHost)) {
            try (Scanner s = new Scanner(System.in)) {
                String provider = "aws-ec2";
                String groupName = project.getGroupId().replaceAll("[^a-zA-Z\\-]", "-") + "-" + project.getArtifactId().replaceAll("[^a-zA-Z\\-]", "-");
                getLog().info("Please give aws credential");
                String credential = s.next();
                //LoginCredentials login =  getLoginForCommandExecution();
                ComputeService compute = initComputeService(provider, identity, credential);
                System.out.printf(">> adding node to group %s%n", groupName);
                TemplateBuilder templateBuilder = compute.templateBuilder();
                //templateBuilder = templateBuilder.imageId("us-east-1/ami-d5ddd9bc");
                Statement bootInstructions = AdminAccess.standard();
                templateBuilder.options(EC2TemplateOptions.Builder.inboundPorts(22, 80, 8080).runScript(bootInstructions));
                LoginCredentials login = getLoginForCommandExecution();
                NodeMetadata node = getOnlyElement(compute.createNodesInGroup(groupName, 1, templateBuilder.build()));
                getLog().info(String.format("<< node %s: %s%n", node.getId(), concat(node.getPrivateAddresses(), node.getPublicAddresses())));
                getLog().info(String.format("Private key: %s\n", node.getCredentials().getOptionalPrivateKey().or("")));
                getLog().info(String.format("Identity: %s\n", node.getCredentials().identity));
                compute.runScriptOnNode(node.getId(), exec("yum install -y java-1.7.0-openjdk-devel"), overrideLoginCredentials(login).runAsRoot(true).wrapInInitScript(false));
            } catch (Exception e) {
                throw new MojoExecutionException("", e);
            }
        }
        project.getFile();
    }

    private static ComputeService initComputeService(final String provider, final String identity, final String credential) {
        // example of specific properties, in this case optimizing image list to
        // only amazon supplied
        Properties properties = new Properties();
        properties.setProperty(PROPERTY_EC2_AMI_QUERY, "owner-id=137112412989;state=available;image-type=machine");
        properties.setProperty(PROPERTY_EC2_CC_AMI_QUERY, "");
        long scriptTimeout = TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES);
        properties.setProperty(TIMEOUT_SCRIPT_COMPLETE, scriptTimeout + "");
        // example of injecting a ssh implementation
        Iterable<Module> modules = ImmutableSet.<Module> of(new JschSshClientModule(), new SLF4JLoggingModule(), new EnterpriseConfigurationModule());
        ContextBuilder builder = ContextBuilder.newBuilder(provider).credentials(identity, credential).modules(modules).overrides(properties);
        System.out.printf(">> initializing %s%n", builder.getApiMetadata());
        return builder.buildView(ComputeServiceContext.class).getComputeService();
    }

    private static LoginCredentials getLoginForCommandExecution() {
        String user = System.getProperty("user.name");
        LoginCredentials.Builder builder = LoginCredentials.builder().user(user);
        try {
            String privateKey = Files.toString(new File(System.getProperty("user.home") + "/.ssh/id_rsa"), UTF_8);
            if (!privateKey.contains("Proc-Type: 4,ENCRYPTED")) {
                builder.privateKey(privateKey).build();
            }
        } catch (Exception e) {
            System.err.println("error reading ssh key " + e.getMessage());
            return null;
        }
        return builder.build();
    }
}
