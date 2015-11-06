package com.panda.config;

import java.util.Properties;

/**
 * @author Jason
 *
 */
public interface IPropertySupport {
    public Properties loadAll();
    public void loadConfig();
    public void registerWatcher();
    public String get(String key);
}
