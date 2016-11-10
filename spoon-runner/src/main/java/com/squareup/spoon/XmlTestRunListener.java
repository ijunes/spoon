package com.squareup.spoon;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.atteo.xmlcombiner.XmlCombiner;
import org.xml.sax.SAXParseException;

import static com.squareup.spoon.SpoonLogger.logDebug;

/**
 * An {@link com.android.ddmlib.testrunner.XmlTestRunListener XmlTestRunListener} that points
 * directly to an output file.
 */
class XmlTestRunListener extends com.android.ddmlib.testrunner.XmlTestRunListener {
  private final File file;
  private File tmpFile;
  private boolean isMultipleTest;

  XmlTestRunListener(File file, boolean isMultipleTest) {
    if (file == null) {
      throw new IllegalArgumentException("File may not be null.");
    }
    this.file = file;
    this.isMultipleTest = isMultipleTest;
  }

  @Override protected File getResultFile(File reportDir) throws IOException {
    file.getParentFile().mkdirs();
    
    if (isMultipleTest) {
    	tmpFile = File.createTempFile(file.getName(), ".tmp", file.getParentFile());
    	return tmpFile;
    }
    
    return file;
  }
  
  @Override public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
  	super.testRunEnded(elapsedTime, runMetrics);
  	
  	if (isMultipleTest) {
  		/*
  		if (!file.exists()) {
  			try {
    			file.createNewFile();	
  			} catch (IOException e) {
  				e.printStackTrace();
  			}
  		}
  		*/
  		
			try {
				XmlCombiner combiner = new XmlCombiner("name");
				FileInputStream tmpFileIS = new FileInputStream(tmpFile);

	      // combine files
				try {
		      combiner.combine(new FileInputStream(file));
				} catch (Exception e) {
					logDebug(true, "The file: " + file.getName() + " has no content");
				}

	      combiner.combine(tmpFileIS);
	      // store the result
	      combiner.buildDocument(new FileOutputStream(file));
	      
	      //tmpFileIS.close();
	      tmpFile.delete();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
  	}
  }
}
