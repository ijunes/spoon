package com.squareup.spoon.soup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Paths;  
import org.atteo.xmlcombiner.XmlCombiner;
import org.xml.sax.SAXParseException;

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

  private File srcDir;
  private File reportDir;
  private LinkedList<TestIdentifier> tests;
  private boolean debug;

  private BufferedWriter logFileWriter;

  private Soup(File srcDir, File reportDir, boolean debug) {
    this.srcDir = srcDir;
    this.reportDir = reportDir;
    this.debug = debug;
    this.tests = new LinkedList();

    scanSrcDir();
    scanReportDir();

    Collections.sort(this.tests, new TestComparator());

    createLogFile();
    outputAllTests();
    closeLogFile();
  }

  private Soup(File srcDir, File reportDir) {
    this(srcDir, reportDir, true);
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
    String pattern = "testcase\\s+name=\"(\\S+)\"\\s+classname=\"(\\S+)\"\\s+time=\"(\\S+)\"";
    Pattern p = Pattern.compile(pattern);

    try {
      FileInputStream fis = new FileInputStream(file);
      InputStreamReader isr = new InputStreamReader(fis);
      BufferedReader br = new BufferedReader(isr);
      String line;

      while ((line = br.readLine()) != null) {
        Matcher matcher = p.matcher(line);
        if (matcher.find()) {
          TestIdentifier ti = new TestIdentifier(matcher.group(2), matcher.group(1));
          float usedTime = Float.parseFloat(matcher.group(3));

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

  public static Soup getInstance(File srcDir, File reportDir) {
    if (soup == null) {
      synchronized (Soup.class) {
        if (soup == null) {
          soup = new Soup(srcDir, reportDir);
        }
      }
    }
    return soup;
  }

  public String[] takeSpoon() {
    String[] result = null;
    synchronized (tests) {
      TestIdentifier test = tests.poll();
      if (test != null) {
        result = new String[2];
        result[0] = test.getClassName();
        result[1] = test.getTestName();
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

  public static void main(String[] args) {
    File srcDir = new File("./");
    File reportDir = new File("./");

    Soup soup = Soup.getInstance(srcDir, reportDir);
    String[] result;

    while ((result = soup.takeSpoon()) != null) {
      System.out.println(result[0] + "#" + result[1]);
    }
    
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
    System.out.println("=== Finish ===");
  }
}