/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    JRILoader.java
 *    Copyright (C) 2012-2018 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.sun.jna.platform.win32.Advapi32Util;
import static com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;
import static com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER;

/**
 * Class that makes sure that key JRI classes and the native library are all
 * loaded by the root class loader.
 * 
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @author Eibe Frank
 * @version $Revision$
 */
public class JRILoader {

  private final static String RSESSION_IMPL = "weka.core.RSessionImpl";
  final static String JRI_NATIVE_LOADER = "weka.core.JRINativeLoader";

  private static boolean s_isLoaded;
  private static Object s_api;

  private static String s_rHome;

  private static boolean s_debug;

  /**
   * Get environment variable. Try Java's cache of environment first. If that fails (i.e. value is empty
   * string or null, retrieve from actual current process environment using JNA if possible.
   */
  public static String getenv(String name) {

    String value = System.getenv(name);
    if (value == null || value.length() == 0) {
      value = SetEnvironmentVariables.INSTANCE.getenv(name);
      if (s_debug) {
        if (value != null) {
          System.err.println("Found variable " + name + " with value " + value + " in process environment.");
        } else {
          System.err.println("Did not find variable " + name + " in Java cache or process environment.");
        }
      }
    } else {
      if (s_debug) {
        System.err.println("Found variable " + name + " with value " + value + " in Java cache of environment.");
      }
    }
    return value;
  }

