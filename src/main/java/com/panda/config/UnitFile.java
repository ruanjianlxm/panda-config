package com.panda.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Jason
 *   文件工具类，把properties写到文件当中
 */
public class UnitFile {

	public static void main(String[] args) {
		UnitFile unitFile = new UnitFile("c:/units/private/", "stcTcpConnector.cfg");
		Properties p = new Properties();
		p.put("name", "jasonyan");
		p.put("age", "32");
		unitFile.write(p);
	}

	private FileOutputStream fout;

	public void write(Properties property) {
		try {
			property.store(fout, "jason");
			fout.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public UnitFile(String path, String fileName) {
		try {
			File dir = new File(path);
			dir.mkdirs();
			File file = new File(dir,fileName);
			file.createNewFile();
			fout = new FileOutputStream(file);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public UnitFile(String fileName) {
		try {
			File file = new File(fileName);
			file.createNewFile();
			fout = new FileOutputStream(file);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
