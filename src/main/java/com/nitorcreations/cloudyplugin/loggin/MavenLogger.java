package com.nitorcreations.cloudyplugin.loggin;


import org.apache.maven.plugin.logging.Log;
import org.jclouds.logging.BaseLogger;
import org.jclouds.logging.Logger;



public class MavenLogger extends BaseLogger {
	private final String category;
	private Log logger;

	public static class MavenLoggerFactory implements LoggerFactory {
		private Log logger;

		public MavenLoggerFactory(Log logger) {
			this.logger = logger;
		}

		@Override
        public Logger getLogger(String category) {
			return new MavenLogger(category, logger);
		}
	}

	public MavenLogger(String category, Log logger) {
		this.category = category;
		this.logger = logger;
	}

	@Override
	protected void logTrace(String message) {
		logger.debug(message);
	}

	@Override
    public boolean isTraceEnabled() {
		return logger.isDebugEnabled();
	}

	@Override
	protected void logDebug(String message) {
		logger.debug(message);
	}

	@Override
    public boolean isDebugEnabled() {
		return logger.isDebugEnabled();
	}

	@Override
	protected void logInfo(String message) {
		logger.info(message);
	}

	@Override
    public boolean isInfoEnabled() {
		return logger.isInfoEnabled();
	}

	@Override
	protected void logWarn(String message) {
		logger.warn(message);
	}

	@Override
	protected void logWarn(String message, Throwable e) {
		logger.warn(message, e);
	}

	@Override
    public boolean isWarnEnabled() {
		return logger.isWarnEnabled();
	}

	@Override
	protected void logError(String message) {
		logger.error(message);
	}

	@Override
	protected void logError(String message, Throwable e) {
		logger.error(message, e);
	}

	@Override
    public boolean isErrorEnabled() {
		return logger.isWarnEnabled();
	}

	@Override
    public String getCategory() {
		return category;
	}
}
