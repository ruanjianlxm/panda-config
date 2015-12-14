package com.panda.config;

import java.util.Properties;

/**
 * @author Jason
 *   配置文件的基类，不管是更新或者操作配置文件，都有以下方法，实现该类
 */
public interface IPropertySupport {
	/**
	 * 加载所有的配置项
	 * @return
	 */
    public Properties loadAll();
    /**
     * 加载配置
     */
    public void loadConfig();
    /**
     * 注册监听
     */
    public void registerWatcher();
    /**
     * 获得某个值
     * @param key
     * @return
     */
    public String get(String key);
}
