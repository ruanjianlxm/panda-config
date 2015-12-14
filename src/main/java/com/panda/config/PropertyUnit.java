package com.panda.config;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 管理LocalConfig和RemoteConfig
 */
public class PropertyUnit {
	private static final Logger logger = LoggerFactory
			.getLogger(PropertyUnit.class);
	private LocalProperty localConfig;
	private RemoteProperty remoteConfig;
	private boolean pushToRemote = false;
	private boolean configUseRemote = false;
	private String[] excludeConfigs;
	ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
   /**
    * 配置单元，一个PropertyUnit代表一个配置文件
    * @param serverpath  服务的节点
    * @param configFile 配置文件路径
    * @param excludeConfig  要忽略的文件
    * @param zkClient   zookeeper连接的客户端
    * @param configUseRemote  是否使用远程节点
    * @param pushToRemote     本地配置是否推送到远程
    * @param isDynamic        是否动态监听本地配置文件
    * @param propertyListener 本地配置文件改动后的回调方法
    */
	public PropertyUnit(String serverpath, String configFile,String excludeConfig,
			CuratorFramework zkClient,boolean configUseRemote, boolean pushToRemote, boolean isDynamic,
			PropertyChangeListener propertyListener) {
		this.pushToRemote = pushToRemote;
		this.configUseRemote = configUseRemote;
		localConfig = new LocalProperty(configFile, isDynamic);
		if(StringUtils.isNotEmpty(excludeConfig)){
			excludeConfigs = excludeConfig.split(",");
			for(String config : excludeConfigs){
				if(configFile.contains(config)){
					logger.debug("Ignore the exclude config:" + excludeConfig);
					return;
				}
				
			}
		}
	
		remoteConfig = new RemoteProperty(serverpath, zkClient, configUseRemote,pushToRemote,
				isDynamic, new PropertyChangeListener() {
					@Override
					public void onPropertyChanged(String configFile,
							Properties properties) {
						logger.debug("Remote config changed,sync local config");
						localConfig.syncLocalConfig(properties);
					}
				});
		if (pushToRemote) {
			logger.debug("Push Local config " + configFile + " to remote!");
			remoteConfig.pushConfigToRemote(localConfig.loadAll());
		}
		if(isDynamic){
		logger.debug("Need to watch zk node chage for config " + configFile);
		service.schedule(new Runnable(){
			@Override
			public void run() {
				remoteConfig.registerWatcher();
			}
		}, 1000 * 60, TimeUnit.MILLISECONDS);
	}
	}
    /**
     * 加载所有的配置，会合并本地配置和远程配置
     * @return
     */
	public Properties loadAll() {
		logger.debug("Loading all properties");
		if (pushToRemote) {
			if(remoteConfig!=null){
				remoteConfig.loadConfig();
			}
		}
		Properties localProp = localConfig.loadAll();
		Properties merged = new Properties();
		if(remoteConfig!=null){
			Properties remoteProp = remoteConfig.loadAll();
			// load localConfig first,so that remoteConfig can override localConfig
			merged.putAll(localProp);
			merged.putAll(remoteProp);
		}
		else{
			merged.putAll(localProp);
		}

		if(configUseRemote&&!pushToRemote){
			localConfig.syncLocalConfig(merged);
		}

		return merged;
	}
}