  /**
   * Checks if environment variable R_HOME is available, first in Java cache of process environment and then
   * in actual process environment. Tries to guess value of R_HOME if environment variable or r.home property
   * do not have an appropriate value.
   *
   * Priority: 1) R_HOME in cached environment; 2) R_HOME in process environment; 3) r.home property.
   *
   * Sets value of R_HOME in the process environment. Also sets Java system property r.home to whichever value
   * is left in environment variable R_HOME.
   *
   * Does the same for R_LIBS_USER and r.libs.user respectively.
   *
   * Sets the JAVA_HOME environment variable to the value in the java.home property if
   * JAVA_HOME is not set to a seemingly appropriate value already.
   *
   * Tries to install rJava and JavaGD if they are not installed already.
   *
   * If the class java.gui.GUIChooser occurs in the method call stack, the Java property weka.started.via.GUIChooser
   * is set to the value true. Otherwise, the property is set to value false.
   *
   * Finally, this method sets the system property jri.ignore.ule to value "yes", so that Rengine in the JRI
   * library will not terminate the JVM.
   */
  public static boolean checkRHome() {

    if (s_rHome == null) {

      // Set the system property jri.ignore.ule to value "yes", so that  Rengine in the JRI
      // library will not terminate the JVM.
      System.setProperty("jri.ignore.ule", "yes");

      // Check if the user is using a GUI environment
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      System.setProperty("weka.started.via.GUIChooser", "false");
      for (StackTraceElement s : stack) {
        if (s.getClassName().equals("weka.gui.GUIChooser")) {
          System.setProperty("weka.started.via.GUIChooser", "true");
          break;
        }
      }

      // Set JAVA_HOME for rJava and JavaGD if it is not set already
      String osType = System.getProperty("os.name");
      String javaExecutableName = "java";
      if ((osType != null) && (osType.contains("Windows"))) {
        javaExecutableName = "java.exe";
      }
      String javaHome = getenv("JAVA_HOME");
      if ((javaHome != null) && (!(new File(javaHome)).exists() ||
              !(new File(javaHome + File.separator + "bin" + File.separator + javaExecutableName).exists()))) {
        if (s_debug) {
          System.err.println("JAVA_HOME " + javaHome + " does not appear to be a valid home of Java.");
        }
        javaHome = null;
      }
      if (javaHome == null) {
        javaHome = System.getProperty("java.home");
        if (s_debug) {
          System.err.println("Setting JAVA_HOME to " + javaHome);
        }
        if (SetEnvironmentVariables.INSTANCE.setenv("JAVA_HOME", javaHome, 0) != 0) {
          System.err.println("Failed to set JAVA_HOME.");
          s_rHome = null;
          return false;
        }
      }

      // First deal with R_HOME and r.home
      s_rHome = getenv("R_HOME");
      String rExeString = "R";
      String rScriptExeString = "Rscript";
      if ((osType != null) && (osType.contains("Windows"))) {
        rExeString = "R.exe";
        rScriptExeString = "Rscript.exe";
      }
      if (s_rHome != null && (!(new File(s_rHome)).exists() ||
              !(new File(s_rHome + File.separator + "bin" + File.separator + rExeString).exists()))) {
        System.err.println("R_HOME " + s_rHome + " does not appear to be a valid home of R.");
        s_rHome = null;
        return false; // If R_HOME is set incorrectly in cached environment, we cannot fix things for some reason.
      }
      if (s_rHome == null) {
        s_rHome = System.getProperty("r.home");
        if (s_rHome == null) {
          if (osType != null) {
            if (osType.contains("Mac OS X")) {
              s_rHome = "/Library/Frameworks/R.framework/Resources"; // This is the guess for macOS
            } else if (osType.contains("Windows")) {
              try {
                if (System.getenv("ProgramFiles(x86)") != null) {
                  s_rHome = Advapi32Util.
                          registryGetStringValue(HKEY_LOCAL_MACHINE, "Software\\R-core\\R64", "InstallPath");
                } else {
                  s_rHome = Advapi32Util.
                          registryGetStringValue(HKEY_LOCAL_MACHINE, "Software\\Wow6432Node\\R-core\\R", "InstallPath");
                }
              } catch (Exception ex) {
                if (s_debug) {
                  System.err.println("Could not find system-wide install location for R in registry.");
                }
                try {
                  if (System.getenv("ProgramFiles(x86)") != null) {
                    s_rHome = Advapi32Util.
                            registryGetStringValue(HKEY_CURRENT_USER, "Software\\R-core\\R64", "InstallPath");
                  } else {
                    s_rHome = Advapi32Util.
                            registryGetStringValue(HKEY_CURRENT_USER, "Software\\Wow6432Node\\R-core\\R", "InstallPath");
                  }
                } catch (Exception ex2) {
                  System.err.println("Could not find user-specific install location for R in registry either.");
                  return false;
                }
              }
            } else { // Assuming linux (or a Unix-derivative that has the same default install location for R).
              s_rHome = "/usr/lib/R";
            }
          } else {
            System.err.println("The os.name property is not available. Cannot guess R_HOME.");
            s_rHome = null;
            return false;
          }
        }
        if (s_debug) {
          System.err.println("Setting R_HOME to " + s_rHome);
        }
        if (SetEnvironmentVariables.INSTANCE.setenv("R_HOME", s_rHome, 0) != 0) {
          System.err.println("Failed to set R_HOME.");
          s_rHome = null;
          return false;
        }
      }
      if (!(new File(s_rHome)).exists() ||
              !(new File(s_rHome + File.separator + "bin" + File.separator + rExeString).exists())) {
        System.err.println("R_HOME " + s_rHome + " does not appear be a valid value for the home of R.");
        s_rHome = null;
        return false;
      }
      System.setProperty("r.home", s_rHome);

      // On Windows, check if appropriate folder in R_HOME is in path and add it if it isn't
      if ((osType != null) && (osType.contains("Windows"))) {
        try {
          Process p = Runtime.getRuntime().exec("Rterm"); // Use this because it is in x64/i386, not just bin
        } catch (Exception ex) {

          // Could not run process, so let's add the x64/i386 folder to the path
          String PATH = getenv("PATH");
          if (s_debug) {
            System.err.println("Adding appropriate folder in R's home to PATH.");
          }
          String subFolderName = "i386";
          if (System.getenv("ProgramFiles(x86)") != null) {
            subFolderName = "x64";
          }
          if (SetEnvironmentVariables.INSTANCE.setenv("PATH", s_rHome + File.separator +
                  "bin" + File.separator + subFolderName +
                  File.pathSeparator + PATH, 0) != 0) {
            System.err.println("Could not add " + subFolderName + " folder in R's home to PATH.");
            s_rHome = null;
            return false;
          }
          if (s_debug) {
            System.err.println(SetEnvironmentVariables.INSTANCE.getenv("PATH")); // Debugging output.
          }
        }
      }

      // Now deal with R_LIBS_USER and r.libs.user
      String rLibsUser = getenv("R_LIBS_USER");
      if (rLibsUser == null) { // Try to get library location if user has not set R_LIBS_USER
        rLibsUser = System.getProperty("r.libs.user");
        if (rLibsUser == null) {
          try {
            String cmdToGetVariableInR = "Sys.getenv(\"R_LIBS_USER\")";
            if ((osType != null) && (osType.contains("Windows"))) {
              cmdToGetVariableInR = "Sys.getenv('R_LIBS_USER')"; // Could possibly do this on the other platforms as well.
            }
            String[] cmd = new String[]{s_rHome + File.separator + "bin" + File.separator + rScriptExeString, "-e",
                    cmdToGetVariableInR};
            Process p = Runtime.getRuntime().exec(cmd);
            int execResult = p.waitFor();
            if (execResult != 0) {
              System.err.println("Failed to execute Rscript to get library folder from R.");
              s_rHome = null;
              return false;
            }

            BufferedReader bf = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String firstLine = bf.readLine();
            while (!firstLine.startsWith("[1]")) { // Rscript may output a warning that we should just skip
              if ((firstLine = bf.readLine()) == null) {
                firstLine = "";
                break;
              }
            }

            Pattern pat = Pattern.compile("\"([^\"]*)\"");
            Matcher m = pat.matcher(firstLine);
            if (!m.find()) {
              System.err.println("Failed to extract valid library folder from information return by Sys.getenv(\"R_LIBS_USER\"): " +
                      firstLine);
              s_rHome = null;
              return false;
            }
            rLibsUser = m.group(1);
            if (rLibsUser.substring(0, 1).equals("~")) { // This should never happen on Windows
              String home = null;
              if ((home = getenv("HOME")) == null) {
                System.err.println("Failed to get user home from HOME variable.");
                s_rHome = null;
                return false;
              }
              rLibsUser = rLibsUser.replaceFirst("~", home);
            }
          } catch (Exception ex) {
            System.err.println("Failed to establish library folder of R.");
            ex.printStackTrace();
            s_rHome = null;
            return false;
          }
        }
        if (s_debug) {
          System.err.println("Setting R_LIBS_USER to " + rLibsUser);
        }
        if (SetEnvironmentVariables.INSTANCE.setenv("R_LIBS_USER", rLibsUser, 0) != 0) {
          System.err.println("Failed to set R_LIBS_USER.");
          s_rHome = null;
          return false;
        }
      }
      if (!(new File(rLibsUser).exists())) {
        if (s_debug) {
          System.err.println("Folder " + rLibsUser + " does not exist. Trying to create.");
        }
        if (!(new File(rLibsUser)).mkdirs()) {
          System.err.println("Failed to create folder " + rLibsUser);
          s_rHome = null;
          return false;
        }
      }
      System.setProperty("r.libs.user", rLibsUser);

      if (!installLibraryViaRuntime(rScriptExeString, rLibsUser, "rJava")) {
        return false;
      }

      // Install JavaGD here to avoid failure of subsequent R library installations on Windows?
      if (!installLibraryViaRuntime(rScriptExeString, rLibsUser, "JavaGD")) {
        return false;
      }
    }
    return true; // We have established a potentially valid R_HOME and R_LIBS_USER.
  }

