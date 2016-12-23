package com.squareup.spoon;

import static com.android.ddmlib.SyncService.getNullProgressMonitor;
import static com.squareup.spoon.SpoonLogger.logDebug;
import static com.squareup.spoon.SpoonLogger.logError;
import static com.squareup.spoon.SpoonUtils.obtainRealDevice;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;

public class CovFileTestRunListener implements ITestRunListener {
	// listener that combine the coverage file
	
	private SpoonCoverageMerger merger;
	private IDevice device;
	private File coverageDir; 
	private boolean debug;
	private String remotePath;
	private File coverageFile;
	private File tmpCovFile;
	private String cppCovMobilePath;
	private String gcnoPath;
	private String cppCovDstPath;
	private FileFilter gcnoFileFilter;
	
	public CovFileTestRunListener(IDevice device, File coverageDir, final boolean debug, String cppCovMobilePath,
			String gcnoPath, String cppCovDstPath) {
		this.device = device;
		try {
      this.remotePath = SpoonUtils.getExternalStoragePath(device, SpoonDeviceRunner.COVERAGE_FILE);
    } catch (Exception exception) {
    	exception.printStackTrace();
    }

		this.debug = debug;
		this.cppCovMobilePath = cppCovMobilePath;
		this.gcnoPath = gcnoPath;
		this.cppCovDstPath = cppCovDstPath;
		this.gcnoFileFilter = new FileFilter() {
			@Override
			public boolean accept(File file) {
				boolean result = file.getName().endsWith(".gcno") || file.isDirectory();
				//logDebug(debug, "For file: %s, accept?: %b", file.getName(), result);
				return result;
			}
		};
		
		this.coverageDir = coverageDir;
    coverageDir.mkdirs();
		this.coverageFile = new File(coverageDir, SpoonDeviceRunner.COVERAGE_FILE);
		
		this.merger = new SpoonCoverageMerger(this.coverageFile);
		
	}
	
	private void combineCovFiles() {
		pullCoverageFile();
		try {
			logDebug(debug, "tmp cov file is: " + tmpCovFile.getAbsolutePath());
			merger.loadFile(tmpCovFile);
			tmpCovFile.delete();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
  private void adbPullFile(String remoteFile, String localDir) {
    try {
      device.getSyncService()
          .pullFile(remoteFile, localDir, getNullProgressMonitor());
    } catch (Exception e) {
      logDebug(debug, e.getMessage(), e);
    }
  }
	
  private void pullCoverageFile() {
  	if (remotePath != null) {
      try {
				tmpCovFile = File.createTempFile("coverage.ec-", "", coverageDir);
	      adbPullFile(remotePath, tmpCovFile.getAbsolutePath());
			} catch (IOException e) {
				logError(e.getMessage(), e);
			}
  	}
  }
  
  private boolean pathAvailable(String path) {
  	return path != null && path.length() > 0;
  }
  
  private void adbPull(FileEntry remoteDirName, String localDirName) {
    try {
      device.getSyncService().pull(new FileEntry[]{remoteDirName}, localDirName,
          getNullProgressMonitor());
    } catch (Exception e) {
      logDebug(debug, e.getMessage(), e);
    }
  }
  
  private void pullCppCovFiles() {
  	if (pathAvailable(cppCovMobilePath) && pathAvailable(gcnoPath) && pathAvailable(cppCovDstPath)) {
  		File dstTmpDir = new File(cppCovDstPath, SpoonUtils.getRandomCharOrNumber(6));
  		logDebug(debug, "Pull cpp coverage files(%s) to %s", cppCovMobilePath, dstTmpDir.getAbsolutePath());
  		
  		dstTmpDir.mkdirs();
  		try {
				Thread.sleep(1000); // todo: how many seconds we should wait for cpp coverage ready
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
  		
  		adbPull(SpoonUtils.obtainDirectoryFileEntry(cppCovMobilePath), dstTmpDir.getAbsolutePath());
  		
  		try {
  			logDebug(debug, "Copy %s to %s", gcnoPath, dstTmpDir.getAbsoluteFile());
  			FileUtils.copyDirectory(new File(gcnoPath), dstTmpDir, gcnoFileFilter);
  		} catch (IOException e) {
  			logError(e.getMessage(), e);
  		}
  	}
  }

	@Override
	public void testAssumptionFailure(TestIdentifier arg0, String arg1) {

	}

	@Override
	public void testEnded(TestIdentifier arg0, Map<String, String> arg1) {
	}

	@Override
	public void testFailed(TestIdentifier arg0, String arg1) {

	}

	@Override
	public void testIgnored(TestIdentifier arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void testRunEnded(long arg0, Map<String, String> arg1) {
		combineCovFiles();
		pullCppCovFiles();
		
	}

	@Override
	public void testRunFailed(String arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void testRunStarted(String arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void testRunStopped(long arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void testStarted(TestIdentifier arg0) {
		// TODO Auto-generated method stub
		
	}

	public static void main(String[] args) {
		AndroidDebugBridge adb = SpoonUtils.initAdb(new File("D:\\Freedom\\AndroidSDK"), 10000);
		IDevice device = obtainRealDevice(adb, "emulator-5554");
		CovFileTestRunListener l = new CovFileTestRunListener(device, new File("coverage"), true, 
				"/storage/sdcard/downloader_cov/FormalProjects/downloadertest/downloader/android/downloader/obj/local/x86/objs/",
				"G:\\FormalProjects\\downloadertest\\downloader\\android\\downloader\\obj\\local\\x86\\",
				"G:\\lcov\\objs");
		l.testEnded(null, null);
	}
}
