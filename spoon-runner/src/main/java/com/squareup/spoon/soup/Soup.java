package com.squareup.spoon.soup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Paths;  
import org.atteo.xmlcombiner.XmlCombiner;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.squareup.spoon.SpoonRunner;
import com.squareup.spoon.SpoonUtils;
import com.squareup.spoon.SpoonRunner.FileConverter;
import com.squareup.spoon.SpoonRunner.NoSplitter;
import com.squareup.spoon.SpoonRunner.TestSizeConverter;

import static com.squareup.spoon.SpoonLogger.logDebug;
import static com.squareup.spoon.SpoonLogger.logInfo;

public class Soup {
	// TODO: it is better to split this class into 2 classes and one of them manage the division of test case
	// and the other communicate with the manager and return the cases to the device to run
	
  private static Soup soup;
  private static final String SOUP_FILE_PATH = "soup";
  private File srcDir;
  private File reportDir;
  private RandomAccessFile testsFile;
  private LinkedList<TestIdentifier> tests;
  private boolean debug;
  private FileLock locker;
  private FileChannel channel;
  private int serialsNum;
  private static final File TESTCASE_DIR = new File("testcase/");
  private float totalTime;
  private File testcaseFile;
  private boolean outputTime; // TODO: we can use this flag to determine whether to output times
  
  public static void cleanUpSoup() {
  	new File(SOUP_FILE_PATH).delete();
  	SpoonUtils.deletePath(TESTCASE_DIR, false);
  }
  
  private Soup(boolean debug, File srcDir, File testcaseFile, File reportDir, File workDir, int serialsNum) {
    this.srcDir = srcDir;
    this.reportDir = reportDir;
    this.debug = debug;
    this.tests = new LinkedList();
    this.serialsNum = serialsNum;
    if (workDir != null) {
      logDebug(debug, "work dir: " + workDir.getAbsolutePath());
    }
    this.totalTime = 0;
    this.testcaseFile = testcaseFile;
    this.outputTime = false;
    //this.soupFilePath = new File(workDir, this.SOUP_FILE_PATH).getAbsolutePath();
  }
  
  private void cookSoup() {
    prepareSoup();
    
    if (testsFile != null) {
    	channel = testsFile.getChannel();
    	
    	try {
				locker = blockRequestLock();

				if (testsFile.length() <= 0) {
		      scanSrcDir();
		      scanTestcaseFile();
		      scanReportDir();

		      Collections.sort(this.tests, new TestComparator());

		      logAllTests();
		      splitTests();
				}
	      
			} catch (IOException e) {
				logDebug(debug, e.getMessage(), e);
				
			} finally {
				try {
					if (locker != null) {
						locker.release();
					}
				} catch (IOException e) {
					logDebug(debug, e.getMessage(), e);
				}
			}
    }
  }
  
