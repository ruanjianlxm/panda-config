package com.panda.config;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jason
 * 
 */
public class UnitConfig {
	private static final Logger logger = LoggerFactory
			.getLogger(RemoteProperty.class);
	private static CuratorFramework zkClient;
	private static final String DEFAULT_ENCODE = "utf-8";
	private String remoteUnitPath;
	private String localUnitPath;
	private long ingoreUnitWatchInterval;
	private PropertyChangeListener propertyListener;
	private List<String> watchPathList = new ArrayList<String>();
	private long createTime;
	private volatile boolean hadDeleted = false;
	private String[] excludeUnits;

	public UnitConfig(String remoteUnitPath, String localUnitPath,
			String excludeUnit, CuratorFramework zkClient, boolean useRemote,
			boolean pushtoRemote, boolean isDynamic,
			long ingoreUnitWatchInterval,
			PropertyChangeListener propertyListener) {
		this.remoteUnitPath = remoteUnitPath;
		this.localUnitPath = localUnitPath;
		if (StringUtils.isNotEmpty(excludeUnit)) {
			this.excludeUnits = excludeUnit.split(",");
		}

		this.zkClient = zkClient;
		this.propertyListener = propertyListener;
		this.ingoreUnitWatchInterval = ingoreUnitWatchInterval;
		this.createTime = System.currentTimeMillis();
		if (pushtoRemote) {
			pushConfigToRemote(localUnitPath);
		}
		if (useRemote) {
			String unitpath = this.getClass().getResource("/").getPath();
			unitpath = unitpath.substring(0, unitpath.lastIndexOf("bin"));
			unitpath = unitpath + "units";
			deleteDir(new File(unitpath));
			loadUnitConfig(remoteUnitPath);
		}

		if (isDynamic) {
			registerWatcher();
		}
	}

