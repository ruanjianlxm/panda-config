package com.panda.config;

import java.util.Properties;

/**
 * @author Jason
 *
 */
public interface PropertyChangeListener {

	public void onPropertyChanged(String configFile,Properties properties);
}
