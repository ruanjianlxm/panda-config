package com.panda.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class PropertyUnitFactory {
	private static final Logger logger = LoggerFactory
			.getLogger(PropertyUnitFactory.class);
	private static final String DEFAULT_CONFIG_NAMESPACE = "/config-center";
	private static final String DEFAULT_UNIT_NAMESPACE = "/unit-center";

	private static final String CONFIG_PROJECT_KEY = "project.name";
	private static final String CONFIG_FILE_VALUE = "config.properties";
	private static final String CONFIG_REMOTE_CENTER_KEY = "config.zookeeper";
	private static final String CONFIG_SUPPORT_DYNAMIC_KEY = "config.support.dynamic";
	private static final String CONFIG_PUSH_TO_REMOTE = "config.push.to.remote";
	private static final String CONFIG_USE_REMOTE = "config.use.remote";

	private static final String UNIT_USE_REMOTE = "unit.use.remote";
	private static final String UNIT_PUSH_TO_REMOTE = "unit.push.to.remote";
	private static final String UNIT_LOCAL_PATH = "unit.local.path";
	private static final String UNIT_SUPPORT_DYNAMIC_KEY = "unit.support.dynamic";
	private static final String IGNORE_UNIT_WATCH_INTERVAL = "unit.change.ingore.interval";

	private static final String EXCLUDE_CONFIG = "config.exclude";
	private static final String EXCLUDE_UNIT = "unit.exclude";

	private static PropertyUnit configBundle;
	private static volatile String configPath;
	private String remoteUnitPath;
	private String localUnitPath;
	private String project;
	private String configRemoteHost;
	private String configSupportDynamic;
	private String configUseRemote;
	private String configPushToRemote;
	private String unitUseRemote;
	private String unitPushToRemote;
	private String unitSupportDynamic;
	private String ingoreWatchInterval;
	private String excludeConfig;
	private String excludeUnit;

	private CuratorFramework zkClient;

	public PropertyUnitFactory() {
		try {

			Path file = Paths.get(this.getClass().getClassLoader()
					.getResource(CONFIG_FILE_VALUE).toURI());
			FileReader fr = null;
			fr = new FileReader(file.toFile().getPath());
			Properties properties = new Properties();
			properties.load(fr);// load()方法可通过字符流直接加载文件

			project = properties.getProperty(CONFIG_PROJECT_KEY);
			configRemoteHost = properties.getProperty(CONFIG_REMOTE_CENTER_KEY);

			configSupportDynamic = properties
					.getProperty(CONFIG_SUPPORT_DYNAMIC_KEY);
			configPushToRemote = properties.getProperty(CONFIG_PUSH_TO_REMOTE);
			configUseRemote = properties.getProperty(CONFIG_USE_REMOTE);
			excludeConfig = properties.getProperty(EXCLUDE_CONFIG);

			unitUseRemote = properties.getProperty(UNIT_USE_REMOTE);
			unitPushToRemote = properties.getProperty(UNIT_PUSH_TO_REMOTE);
			excludeUnit = properties.getProperty(EXCLUDE_UNIT);
			unitSupportDynamic = properties
					.getProperty(UNIT_SUPPORT_DYNAMIC_KEY);
			localUnitPath = properties.getProperty(UNIT_LOCAL_PATH);
			ingoreWatchInterval = properties
					.getProperty(IGNORE_UNIT_WATCH_INTERVAL);

			logger.info("stc config configSupportDynamic load:"
					+ configSupportDynamic);
			if (StringUtils.isBlank(configSupportDynamic)) {
				configSupportDynamic = "false";
			}

			if (StringUtils.isBlank(configPushToRemote)) {
				configPushToRemote = "false";
			}

			if (StringUtils.isBlank(configUseRemote)) {
				configUseRemote = "false";
			}

			if (StringUtils.isBlank(unitUseRemote)) {
				unitUseRemote = "false";
			}

			if (StringUtils.isBlank(unitPushToRemote)) {
				unitPushToRemote = "false";
			}

			if (StringUtils.isBlank(unitSupportDynamic)) {
				unitSupportDynamic = "false";
			}

			if (StringUtils.isBlank(localUnitPath)) {
				String unitpath = this.getClass().getResource("/").getPath();
				localUnitPath = unitpath.substring(0,
						unitpath.lastIndexOf("bin"))
						+ "units";
			}

			if (StringUtils.isBlank(ingoreWatchInterval)) {
				ingoreWatchInterval = "60000";
			}

			checkValueNotNull(CONFIG_PROJECT_KEY, project);
			checkValueNotNull(CONFIG_REMOTE_CENTER_KEY, configRemoteHost);

			generateZKPath();
			RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
			zkClient = CuratorFrameworkFactory.newClient(configRemoteHost,
					retryPolicy);
			zkClient.start();

			if (Boolean.valueOf(configPushToRemote)) {
				deleteRemoteConfig(configPath);
			}

			loadUnitProperties();

			fr.close();
		} catch (IOException | URISyntaxException e) {
			logger.error("initConfigBundle error:{}", e);
		}
	}

	public PropertyUnit createConfigBundle(String configName,
			PropertyChangeListener propertyListener) {
		return new PropertyUnit(configPath.concat("/").concat(configName),
				configName, excludeConfig, zkClient,
				Boolean.valueOf(configUseRemote),
				Boolean.valueOf(configPushToRemote),
				Boolean.valueOf(configSupportDynamic), propertyListener);
	}

	public void loadUnitProperties() {
		if (Boolean.valueOf(unitUseRemote) || Boolean.valueOf(unitPushToRemote)) {
			UnitConfig config = new UnitConfig(remoteUnitPath, localUnitPath,
					excludeUnit, zkClient, Boolean.valueOf(unitUseRemote),
					Boolean.valueOf(unitPushToRemote),
					Boolean.valueOf(unitSupportDynamic),
					Long.parseLong(ingoreWatchInterval), null);
		} else {
			logger.debug("Will use local unit cfg file");
		}
	}

	protected void checkValueNotNull(String key, String value) {
		if (StringUtils.isBlank(value)) {
			throw new RuntimeException("properties load key is null:" + key);
		}
	}

	private void generateZKPath() {
		if (configPath == null) {
			checkNotNull(project, "project name can't be null");
			configPath = DEFAULT_CONFIG_NAMESPACE.concat("/").concat(project);
		}

		if (remoteUnitPath == null) {
			checkNotNull(project, "project name can't be null");
			remoteUnitPath = DEFAULT_UNIT_NAMESPACE.concat("/").concat(project);
		}
	}

	/**
	 * factory method
	 * 
	 * @return
	 */
	public PropertyUnit getConfigBundle() {
		return configBundle;
	}

	private void checkNotNull(String str, String errorLogMsg) {
		if (StringUtils.isBlank(str)) {
			logger.error(errorLogMsg);
			throw new NullPointerException(errorLogMsg);
		}
	}

	private void deleteRemoteConfig(String path) {
		try {
			if (zkClient.checkExists().forPath(path) != null) {
				logger.debug("Deleting old path before pushing to remote"
						+ path);
				zkClient.delete().deletingChildrenIfNeeded().forPath(path);
			}
		} catch (Exception e) {
			logger.error("up init error:{}", e);
		}
	}
}