  /**
   * Installs the given R library by invoking R using the runtime environment if the library directory
   * cannot be found in rLibsUser.
   */
  private static boolean installLibraryViaRuntime(String rScriptExeString, String rLibsUser, String libraryName) {

    File libraryF = new File(rLibsUser + File.separator + libraryName);
    if (libraryF.exists()) {
      if (s_debug) {
        System.err.println("Found " + libraryName + " installed in " + libraryF.getPath());
      }
    } else {
      if (s_debug) {
        System.err.println("Did not find " + libraryName + " installed in " + libraryF.getPath() + " -- trying to install.");
      }
      try {
        String[] cmd = new String[]{s_rHome + File.separator + "bin" + File.separator + rScriptExeString, "-e",
                "local(options(install.packages.compile.from.source='never'));" + // No spaces!
                        "local({r=getOption('repos');" + // Need to use = instead of <- for Windows!
                        "r['CRAN']='https://cloud.r-project.org';" + // Need to use = instead of <- for Windows!
                        "options(repos=r)});" +
                        "install.packages('" + libraryName +"')"}; // Single quotes everywhere for Windows!
        Process p = Runtime.getRuntime().exec(cmd);
        int execResult = p.waitFor();
        if (execResult != 0) {
          BufferedReader bf = new BufferedReader(new InputStreamReader(p.getErrorStream()));
          String line;
          while ((line = bf.readLine()) != null) {
            System.err.println(line);
          }
          throw new Exception("Rscript returned non-zero value.");
        } else {
          BufferedReader bf = new BufferedReader(new InputStreamReader(p.getInputStream()));
          String line;
          while ((line = bf.readLine()) != null) {
            System.err.println(line);
          }
        }
      } catch (Exception ex) {
        System.err.println("Failed to install " + libraryName + ".");
        ex.printStackTrace();
        s_rHome = null;
        return false;
      }
    }
    return true;
  }