  private FileLock blockRequestLock() throws IOException {
  	for (int i = 0; i < 100; i++) {
    	try {
    		FileLock locker = channel.lock();
    		logDebug(debug, "request done");
    		return locker;
    	} catch(OverlappingFileLockException  e) {
    		logDebug(debug, "Waiting others releasing the lock");
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    	
    	try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
  	}
  	return channel.lock();
  }
  
  private boolean testcaseTimeAlmostUnkown() {
  	float avgTime = totalTime / tests.size();
  	return avgTime <= 0.2;
  }
  
  private void splitTests() {
  	int bucketNum = serialsNum;
  	if (testcaseTimeAlmostUnkown() && serialsNum > 1) {
  		// We don't known cost time of almost test cases. So it's better to allocate more bucket
  		bucketNum *= 2;
  	}
  	
  	SoupBucket[] buckets = new SoupBucket[bucketNum];
  	for (int i = 0; i < buckets.length; i++) {
  		buckets[i] = new SoupBucket();
  	}
  	
  	float bucketAvgTime = totalTime / bucketNum;
  	int bucketIdx = 0;
  	float remainTime = totalTime;
  	
  	for (TestIdentifier ti : tests) {
  		SoupBucket bucket = buckets[bucketIdx];
  		bucket.feed(ti);
  		
  		if (bucket.getTotalTime() >= bucketAvgTime && bucketIdx < bucketNum - 1) {
  			/* logDebug(debug, "Bucket %d used time: %f(agvTime: %f)", bucketIdx, bucket.getTotalTime(), 
  					bucketAvgTime); */
  			
  			bucketIdx++;
  			remainTime -= bucket.getTotalTime();
  			bucketAvgTime = remainTime / (bucketNum - bucketIdx); 
  		}
  	}
  	
  	for (int i = 0; i < buckets.length; i++) {
  		SoupBucket bucket = buckets[i];
			File bucketFile = new File(TESTCASE_DIR, "testcase-" + i);
			
			logDebug(debug, "Bucket %d used time: %f. BucketFile: %s", i, bucket.getTotalTime(), 
					bucketFile.getAbsoluteFile());
			
			bucket.flushToFile(bucketFile);
			
  		try {
  			String s = bucketFile.getAbsolutePath() + "\n";
  			testsFile.writeBytes(s);
			} catch (IOException e) {
				logDebug(debug, e.getMessage(), e);
			}
  	}
  }
  
  private void prepareSoup() {
  	//for (int i = 0; i < 100; i++) {
    try {
  			testsFile = new RandomAccessFile(SOUP_FILE_PATH, "rw");
  	} catch (FileNotFoundException e) {
  		try {
  			new File(SOUP_FILE_PATH).createNewFile();
  			testsFile = new RandomAccessFile(SOUP_FILE_PATH, "rw");
  		} catch (IOException e1) {
  			logDebug(debug, "Create file : %s failed. Error: %s", SOUP_FILE_PATH, e1.getMessage());
  		}
  	}
    
    TESTCASE_DIR.mkdirs();
  	//}
    
  }

  private BufferedWriter createLogFile() {
    try {
      File logFile = new File("./tests.log");
      if (logFile.exists()) {
        logFile.delete();
      }
      logFile.createNewFile();

      BufferedWriter logFileWriter = new BufferedWriter(new FileWriter(logFile));
      return logFileWriter;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void logAllTests() {
  	BufferedWriter logFileWriter = createLogFile();
    try {
      for (TestIdentifier t : tests) {
        logFileWriter.write("Test: " + t + "\n");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    closeLogFile(logFileWriter);
  }

  private void scanSrcFile(File file) {
    String methodPattern = "public\\s+void\\s+(test.*)\\s*\\(";
    String packagePattern = "package\\s+(.*);";
    String classPattern = "public\\s+class\\s+(\\S*)[\\s|\\{]+";

    Pattern mp = Pattern.compile(methodPattern);
    Pattern pp = Pattern.compile(packagePattern);
    Pattern cp = Pattern.compile(classPattern);

    try {
      FileInputStream fis = new FileInputStream(file);
      InputStreamReader isr = new InputStreamReader(fis);
      BufferedReader br = new BufferedReader(isr);
      String line;
      String fullClassName = "";

      while ((line = br.readLine()) != null) {
        Matcher matcher = mp.matcher(line);
        if (matcher.find()) {
          // logDebug(debug, m.group(0));
          String methodName = matcher.group(1);
          TestIdentifier ti = new TestIdentifier(fullClassName, methodName);
          logDebug(debug, "Adding: " + ti);
          tests.add(ti);
          totalTime += ti.getUsedTime();
        } else {
          matcher = pp.matcher(line);
          if (matcher.find()) {
            fullClassName = matcher.group(1);
          } else {
            matcher = cp.matcher(line);
            if (matcher.find()) {
              fullClassName += "." + matcher.group(1);
            }
          }
        }
      }

      br.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  private void scanSrcDir() {
    if (srcDir != null) {
      logInfo("Scanning src dir: " + srcDir.getName());
      for (File file : SpoonUtils.listFiles(srcDir)) {
        logDebug(debug, "For file " + file.getName());
        if (file.getName().endsWith(".java")) {
          //logDebug(debug, file.getName() + ": Scanning");
          scanSrcFile(file);
          logDebug(debug, file.getName() + ": Scanned");
        } else {
          logDebug(debug, file.getName() + ": Skipped");
        }
      }
    }
  }
  
  private void scanTestcaseFile() {
  	if (testcaseFile != null) {
  		 try {
  	      FileInputStream fis = new FileInputStream(testcaseFile);
  	      InputStreamReader isr = new InputStreamReader(fis);
  	      BufferedReader br = new BufferedReader(isr);
  	      String line;

  	      while ((line = br.readLine()) != null) {
  	        String[] classMethod = line.split("#");
  	        if (classMethod.length >= 2) {
  	        	TestIdentifier ti = new TestIdentifier(classMethod[0], classMethod[1]);
  	        	
  	        	if (classMethod.length == 3) {
  	        		try {
    	        		ti.setUsedTime(Float.parseFloat(classMethod[2]));
  	        		} catch (NumberFormatException e) {
  	        			logDebug(debug, e.getMessage(), e);
  	        		}
  	        	}
  	        	logDebug(debug, "Adding: " + ti);
  	          tests.add(ti);
  	          totalTime += ti.getUsedTime();
  	        }
  	      }

  	      br.close();
  	    } catch (IOException e) {
  	      e.printStackTrace();
  	    }
  	}
  }

  private void scanReportFile(File file) {
    //String pattern = "testcase\\s+name=\"(\\S+)\"\\s+classname=\"(\\S+)\"\\s+time=\"(\\S+)\"";
  	//TODO: classname and name may not be in order of `name=... classname=...`
    String pattern = "testcase\\s+classname=\"(\\S+)\"\\s+name=\"(\\S+)\"\\s+time=\"(\\S+)\"";
    Pattern p = Pattern.compile(pattern);

    try {
      FileInputStream fis = new FileInputStream(file);
      InputStreamReader isr = new InputStreamReader(fis);
      BufferedReader br = new BufferedReader(isr);
      String line;

      while ((line = br.readLine()) != null) {
        Matcher matcher = p.matcher(line);
        if (matcher.find()) {
          TestIdentifier ti = new TestIdentifier(matcher.group(1), matcher.group(2));
          float usedTime = Float.parseFloat(matcher.group(3));
          //logDebug(debug, "Found test: " + ti);
          for (TestIdentifier t : tests) {
            // logDebug(debug, t + " === " + ti);
            if (t.equals(ti)) {
              logDebug(debug, "setting " + t + " used time to " + usedTime);
              totalTime = totalTime - t.getUsedTime() + usedTime;
              t.setUsedTime(usedTime);
            }
          }
        }
      }

      br.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void scanReportDir() {
    if (reportDir != null) {
      logInfo("Scanning report dir: " + reportDir.getName());
      for (File file : SpoonUtils.listFiles(reportDir)) {
        logDebug(debug, "For file " + file.getName());
        if (file.getName().endsWith(".xml")) {
          scanReportFile(file);
          logDebug(debug, file.getName() + ": Scanned");
        } else {
          logDebug(debug, file.getName() + ": Skipped");
        }
      }
    }
  }

  public static Soup getInstance(boolean debug, File srcDir, File testcaseFile, File reportDir, File workDir, int serialsNum) {
    if (soup == null) {
      synchronized (Soup.class) {
        if (soup == null) {
        	soup = new Soup(debug, srcDir, testcaseFile, reportDir, workDir, serialsNum);
        	soup.cookSoup();
        }
      }
    }
    return soup;
  }

  public String takeSpoon() {
    String result = null;
    
    try {
			locker = blockRequestLock();
			
			testsFile.seek(0);
			String firstLine = testsFile.readLine();
			
			if (firstLine != null) {
        result = firstLine;
        // line: Index#ClassName#MethodName#Time
        /*String[] tokens = firstLine.split("#");
        result[0] = tokens[0];
        result[1] = tokens[1];*/
        logDebug(debug, "%s has been taken", firstLine);

  			StringBuffer sb = new StringBuffer();
  			String line = "";
  			while((line = testsFile.readLine()) != null){
  				line += "\n";
          sb.append(line); 
  			} 
  			//logDebug(true, sb.toString());
  			testsFile.getChannel().truncate(0);
  			testsFile.writeBytes(sb.toString());
			}
			
			
		} catch (IOException e) {
			e.printStackTrace();
			
		} finally {
			if (locker != null) {
				try {
					locker.release();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
    
    return result;
  }

  public void closeLogFile(BufferedWriter logFileWriter) {
    try {
      logFileWriter.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private static void takingSpoon() {
    File srcDir = new File("./filemanager");
    File reportDir = new File("./");
    File workDir = new File("./");
    
  	Soup soup = Soup.getInstance(true, srcDir, null, new File("report"), workDir, 2);
    String result;

    System.out.println("=== Start taking ===");
    int count = 0;
    while ((result = soup.takeSpoon()) != null) {
    	count++;
      logInfo(Thread.currentThread().getName() + ": (count:" + count + ") " + result);
      try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    }
  } 
  
  private static File cleanFile(String path) {
    if (path == null) {
      return null;
    }
    return new File(path);
  }
  
  static class CommandLineArgs {
    @Parameter(names = { "--src-dir" }, description = "Source file path",
            converter = FileConverter.class, required=true) //
    public File srcDir = cleanFile(null);

    @Parameter(names = { "--report-dir" }, description = "Report file path",
            converter = FileConverter.class) //
    public File reportDir = cleanFile(null);

    @Parameter(names = "--device-num", required = true,
        description = "Number of the device to use (May be used multiple times)")
    private int serialNum = 0;

    @Parameter(names = { "-h", "--help" }, description = "Command help", help = true, hidden = true)
    public boolean help;
  }

  public static void main(String[] args) {
    CommandLineArgs parsedArgs = new CommandLineArgs();
    JCommander jc = new JCommander(parsedArgs);

    try {
      jc.parse(args);
    } catch (ParameterException e) {
      StringBuilder out = new StringBuilder(e.getLocalizedMessage()).append("\n\n");
      jc.usage(out);
      System.err.println(out.toString());
      System.exit(1);
      return;
    }
    if (parsedArgs.help) {
      jc.usage();
      return;
    }
  	
    //takingSpoon();
  	cleanUpSoup();
  	Soup.getInstance(true, parsedArgs.srcDir, null, parsedArgs.reportDir, null, parsedArgs.serialNum);
  	/*
    
    Thread thread1 = new Thread(new Runnable() {
    	public void run() {
    		takingSpoon();
    	}
    });
    
    Thread thread2 = new Thread(new Runnable() {
    	public void run() {
    		takingSpoon();
    	}
    });
    
    thread1.start();
    thread2.start();
    try {
    	thread1.join();
    	thread2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		*/
    
    /*
    try {
      // soup.closeLogFile();
      // create combiner
      XmlCombiner combiner = new XmlCombiner("name");

      // combine files
      try {

        combiner.combine(Paths.get("1.xml"));
      } catch (Exception e) {
      	
      } 
      combiner.combine(Paths.get("2.xml"));

      // store the result
      combiner.buildDocument(Paths.get("1.xml"));
    } catch (Exception e) {
    	e.printStackTrace();
    }
    */
    System.out.println("=== Finish ===");
  }
}