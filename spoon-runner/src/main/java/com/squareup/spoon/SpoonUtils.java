package com.squareup.spoon;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.madgag.gif.fmsware.AnimatedGifEncoder;


import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.commons.io.FileUtils;

import static com.android.ddmlib.FileListingService.FileEntry;
import static com.android.ddmlib.FileListingService.TYPE_DIRECTORY;
import static com.squareup.spoon.SpoonLogger.logInfo;

/** Utilities for executing instrumentation tests on devices. */
public final class SpoonUtils {
  private static final Pattern SERIAL_VALIDATION = Pattern.compile("[^a-zA-Z0-9_-]");
  static final Gson GSON = new GsonBuilder() //
      .registerTypeAdapter(File.class, new TypeAdapter<File>() {
        @Override public void write(JsonWriter jsonWriter, File file) throws IOException {
          if (file == null) {
            jsonWriter.nullValue();
          } else {
            jsonWriter.value(file.getAbsolutePath());
          }
        }

        @Override public File read(JsonReader jsonReader) throws IOException {
          return new File(jsonReader.nextString());
        }
      }) //
      .enableComplexMapKeySerialization() //
      .setPrettyPrinting() //
      .create();

  /** Fetch or create a real device that corresponds to a device model. */
  static IDevice obtainRealDevice(AndroidDebugBridge adb, String serial) {
    // Get an existing real device.
    for (IDevice adbDevice : adb.getDevices()) {
      if (adbDevice.getSerialNumber().equals(serial)) {
        return adbDevice;
      }
    }
    throw new IllegalArgumentException("Unknown device serial: " + serial);
  }

  static String sanitizeSerial(String serial) {
    return SERIAL_VALIDATION.matcher(serial).replaceAll("_");
  }

  /** Get a {@link FileEntry} for an arbitrary path. */
  static FileEntry obtainDirectoryFileEntry(String path) {
    try {
      FileEntry lastEntry = null;
      Constructor<FileEntry> c =
          FileEntry.class.getDeclaredConstructor(FileEntry.class, String.class, int.class,
              boolean.class);
      c.setAccessible(true);
      for (String part : path.split("/")) {
        lastEntry = c.newInstance(lastEntry, part, TYPE_DIRECTORY, lastEntry == null);
      }
      return lastEntry;
    } catch (NoSuchMethodException ignored) {
    } catch (InvocationTargetException ignored) {
    } catch (InstantiationException ignored) {
    } catch (IllegalAccessException ignored) {
    }
    return null;
  }

  /** Turn on debug logging in ddmlib classes. */
  static void setDdmlibInternalLoggingLevel() {
    DdmPreferences.setLogLevel("debug");
  }

  /** Find all device serials that are plugged in through ADB. */
  public static Set<String> findAllDevices(AndroidDebugBridge adb) {
    return findAllDevices(adb, null);
  }

  /**
   * Find all device serials that are plugged in through ADB.
   *
   * @param minApiLevel If <code>null</code>, all devices will be returned. Otherwise, only
   *                      those device serials that are greater than or equal to the provided
   *                      version will be returned.
   */
  public static Set<String> findAllDevices(AndroidDebugBridge adb, Integer minApiLevel) {
    Set<String> devices = new LinkedHashSet<String>();
    for (IDevice realDevice : adb.getDevices()) {
      if (minApiLevel == null) {
        devices.add(realDevice.getSerialNumber());
      } else {
        DeviceDetails deviceDetails = DeviceDetails.createForDevice(realDevice);
        int apiLevel = deviceDetails.getApiLevel();
        if (apiLevel == DeviceDetails.UNKNOWN_API_LEVEL || apiLevel >= minApiLevel) {
          devices.add(realDevice.getSerialNumber());
        }
      }
    }
    return devices;
  }

  /** Get an {@link com.android.ddmlib.AndroidDebugBridge} instance given an SDK path. */
  public static AndroidDebugBridge initAdb(File sdk, long timeOutMs) {
    AndroidDebugBridge.initIfNeeded(false);
    File adbPath = FileUtils.getFile(sdk, "platform-tools", "adb");
    AndroidDebugBridge adb = AndroidDebugBridge.createBridge(adbPath.getAbsolutePath(), false);
    waitForAdb(adb, timeOutMs);
    return adb;
  }
  
  public static String getExternalStoragePath(IDevice device, final String path) throws Exception {
    CollectingOutputReceiver pathNameOutputReceiver = new CollectingOutputReceiver();
    device.executeShellCommand("echo $EXTERNAL_STORAGE", pathNameOutputReceiver);
    return pathNameOutputReceiver.getOutput().trim() + "/" + path;
  }

  static void createAnimatedGif(List<File> testScreenshots, File animatedGif) throws IOException {
    AnimatedGifEncoder encoder = new AnimatedGifEncoder();
    encoder.start(animatedGif.getAbsolutePath());
    encoder.setDelay(1500 /* 1.5 seconds */);
    encoder.setQuality(1 /* highest */);
    encoder.setRepeat(0 /* infinite */);
    encoder.setTransparent(Color.WHITE);

    int width = 0;
    int height = 0;
    for (File testScreenshot : testScreenshots) {
      BufferedImage bufferedImage = ImageIO.read(testScreenshot);
      width = Math.max(bufferedImage.getWidth(), width);
      height = Math.max(bufferedImage.getHeight(), height);
    }
    encoder.setSize(width, height);

    for (File testScreenshot : testScreenshots) {
      encoder.addFrame(ImageIO.read(testScreenshot));
    }

    encoder.finish();
  }

  private static void waitForAdb(AndroidDebugBridge adb, long timeOutMs) {
    long sleepTimeMs = TimeUnit.SECONDS.toMillis(1);
    while (!adb.hasInitialDeviceList() && timeOutMs > 0) {
      try {
        Thread.sleep(sleepTimeMs);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      timeOutMs -= sleepTimeMs;
    }
    if (timeOutMs <= 0 && !adb.hasInitialDeviceList()) {
      throw new RuntimeException("Timeout getting device list.", null);
    }
  }

  private SpoonUtils() {
    // No instances.
  }
  
  public static String getRandomCharOrNumber(int length) {
    String val = "";
    Random random = new Random();
    for (int i = 0; i < length; i++) {
        String charOrNum = random.nextInt(2) % 2 == 0 ? "char" : "num"; // 输出字母还是数字

        if ("char".equalsIgnoreCase(charOrNum)) { // 字符串
            int choice = random.nextInt(2) % 2 == 0 ? 65 : 97; // 取得大写字母还是小写字母
            val += (char) (choice + random.nextInt(26));
            // int choice = 97; // 指定字符串为小写字母
            val += (char) (choice + random.nextInt(26));
        } else if ("num".equalsIgnoreCase(charOrNum)) { // 数字
            val += String.valueOf(random.nextInt(10));
        }
    }
    return val;
  }
  
  public static void deletePath(File path, boolean inclusive) {
  	logInfo("deletPath:%s (inclusive:%b). ", path.getAbsolutePath(), inclusive);
    if (path.isDirectory()) {
      File[] children = path.listFiles();
      if (children != null) {
        for (File child : children) {
          deletePath(child, true);
        }
      }
    }
    if (inclusive) {
      path.delete();
    }
  }
}