  /**
   * Inject byte code into the root class loader and make sure that the native
   * libraries are loaded (just once) and all JRI classes are loaded by the root
   * class loader
   * 
   * @return a reference to the singleton RSessionImpl object loaded and
   *         initialized in the root class loader.
   * 
   * @throws Exception if a problem occurs
   */
  public static synchronized Object load() throws Exception {
    if (s_api != null) {
      return s_api;
    }

    // check if we need to inject the native loader and dependencies
    if (!hasInjectedNativeLoader()) {

      // check for R_HOME
      boolean checkRHome = checkRHome(); // Need to do this first to fix things up if necessary and possible

      Class<?> nativeLoader = injectJRINativeLoader(); // Do this even if R_HOME is not valid, so that class discovery works

      if (checkRHome) {
        s_api = Class.forName(RSESSION_IMPL).newInstance();

        Method initMethod = nativeLoader.getDeclaredMethod("init", new Class[0]);
        s_api = initMethod.invoke(s_api, (Object[]) null);

        if (s_api == null) {
          throw new Exception("Unable to establish R session implementation.");
        }

        s_isLoaded = true;
      }
    } else {
      if (checkRHome()) {
        Class<?> implClass = Class.forName(RSESSION_IMPL);

        Method singletonGrabber = implClass.getDeclaredMethod("getSessionSingleton", new Class[] {});

        s_api = singletonGrabber.invoke(null, (Object[]) null);

        if (s_api == null) {
          throw new Exception("Unable to establish R session implementation.");
        }

        s_isLoaded = true;
      }
    }

    return s_api;
  }

  /**
   * Returns true if the native libraries were loaded successfully.
   * 
   * @return true if the native library was loaded successfully
   */
  public static boolean isNativeLibraryLoaded() {
    return s_isLoaded;
  }

  private static boolean hasInjectedNativeLoader() {
    try {
      final String nativeLoaderClassName = "weka.core.RSessionImpl";
      Class<?> c = Class.forName(nativeLoaderClassName);
      // If this native loader class is already defined,
      // it means that another class loader already loaded the native lib

      return true;
    } catch (ClassNotFoundException e) {
      // do loading
      return false;
    }
  }

