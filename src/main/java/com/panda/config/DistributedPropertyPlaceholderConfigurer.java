package com.panda.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.core.io.Resource;

/**
 * @author Jason
 *
 */
public class DistributedPropertyPlaceholderConfigurer extends
		PropertyPlaceholderConfigurer {
	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory
			.getLogger(DistributedPropertyPlaceholderConfigurer.class);
	private List<PropertyUnit> propertyUnitList = new ArrayList<PropertyUnit>();
	private PropertyUnitFactory propertyUnitFactory;
	private PropertyChangeListener propertyListener;
	
	/**
	 * Return a merged Properties instance containing both the loaded properties
	 * and properties set on this FactoryBean.
	 */
	
	@Override
	protected Properties mergeProperties() throws IOException {
		Properties result;
		result = super.mergeProperties();
		loadCentralProperties(result);
		return result;
	}

	@Override
	public void setLocation(Resource location) {
		PropertyUnit propertyUnit = propertyUnitFactory.createConfigBundle(location.getFilename(),propertyListener);
		propertyUnitList.add(propertyUnit);
		super.setLocation(location);
	}
	
	//TODO:jason:后面版本支持多个propertiesFile工作
	@Override
	public void setLocations(Resource[] locations) {
		for(Resource location: locations){
			PropertyUnit propertyUnit = propertyUnitFactory.createConfigBundle(location.getFilename(),propertyListener);
			propertyUnitList.add(propertyUnit);
		}
		super.setLocations(locations);
	}
	/**
	 * load central proxy
	 */
	protected void loadCentralProperties(Properties properties) {
		for(PropertyUnit propertyUnit: propertyUnitList){
			properties.putAll(propertyUnit.loadAll());
		}
	}
	
	
	public PropertyChangeListener getPropertyListener() {
		return propertyListener;
	}

	public void setPropertyListener(PropertyChangeListener propertyListener) {
		this.propertyListener = propertyListener;
	}
	
	public PropertyUnitFactory getPropertyUnitFactory() {
		return propertyUnitFactory;
	}

	public void setPropertyUnitFactory(PropertyUnitFactory propertyUnitFactory) {
		this.propertyUnitFactory = propertyUnitFactory;
	}

}
