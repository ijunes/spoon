package com.squareup.spoon;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.atteo.xmlcombiner.XmlCombiner;

import static com.squareup.spoon.SpoonLogger.logDebug;

/**
 * An {@link com.android.ddmlib.testrunner.XmlTestRunListener XmlTestRunListener} that points
 * directly to an output file.
 */
class XmlTestRunListener extends com.android.ddmlib.testrunner.XmlTestRunListener {
  private final File file;
  private File tmpFile;
  private boolean isMultipleTest;

	private XmlCombiner combiner;

  XmlTestRunListener(File file, boolean isMultipleTest) {
    if (file == null) {
      throw new IllegalArgumentException("File may not be null.");
    }
    this.file = file;
    this.isMultipleTest = isMultipleTest;
    
    try {
    	ArrayList<String> list = new ArrayList();
    	list.add("name");
    	list.add("classname");
			combiner = new XmlCombiner(list);
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
  }

  @Override protected File getResultFile(File reportDir) throws IOException {
    file.getParentFile().mkdirs();
    
    if (isMultipleTest) {
    	tmpFile = File.createTempFile(file.getName(), "", file.getParentFile());
    	return tmpFile;
    }
    
    return file;
  }
  
  @Override public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
  	super.testRunEnded(elapsedTime, runMetrics);
  	
  	if (isMultipleTest) {  		
			try {
	      combiner.combine(new FileInputStream(tmpFile));
	      // store the result
	      combiner.buildDocument(new FileOutputStream(file));
	      
	      tmpFile.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}
  	}
  }
}
