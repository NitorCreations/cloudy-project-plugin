package com.nitorcreations.cloudyplugin.logging.config;

import org.apache.maven.plugin.logging.Log;
import org.jclouds.logging.Logger.LoggerFactory;
import org.jclouds.logging.config.LoggingModule;

import com.nitorcreations.cloudyplugin.logging.MavenLogger.MavenLoggerFactory;

public class MavenLoggingModule extends LoggingModule {
    private Log logger;

    public MavenLoggingModule(Log logger) {
        this.logger = logger;
    }

    @Override
    public LoggerFactory createLoggerFactory() {
        return new MavenLoggerFactory(logger);
    }
}
