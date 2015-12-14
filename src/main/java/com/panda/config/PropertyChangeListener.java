package com.panda.config;

import java.util.Properties;

/**
 * 
 * 当配置发生变更时调用
 * @author Jason
 *
 */
public interface PropertyChangeListener {

	public void onPropertyChanged(String configFile,Properties properties);
}