	private void deleteRemoteConfig() {
		try {
			if (!hadDeleted) {
				if (zkClient.checkExists().forPath(remoteUnitPath) != null) {
					logger.debug("Deleting old path before pushing to remote"
							+ remoteUnitPath);
					zkClient.delete().deletingChildrenIfNeeded()
							.forPath(remoteUnitPath);
				}
				hadDeleted = true;
			}

		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	public void pushConfigToRemote(String unitPath) {
		deleteRemoteConfig();
		File path = new File(unitPath);
		if (path.isDirectory() && path.listFiles() != null) {
			for (File file : path.listFiles()) {
				if (file.isDirectory()) {
					pushConfigToRemote(file.getAbsolutePath());
				} else if (file.getAbsolutePath().endsWith("cfg")) {
					if (isExcludeUnitFile(file.getAbsolutePath())) {
						continue;
					}
					try {
						FileReader fr = new FileReader(file.getAbsolutePath());
						Properties properties = new Properties();
						properties.load(fr);// load()方法可通过字符流直接加载文件
						Enumeration enumeration = properties.propertyNames();
						String remotePath = file.getAbsolutePath();
						remotePath = remoteUnitPath
								+ remotePath.substring(remotePath
										.lastIndexOf("units") + 5);
						remotePath = remotePath.replace(File.separator, "/");

						if (!watchPathList.contains(remotePath)) {
							watchPathList.add(remotePath);
						}
						while (enumeration.hasMoreElements()) {
							String key = enumeration.nextElement().toString();
							String value = properties.getProperty(key);
							String propertyPath = remotePath.concat("/")
									.concat(key);

							if (zkClient.checkExists().forPath(propertyPath) == null) {
								logger.debug("creating path for " + path);
								zkClient.create().creatingParentsIfNeeded()
										.withMode(CreateMode.PERSISTENT)
										.forPath(propertyPath);
								zkClient.setData().forPath(propertyPath,
										value.getBytes(DEFAULT_ENCODE));
							} else {
								zkClient.setData().forPath(propertyPath,
										value.getBytes(DEFAULT_ENCODE));
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public void loadUnitConfig(String zkPath) {
		try {
			if (zkClient.checkExists().forPath(zkPath) != null) {
				if (zkPath.endsWith(".cfg")) {
					if (isExcludeUnitFile(zkPath)) {
						return;
					}
					Properties properties = new Properties();
					List<String> childPaths = zkClient.getChildren().forPath(
							zkPath);
					if (!watchPathList.contains(zkPath)) {
						watchPathList.add(zkPath);
					}
					for (String path : childPaths) {
						String childPath = zkPath + "/" + path;
						if (zkClient.checkExists().forPath(childPath) != null) {
							byte[] valBytes = zkClient.getData().forPath(
									childPath);
							if (valBytes != null) {
								String val = new String(valBytes,
										DEFAULT_ENCODE);
								properties.put(path.trim(), val.trim());
							}
						}
					}
					String unitpath = this.getClass().getResource("/")
							.getPath();
					unitpath = unitpath.substring(0,
							unitpath.lastIndexOf("bin"));
					unitpath = unitpath
							+ "units"
							+ File.separator
							+ zkPath.substring(remoteUnitPath.length() + 1,
									zkPath.lastIndexOf("/"));
					String filename = zkPath
							.substring(zkPath.lastIndexOf("/") + 1);

					UnitFile unitFile = new UnitFile(unitpath, filename);
					unitFile.write(properties);
				} else {
					if (!watchPathList.contains(zkPath)) {
						watchPathList.add(zkPath);
					}
					List<String> childPaths = zkClient.getChildren().forPath(
							zkPath);
					for (String path : childPaths) {
						String childPath = zkPath + "/" + path;
						loadUnitConfig(childPath);
					}
				}
			}
		} catch (Exception e) {
			logger.error("localConfig init error:{}", e);
		}
	}

	private void registerWatcher() {

		PathChildrenCacheListener listener = new PathChildrenCacheListener() {
			@Override
			public void childEvent(CuratorFramework client,
					PathChildrenCacheEvent event) throws Exception {
				if (System.currentTimeMillis() - createTime < ingoreUnitWatchInterval) {
					logger.debug("Will not check the event since it is just booted");
					return;
				}
				String zkPath = event.getData().getPath();
				String unitpath = this.getClass().getResource("/").getPath();
				unitpath = unitpath.substring(0, unitpath.lastIndexOf("bin"));

				byte[] dataBytes = event.getData().getData();
				String value = "";
				if (dataBytes != null && dataBytes.length > 0) {
					value = new String(dataBytes, DEFAULT_ENCODE);
				}
				if (event.getType() == null) {
					return;
				}

				switch (event.getType()) {
				case CHILD_ADDED:
					logger.debug("Child Added. path=" + zkPath + " value="
							+ value);
					if (zkPath.endsWith(".cfg")) {
						if (!watchPathList.contains(zkPath)) {
							watchPathList.add(zkPath);
							addToWatchList(this, zkPath);
						}

						loadUnitConfig(zkPath);
					} else {
						zkPath = zkPath.substring(0, zkPath.lastIndexOf("/"));
						if (zkPath.endsWith(".cfg")) {
							logger.debug("Ready to use zkPath to reload config "
									+ zkPath);
							loadUnitConfig(zkPath);
						} else {
							if (!watchPathList.contains(zkPath)) {
								watchPathList.add(zkPath);
								addToWatchList(this, zkPath);
							}
						}
					}
					break;
				case CHILD_REMOVED:
					logger.debug("Child Removed. path=" + zkPath + " value="
							+ value);
					if (zkPath.endsWith(".cfg")) {
						unitpath = unitpath
								+ "units"
								+ File.separator
								+ zkPath.substring(remoteUnitPath.length() + 1,
										zkPath.lastIndexOf("/"));
						String filename = zkPath.substring(zkPath
								.lastIndexOf("/") + 1);
						File file = new File(unitpath, filename);
						if (file.exists()) {
							logger.debug("local file EXSIT, will delete file:"
									+ file.getAbsolutePath());
							boolean ret = file.delete();
							logger.debug("Delete result is " + ret);

						}
					} else {
						zkPath = zkPath.substring(0, zkPath.lastIndexOf("/"));
						if (zkPath.endsWith(".cfg")) {
							logger.debug("Ready to use zkPath to reload config "
									+ zkPath);
							loadUnitConfig(zkPath);
						} else {
							unitpath = unitpath
									+ "units"
									+ File.separator
									+ zkPath.substring(remoteUnitPath.length() + 1);
							File file = new File(unitpath);
							if (file.exists()) {
								logger.debug("Deleting path " + unitpath);
								file.delete();
							}
						}
					}

					break;
				case CHILD_UPDATED:
					logger.debug("Child Updated.path=" + zkPath + " value="
							+ value);
					zkPath = zkPath.substring(0, zkPath.lastIndexOf("/"));
					if (zkPath.endsWith(".cfg")) {
						logger.debug("Ready to use zkPath to reload config "
								+ zkPath);
						loadUnitConfig(zkPath);
					}

					break;
				}
			}
		};

		for (String path : this.watchPathList) {
			addToWatchList(listener, path);
		}
	}

	private void addToWatchList(PathChildrenCacheListener listener, String path) {
		if (isExcludeUnitFile(path)) {
			return;
		}
		final PathChildrenCache pathChildrenCache = new PathChildrenCache(
				zkClient, path, true);
		pathChildrenCache.getListenable().addListener(listener);
		try {
			pathChildrenCache.start();
		} catch (Exception e) {
			logger.error("registerWatcher zookeeper path error", e);
		}
	}

	private void deleteDir(File file) {
		if (file.exists()) {// 判断文件是否存在
			if (file.isFile()) {// 判断是否是文件
				if (file.delete()) {
					logger.debug("Delete Succeed:" + file.getAbsolutePath());
				} else {
					logger.debug("Delete Failed:" + file.getAbsolutePath());
				}
			} else if (file.isDirectory()) {// 否则如果它是一个目录
				File[] files = file.listFiles();// 声明目录下所有的文件 files[];
				for (int i = 0; i < files.length; i++) {// 遍历目录下所有的文件
					this.deleteDir(files[i]);// 把每个文件用这个方法进行迭代
				}
				if (file.delete()) {
					logger.debug("Delete Succeed:" + file.getAbsolutePath());
				} else {
					logger.debug("Delete Failed:" + file.getAbsolutePath());
				}
			}
		} else {
			logger.debug("所删除的文件不存在" + file.getAbsolutePath());
		}
	}

	private boolean isExcludeUnitFile(String cfgFile) {
		if (excludeUnits == null || excludeUnits.length <= 0) {
			return false;
		}
		for (String excludeUnit : excludeUnits) {
			if (cfgFile.contains(excludeUnit)) {
				return true;
			}
		}
		return false;
	}
}
