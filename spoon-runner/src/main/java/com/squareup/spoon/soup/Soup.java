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

import static com.squareup.spoon.SpoonLogger.logDebug;
import static com.squareup.spoon.SpoonLogger.logInfo;

class FileHelper {
  static Collection<File> listFiles(File root) {
    List<File> files = new ArrayList<File>();
    listFiles(files, root);
    return files;
  }

  static void listFiles(List<File> files, File dir) {
    File[] listFiles = dir.listFiles();
    if (listFiles != null) {
      for (File f : listFiles) {
        if (f.isFile()) {
          files.add(f);
        } else if (f.isDirectory()) {
          listFiles(files, f);
        }
      }
    }
  }
}

public class Soup {
  private static Soup soup;
  private String testcasePath = "soup";
  private File srcDir;
  private File reportDir;
  private RandomAccessFile testsFile;
  private LinkedList<TestIdentifier> tests;
  private boolean debug;
  private FileLock locker;
  private FileChannel channel;
  private BufferedWriter logFileWriter;
  private String cppCovMobilePath;
  private String gcnoPath;
  private String cppCovDstPath;

  private Soup(boolean debug, File srcDir, File reportDir, File workDir, String cppCovMobilePath,
  		String gcnoPath, String cppCovDstPath) {
    this.srcDir = srcDir;
    this.reportDir = reportDir;
    this.debug = debug;
    this.tests = new LinkedList();
    this.cppCovMobilePath = cppCovMobilePath;
    this.gcnoPath = gcnoPath;
    this.cppCovDstPath = cppCovDstPath;
    //this.testcasePath = new File(workDir.getAbsolutePath(), this.testcasePath).getAbsolutePath();
    
    tryCreateTestsFile();
    
    if (testsFile != null) {
    	channel = testsFile.getChannel();
    	
    	try {
				locker = blockRequestLock();

				if (testsFile.length() <= 0) {
		      scanSrcDir();
		      scanReportDir();

		      Collections.sort(this.tests, new TestComparator());

		      createLogFile();
		      outputAllTests();
		      closeLogFile();

		      writeTests();
				}
	      
			} catch (IOException e) {
				logDebug(debug, e.getMessage(), e);
				
			} finally {
				try {
					if (locker != null) {
						locker.release();
					}
					/*if (channel.isOpen()) {
						channel.close();
					}*/
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
  
  private void writeTests() {
  	for (TestIdentifier ti : tests) {
  		try {
  			String s = ti.toString() + "\n";
				//channel.write(ByteBuffer.wrap(s.getBytes()));
  			testsFile.writeBytes(s);
			} catch (IOException e) {
				logDebug(debug, e.getMessage(), e);
			}
  	}
  }
  
  private void tryCreateTestsFile() {
  	for (int i = 0; i < 100; i++) {
    	try {
  			testsFile = new RandomAccessFile(testcasePath, "rw");
  		} catch (FileNotFoundException e) {
  			try {
  				new File(testcasePath).createNewFile();
  				testsFile = new RandomAccessFile(testcasePath, "rw");
  			} catch (IOException e1) {
  				logDebug(debug, "Create file : %s failed. Error: %s", testcasePath, e1.getMessage());
  			}
  		}
    	
    	if (testsFile != null) {
    		return ;
    	}
    	
    	try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logDebug(debug, "sleep is interrupted");
			}
  	}
    
  }

  private void createLogFile() {
    try {
      File logFile = new File("./tests.log");
      if (logFile.exists()) {
        logFile.delete();
      }
      logFile.createNewFile();

      this.logFileWriter = new BufferedWriter(new FileWriter(logFile));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void outputAllTests() {
    try {
      for (TestIdentifier t : tests) {
        logFileWriter.write("Test: " + t + "\n");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
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
      for (File file : FileHelper.listFiles(srcDir)) {
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

  private void scanReportFile(File file) {
    //String pattern = "testcase\\s+name=\"(\\S+)\"\\s+classname=\"(\\S+)\"\\s+time=\"(\\S+)\"";
  	//TODO: classname and name may not be in order
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
          logDebug(debug, "Found test: " + ti);
          for (TestIdentifier t : tests) {
            // logDebug(debug, t + " === " + ti);
            if (t.equals(ti)) {
              logDebug(debug, "setting " + t + " used time to " + usedTime);
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
      for (File file : FileHelper.listFiles(reportDir)) {
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

  public static Soup getInstance(boolean debug, File srcDir, File reportDir, File workDir, String cppCovMobilePath,
  		String gcnoPath, String cppCovDstPath) {
    if (soup == null) {
      synchronized (Soup.class) {
        if (soup == null) {
        	soup = new Soup(debug, srcDir, reportDir, workDir, cppCovMobilePath, gcnoPath, cppCovDstPath);
        }
      }
    }
    return soup;
  }

  public String[] takeSpoon() {
    String[] result = null;
    
    try {
			locker = blockRequestLock();
			
			testsFile.seek(0);
			String firstLine = testsFile.readLine();
			
			if (firstLine != null) {
        result = new String[2];
        String[] tokens = firstLine.split("#");
        result[0] = tokens[0];
        result[1] = tokens[1];
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

  public void closeLogFile() {
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
    
  	Soup soup = Soup.getInstance(true, srcDir, null, workDir, "", "", "");
    String[] result;

    System.out.println("=== Start taking ===");
    int count = 0;
    while ((result = soup.takeSpoon()) != null) {
    	count++;
      logInfo(Thread.currentThread().getName() + ": (count:" + count + ")" + result[0] + "#" + result[1]);
      try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    }
  }

  public static void main(String[] args) {
    //takingSpoon();
    
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