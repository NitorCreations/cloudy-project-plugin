package com.nitorcreations.cloudyplugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "init", aggregator = true)
public class InitMojo extends AbstractCloudyMojo {
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        initNode();
    }
}