  private static ClassLoader getRootClassLoader() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    while (cl.getParent() != null) {
      if (s_debug) {
        System.err.println("Getting parent classloader....");
      }
      cl = cl.getParent();
    }
    return cl;
  }

  private static byte[] getByteCode(String resourcePath) throws IOException {
    InputStream in = RSession.class.getResourceAsStream(resourcePath);
    if (in == null) {
      throw new IOException(resourcePath + " is not found");
    }

    byte[] buf = new byte[1024];
    ByteArrayOutputStream byteCodeBuf = new ByteArrayOutputStream();
    for (int readLength; (readLength = in.read(buf)) != -1;) {
      byteCodeBuf.write(buf, 0, readLength);
    }
    in.close();

    return byteCodeBuf.toByteArray();
  }

  /**
   * Inject RSessionImpl and JRINativeLoader class to the root class loader
   * 
   * @return RSessionImp class initialized in the root class loader.
   *         Instantiating this will cause the native libraries to be loaded via
   *         JRINativeLoader (also in the root class loader)
   */
  private static Class<?> injectJRINativeLoader() throws Exception {
    final String[] PRELOAD = new String[] {
      // JIRI classes
      "org.rosuda.JRI.Mutex",
      "org.rosuda.JRI.RBool",
      "org.rosuda.JRI.RConsoleOutputStream",
      "org.rosuda.JRI.REXP",
      "org.rosuda.JRI.RFactor",
      "org.rosuda.JRI.RList",
      "org.rosuda.JRI.RMainLoopCallbacks",
      "org.rosuda.JRI.RVector",
      "org.rosuda.JRI.Rengine",

      // REngine classes
      "org.rosuda.REngine.REXP",
      "org.rosuda.REngine.REngineInputInterface",
      "org.rosuda.REngine.REngineCallbacks",
      "org.rosuda.REngine.REXPVector",
      "org.rosuda.REngine.REXPGenericVector",
      "org.rosuda.REngine.REXPExpressionVector",
      "org.rosuda.REngine.REngineException",
      "org.rosuda.REngine.REXPMismatchException",
      "org.rosuda.REngine.REXPInteger",
      "org.rosuda.REngine.REXPS4",
      "org.rosuda.REngine.REXPRaw",
      "org.rosuda.REngine.REXPReference",
      "org.rosuda.REngine.REXPLogical",
      "org.rosuda.REngine.RFactor",
      "org.rosuda.REngine.REngineOutputInterface",
      "org.rosuda.REngine.REngineStdOutput",
      "org.rosuda.REngine.REXPJavaReference",
      "org.rosuda.REngine.REXPUnknown",
      "org.rosuda.REngine.REngineUIInterface",
      "org.rosuda.REngine.REngineConsoleHistoryInterface",
      "org.rosuda.REngine.REngineEvalException",
      "org.rosuda.REngine.REXPEnvironment",
      "org.rosuda.REngine.MutableREXP",
      "org.rosuda.REngine.REXPNull",
      "org.rosuda.REngine.REXPSymbol",
      "org.rosuda.REngine.REXPString",
      "org.rosuda.REngine.REXPWrapper",
      "org.rosuda.REngine.REngine",
      "org.rosuda.REngine.REXPDouble",
      "org.rosuda.REngine.RList",
      "org.rosuda.REngine.REXPFactor",
      "org.rosuda.REngine.REXPList",
      "org.rosuda.REngine.REXPLanguage",

      // JRIEngine classes
      "org.rosuda.REngine.JRI.JRIEngine",
      "org.rosuda.REngine.JRI.JRIEngine$JRIPointer",

      // JavaGD
      "org.rosuda.javaGD.GDObject", "org.rosuda.javaGD.GDState",
      "org.rosuda.javaGD.GDContainer", "org.rosuda.javaGD.GDFill",
      "org.rosuda.javaGD.GDRect", "org.rosuda.javaGD.LocatorSync",
      "org.rosuda.javaGD.GDCanvas", "org.rosuda.javaGD.GDCanvas$Refresher",
      "org.rosuda.javaGD.GDLinePar", "org.rosuda.javaGD.GDFont",
      "org.rosuda.javaGD.JGDPanel", "org.rosuda.javaGD.JGDBufferedPanel",
      "org.rosuda.javaGD.JGDBufferedPanel$Refresher",
      "org.rosuda.javaGD.GDClip", "org.rosuda.javaGD.XGDserver",
      "org.rosuda.javaGD.XGDserver$XGDworker", "org.rosuda.javaGD.GDInterface",
      "org.rosuda.javaGD.JavaGD", "org.rosuda.javaGD.GDRaster",
      "org.rosuda.javaGD.GDLine", "org.rosuda.javaGD.GDCircle",
      "org.rosuda.javaGD.GDColor", "org.rosuda.javaGD.GDPath",
      "org.rosuda.javaGD.GDPolygon", "org.rosuda.javaGD.GDText",

      // Weka
      "weka.core.RSessionException", "weka.core.WekaJavaGD"};

    ClassLoader rootClassLoader = getRootClassLoader();

    // .bytecode classes must *not* get loaded by child class loaders. Other
    // classes
    // in PRELOAD are needed as dependencies but it is not an issue if they also
    // get loaded by child class loaders
    byte[] byteCodeLogger = getByteCode("/weka/core/RLoggerAPI.bytecode");
    byte[] byteCodeAPI = getByteCode("/weka/core/RSessionAPI.bytecode");
    byte[] byteCodeGDL = getByteCode("/weka/core/JavaGDListener.bytecode");
    byte[] byteCodeGD = getByteCode("/weka/core/JavaGDOffscreenRenderer.bytecode");
    byte[] byteCodeRSession = getByteCode("/weka/core/RSessionImpl.bytecode");
    byte[] byteCodeJRINative = getByteCode("/weka/core/JRINativeLoader.bytecode");
    byte[] byteCodeJavaGDNotifier = getByteCode("/weka/core/JavaGDNotifier.bytecode");
    byte[] byteCodeRniIdle = getByteCode("/weka/core/RniIdle.bytecode");
    byte[] byteCodeREngineStartup= getByteCode("/weka/core/REngineStartup.bytecode");

    List<byte[]> preloadClassByteCode = new ArrayList<byte[]>();

    for (String p : PRELOAD) {
      if (p.contains("JRI") || p.contains("javaGD") || p.contains("weka")) {
        preloadClassByteCode.add(getByteCode(String.format("/%s.class",
          p.replaceAll("\\.", "/"))));
      } else {
        // REngine classes
        // .bytecode classes must *not* get loaded by child class loaders
        preloadClassByteCode.add(getByteCode(String.format(
          "/%s.class.bytecode", p.replaceAll("\\.", "/"))));
      }
    }

    Class<?> classLoader = Class.forName("java.lang.ClassLoader");
    Method defineClass = classLoader.getDeclaredMethod("defineClass",
      new Class[] { String.class, byte[].class, int.class, int.class,
        ProtectionDomain.class });

    ProtectionDomain pd = System.class.getProtectionDomain();

    // ClassLoader.defineClass is a protected method, so we have to make it
    // accessible.
    defineClass.setAccessible(true);

    try {

      // Create a new class using a ClassLoader#defineClass
      if (s_debug) {
        System.err.println("Injecting JRI classes into the root class loader...");
      }
      // Define dependent classes in the root class loader
      for (int i = 0; i < PRELOAD.length; ++i) {
        // System.err.println(PRELOAD[i]);
        byte[] b = preloadClassByteCode.get(i);
        if (PRELOAD[i].equals("org.rosuda.JRI.Rengine")) {
          Class injectedRengineClass = (Class)defineClass.invoke(rootClassLoader, PRELOAD[i], b, 0, b.length, pd);
          injectedRengineClass.getDeclaredField("jriLoaded").setBoolean(null, true);
        } else {
          defineClass.invoke(rootClassLoader, PRELOAD[i], b, 0, b.length, pd);
        }
      }

      // Create REngineImpl class and other directly dependent classes from byte
      // code
      defineClass.invoke(rootClassLoader, "weka.core.RniIdle",
        byteCodeRniIdle, 0, byteCodeRniIdle.length, pd);
      defineClass.invoke(rootClassLoader, "weka.core.REngineStartup",
              byteCodeREngineStartup, 0, byteCodeREngineStartup.length, pd);
      defineClass.invoke(rootClassLoader, "weka.core.RLoggerAPI",
        byteCodeLogger, 0, byteCodeLogger.length, pd);
      defineClass.invoke(rootClassLoader, "weka.core.RSessionAPI", byteCodeAPI,
        0, byteCodeAPI.length, pd);
      defineClass.invoke(rootClassLoader, "weka.core.JavaGDListener",
        byteCodeGDL, 0, byteCodeGDL.length, pd);
      defineClass.invoke(rootClassLoader, "weka.core.JavaGDNotifier",
        byteCodeJavaGDNotifier, 0, byteCodeJavaGDNotifier.length, pd);
      defineClass.invoke(rootClassLoader, "weka.core.JavaGDOffscreenRenderer",
        byteCodeGD, 0, byteCodeGD.length, pd);
      defineClass.invoke(rootClassLoader, JRI_NATIVE_LOADER, byteCodeJRINative,
        0, byteCodeJRINative.length, pd);
      defineClass.invoke(rootClassLoader, RSESSION_IMPL, byteCodeRSession, 0,
        byteCodeRSession.length, pd);
    } finally {
      // Reset the accessibility to defineClass method
      defineClass.setAccessible(false);
    }
    return rootClassLoader.loadClass(RSESSION_IMPL);
  }
}
