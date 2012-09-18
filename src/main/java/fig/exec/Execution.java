package fig.exec;

import static fig.basic.LogInfo.begin_track_printAll;
import static fig.basic.LogInfo.end_track;
import static fig.basic.LogInfo.error;
import static fig.basic.LogInfo.logs;
import static fig.basic.LogInfo.logss;
import static fig.basic.LogInfo.stderr;
import static fig.basic.LogInfo.stdout;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fig.basic.CharEncUtils;
import fig.basic.ClassInitializer;
import fig.basic.Exceptions;
import fig.basic.IOUtils;
import fig.basic.ListUtils;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.OptionsParser;
import fig.basic.OrderedStringMap;
import fig.basic.StopWatchSet;
import fig.basic.StrUtils;
import fig.basic.SysInfoUtils;
import fig.basic.Utils;
import fig.basic.IOUtils;
import fig.basic.Fmt;
import fig.record.Record;

/**
 * Represents all the settings and output of an execution of a program.
 * An execution is defined by all the options registered with OptionsParser.
 * Creates a directory for the execution in the execution pool dir.
 */
public class Execution {
  @Option(gloss="Whether to create a directory for this run; if not, don't generate output files")
    public static boolean create = false;
  @Option(gloss="Whether to create a thread to monitor the status.")
    public static boolean monitor = false;

  // How to create the execution directory
  @Option(gloss="Directory to put all output files; if blank, use execPoolDir.")
    public static String execDir;
  @Option(gloss="Directory which contains all the executions (or symlinks).")
    public static String execPoolDir;
  @Option(gloss="Directory which actually holds the executions.")
    public static String actualExecPoolDir;
  @Option(gloss="Overwrite the contents of the execDir if it doesn't exist (e.g., when running a thunk).")
    public static boolean overwriteExecDir;
  @Option(gloss="Assume in the run directory, automatically set execPoolDir and actualExecPoolDir")
    public static boolean useStandardExecPoolDirStrategy = false;

  @Option(gloss="Simply print options and exit.")
    public static boolean printOptionsAndExit = false;
  @Option(gloss="Miscellaneous options (written to options.map and output.map, displayed in servlet); example: a=3 b=4")
    public static ArrayList<String> miscOptions = new ArrayList();

  @Option(gloss="Name of the view to add this execution to in the servlet")
    public static ArrayList<String> addToView = new ArrayList<String>();
  @Option(gloss="Record file to write to")
    public static String recordPath;

  @Option(gloss="Character encoding")
    public static String charEncoding;
  @Option(gloss="Name of jar files to load prior to execution")
    public static ArrayList<String> jarFiles = new ArrayList<String>();
  
  @Option(gloss = "Skip initialization of jars")
	public static boolean dontInitializeJars = false;

  @Option(gloss = "Initialize from jars after copying them to a newly created execDir")
	public static boolean initializeJarsAfterDirCreation = false;

  // Thunk
  @Option(gloss="Make a thunk (a delayed computation).")
    public static boolean makeThunk;
  @Option(gloss="A note to the servlet to automatically run the thunk when it sees it")
    public static boolean thunkAutoQueue;
  @Option(gloss="Priority of the thunk.")
    public static int thunkPriority;
  @Option(gloss="Launch this class")
    public static String thunkMainClassName;
  @Option(gloss="Java options to pass to Java when later running the thunk")
    public static String thunkJavaOpts;
  @Option(gloss="Required memory (in MB)")
    public static int thunkReqMemory = 1024;

  //Exception handling
  @Option(gloss="Whether to catch exceptions (ignored when making a thunk)")
  public static boolean dontCatchExceptions;

  // Whether to print out start a main() track (LogInfo)
  public static boolean startMainTrack = true;

  // Execution directory that we write to (execDir is just a suggestion)
  // Could be a symlink to a directory in actualExecPoolDir
  private static String virtualExecDir;

  // Passed to the options parser
  public static boolean ignoreUnknownOpts = false;

  static OrderedStringMap inputMap = new OrderedStringMap(); // Accessed by monitor thread
  private static OrderedStringMap outputMap = new OrderedStringMap();
  private static OptionsParser parser;
  private static MonitorThread monitorThread; // Thread for monitoring
  static int exitCode = 0;

  static boolean shouldBail = false; // Set by monitor thread
  public static boolean shouldBail() { return shouldBail; }

  private static void mkdirHard(File f) {
    if(!f.mkdir()) {
      stderr.println("Cannot create directory: " + f);
      System.exit(1);
    }
  }

  public static String getVirtualExecDir() { return virtualExecDir; }

  /**
   * Return an unused directory in the execution pool directory.
   * Set virtualExecDir
   */
  public static String createVirtualExecDir() {
    if(useStandardExecPoolDirStrategy) {
      // Assume we are in the run directory, so set the standard paths
      execPoolDir = new File(SysInfoUtils.getcwd(), "state/execs").toString();
      actualExecPoolDir = new File(SysInfoUtils.getcwd(), "state/hosts/"+SysInfoUtils.getShortHostName()).toString();
      if(!new File(actualExecPoolDir).isDirectory())
        actualExecPoolDir = null;
    }
    if(!StrUtils.isEmpty(execPoolDir) && !new File(execPoolDir).isDirectory())
      throw Exceptions.bad("Execution pool directory '" + execPoolDir + "' doesn't exist");
    if(!StrUtils.isEmpty(actualExecPoolDir) && !new File(actualExecPoolDir).isDirectory())
      throw Exceptions.bad("Actual execution pool directory '" + actualExecPoolDir + "' doesn't exist");

    if(!StrUtils.isEmpty(execDir)) { // Use specified execDir
      boolean exists = new File(execDir).isDirectory();
      if(exists && !overwriteExecDir)
        throw Exceptions.bad("Directory already exists and overwrite flag is false");
      if(!exists)
        mkdirHard(new File(execDir));
      else {
        // This part looks at actualExecPoolDir
        // This case is overwriting an existing execution directory, which
        // happens when we are executing a thunk.  We have to be careful here
        // because the actual symlinked directory that was created when thunking
        // might be using a different actualPoolDir.  If this happens, we need
        // to move the actual thunked symlinked directory into the actual
        // execution pool directory requested.  In fact, we always do this for simplicity.
        String oldActualExecDir = Utils.systemGetStringOutputEasy("readlink " + execDir);
        if(oldActualExecDir == null) { // Not symlink
          if(!StrUtils.isEmpty(actualExecPoolDir))
            throw Exceptions.bad("The old execution directory was not created with actualExecPoolDir but now we want an actualExecPoolDir");
          // Do nothing, just use the directory as is
        }
        else { // Symlink
          oldActualExecDir = oldActualExecDir.trim();
          if(StrUtils.isEmpty(actualExecPoolDir))
            throw Exceptions.bad("The old execution directory was created with actualExecPoolDir but now we don't want an actualExecPoolDir");
          // Note that now the execution numbers might not correspond between the
          // actual and virtual execution pool directories.
          File newActualExecDir = null;
          for(int i = 0; ; i++) {
            newActualExecDir = new File(actualExecPoolDir, i+"a.exec");
            if(!newActualExecDir.exists())
              break;
          }
          // Move the old directory to the new directory
          Utils.systemHard(String.format("mv %s %s", oldActualExecDir, newActualExecDir));
          // Update the symlink (execDir -> newActualExecDir)
          Utils.systemHard(String.format("ln -sf %s %s", newActualExecDir.getAbsolutePath(), execDir));
        }
      }
      return virtualExecDir = execDir;
    }

    // execDir hasn't been specified, so we need to pick one from a pool directory
    // execPoolDir must exist; actualExecPoolDir is optional

    // Get a list of files that already exists
    Set<String> files = new HashSet<String>();
    for(String f : new File(execPoolDir).list()) files.add(f);

    // Go through and pick out a file that doesn't exist
    int numFailures = 0;
    for(int i = 0; numFailures < 3; i++) {
      // Either the virtual file (a link) or the actual file
      File f = new File(execPoolDir, i+".exec");
      // Actual file
      File g = StrUtils.isEmpty(actualExecPoolDir) ? null : new File(actualExecPoolDir, i+".exec");

      if(!files.contains(i+".exec") && (g == null || !g.exists())) {
        if(g == null || g.equals(f)) {
          mkdirHard(f);
          return virtualExecDir = f.toString();
        }
        // Create symlink before mkdir to try to reserve the name and avoid race conditions
        if(Utils.createSymLink(g.getAbsolutePath(), f.getAbsolutePath())) {
          mkdirHard(g);
          return virtualExecDir = f.toString();
        }

        // Probably because someone else already linked to it
        // in the race condition: so try again
        stderr.println("Cannot create symlink from " + f + " to " + g);
        numFailures++;
      }
    }
    throw Exceptions.bad("Failed many times to create execution directory");
  }

  // Get the path of the file (in the execution directory)
  public static String getFile(String file) {
    if(StrUtils.isEmpty(virtualExecDir)) return null;
    if(StrUtils.isEmpty(file)) return null;
    return new File(virtualExecDir, file).toString();
  }

  public static void linkFileToExec(String realFileName, String file) {
    if(StrUtils.isEmpty(realFileName) || StrUtils.isEmpty(file)) return;
    File f = new File(realFileName);
    Utils.createSymLink(f.getAbsolutePath(), getFile(file));
  }
  public static void linkFileFromExec(String file, String realFileName) {
    if(StrUtils.isEmpty(realFileName) || StrUtils.isEmpty(file)) return;
    File f = new File(realFileName);
    Utils.createSymLink(getFile(file), f.getAbsolutePath());
  }

  // Getting input and writing output
  public static boolean getBooleanInput(String s) {
    String t = inputMap.get(s, "0");
    return t.equals("true") || t.equals("1");
  }
  public synchronized static String getInput(String s) { return inputMap.get(s); }
  public synchronized static void putOutput(String s, Object t) { outputMap.put(s, StrUtils.toString(t)); }
  public synchronized static void printOutputMapToStderr() { outputMap.print(stderr); }
  public synchronized static void printOutputMap(String path) {
    if(StrUtils.isEmpty(path)) return;
    // First write to a temporary directory and then rename the file
    String tmpPath = path+".tmp";
    if(outputMap.printEasy(tmpPath))
      new File(tmpPath).renameTo(new File(path));
  }

  public static void setExecStatus(String newStatus, boolean override) {
    String oldStatus = outputMap.get("exec.status");
    if(oldStatus == null || oldStatus.equals("running")) override = true;
    if(override) putOutput("exec.status", newStatus);
  }

  public static void putLogRec(String key, Object value) {
    logss("%s = %s", key, value);
    Record.add(key, value);
    putOutput(key, value);
  }

  static OrderedStringMap getInfo() {
    OrderedStringMap map = new OrderedStringMap();
    map.put("Date", SysInfoUtils.getCurrentDateStr());
    map.put("Host", SysInfoUtils.getHostName());
    map.put("CPU speed", SysInfoUtils.getCPUSpeedStr());
    map.put("Max memory", SysInfoUtils.getMaxMemoryStr());
    map.put("Num CPUs", SysInfoUtils.getNumCPUs());
    return map;
  }

  public static void init(String[] args, Object... objects) {
    //// Parse options
    // If one of the objects is an options parser, use that; otherwise, create a new one
    for(int i = 0; i < objects.length; i++) {
      if(objects[i] instanceof OptionsParser) {
        parser = (OptionsParser)objects[i];
        objects[i] = null;
      }
    }
    if(parser == null) parser = new OptionsParser();
    parser.doRegister("log", LogInfo.class);
    parser.doRegister("exec", Execution.class);
    parser.doRegisterAll(objects);
    // These options are specific to the execution, so we don't want to overwrite them
    // with a previous execution's.
    parser.setDefaultDirFileName("options.map");
    parser.setIgnoreOptsFromFileName("options.map",
      ListUtils.newList("log.file", "exec.execDir",
        "exec.execPoolDir", "exec.actualPoolDir", "exec.makeThunk"));
    if(ignoreUnknownOpts) parser.ignoreUnknownOpts();
    if(!parser.doParse(args)) System.exit(1);

    // Load classes
    if (!dontInitializeJars && !initializeJarsAfterDirCreation)
    {
			initializeJars(false);
    }
    // Set character encoding
    if(charEncoding != null)
      CharEncUtils.setCharEncoding(charEncoding);

    if(printOptionsAndExit) { // Just print options and exit
      parser.doGetOptionPairs().print(stdout);
      System.exit(0);
    }

    // Create a new directory
    if(create) {
      createVirtualExecDir();
      //stderr.println(virtualExecDir);
      if(!makeThunk) LogInfo.file = getFile("log");

      // Copy the Jar files for reference
      if(!makeThunk) {
        for(String jarFile : jarFiles)
          Utils.systemHard(String.format("cp %s %s", jarFile, virtualExecDir));
        if (initializeJarsAfterDirCreation)
				{
					initializeJars(true);
				}
      }
    }
    else {
      LogInfo.file = "";
    }

    // Handle miscOptions
    for(String opt : miscOptions) {
      String[] tokens = opt.split("=");
      if(tokens.length == 2)
        putOutput(tokens[0], tokens[1]);
    }

    if(!makeThunk) {
      LogInfo.init();
      if(startMainTrack) begin_track_printAll("main()");
    }

    // Output options
    if(!makeThunk && virtualExecDir != null) logs("Execution directory: " + virtualExecDir);
    if(!makeThunk) getInfo().printEasy(getFile("info.map"));
    printOptions();
    if(create && addToView.size() > 0)
      IOUtils.printLinesHard(Execution.getFile("addToView"), addToView);

    // Start monitoring
    if(!makeThunk && monitor) {
      monitorThread = new MonitorThread();
      monitorThread.start();
    }

    if(!makeThunk)
      Record.init(!StrUtils.isEmpty(recordPath) ? recordPath : Execution.getFile("record"));
  }

/**
 * 
 */
	private static void initializeJars(boolean inVirtualExecDir)
{
	if (jarFiles.size() > 0)
	{
		List<String> names = new ArrayList();
		for (String jarFile : jarFiles)
			names.add(new File(jarFile).getName());
		stderr.println("Loading JAR files: " + StrUtils.join(names));
		for (String jarFile : jarFiles)
		{
			// Load classes
			String jarPath = inVirtualExecDir ? new File(virtualExecDir, new File(jarFile).getName()).getPath() : jarFile;
				ClassInitializer.initializeJar(jarPath);
			}
	}
}

  // Might want to call this again after some command-line options were changed.
  public static void printOptions() {
    boolean saveMakeThunk = makeThunk; makeThunk = false;
    parser.doGetOptionPairs().printEasy(getFile("options.map"));
    parser.doGetOptionStrings().printEasy(getFile("options.help"));
    makeThunk = saveMakeThunk;
  }

  public static void raiseException(Throwable t) {
    error(t + ":\n" + StrUtils.join(t.getStackTrace(), "\n"));
    t = t.getCause();
    if(t != null)
      error("Caused by " + t + ":\n" + StrUtils.join(t.getStackTrace(), "\n"));
    putOutput("exec.status", "exception");
    exitCode = 1;
  }

  public static void finish() {
    if(!makeThunk) {
      Record.finish();

      if (virtualExecDir != null)
        outputMap.put("exec.disk", Fmt.bytesToString(IOUtils.diskUsageBytesUnder(virtualExecDir)));

      if(monitor) monitorThread.finish();
      setExecStatus(shouldBail ? "bailed" : "done", false);
      outputMap.printEasy(getFile("output.map"));
      StopWatchSet.getStats().printEasy(getFile("time.map"));
      if(!makeThunk && virtualExecDir != null) logs("Execution directory: " + virtualExecDir);
      if(makeThunk && virtualExecDir != null) stderr.println(virtualExecDir);
      if(LogInfo.getNumErrors() > 0 || LogInfo.getNumWarnings() > 0)
        stderr.printf("%d errors, %d warnings\n",
            LogInfo.getNumErrors(), LogInfo.getNumWarnings());
      if(startMainTrack) end_track();
    }

    System.exit(exitCode);
  }

  // This should be all we need to put in a main function.
  // args are the commandline arguments
  // First object is the Runnable object to call run on.
  // All of them are objects whose options args is to supposed to populate.
  public static void run(String[] args, Object... objects) {
	  runWithObjArray(args, objects);
  }
  
  public static void runWithObjArray(String[] args, Object[] objects)
  {
    init(args, objects);

    Object mainObj;
    if(objects[0] instanceof String) mainObj = objects[1];
    else                             mainObj = objects[0];

    if(makeThunk) {
      setExecStatus("thunk", true);
      printOutputMap(Execution.getFile("output.map"));
      List<String> cmd = new ArrayList();
      cmd.add("java");
      if(thunkJavaOpts != null) cmd.add(thunkJavaOpts);
      // Set classpath to make sure we have the exact same environment when we run the thunk
      cmd.add("-cp " + StrUtils.join(jarFiles, ":")+":"+System.getenv("CLASSPATH"));
      // java.class.path doesn't pick up $CLASSPATH for scala programs
      //cmd.add("-cp " + StrUtils.join(jarFiles, ":")+":"+System.getProperty("java.class.path"));
      cmd.addAll(ListUtils.newList(
        thunkMainClassName == null ? mainObj.getClass().getName() : thunkMainClassName,
        "++"+virtualExecDir+"/options.map", // Load these options
        // Next time when we run, just run in the same path that we used to create the thunk
        "-execDir", virtualExecDir, "-overwriteExecDir"));
      IOUtils.printLinesHard(Execution.getFile("job.map"),
        ListUtils.newList(
          "workingDir\t"+SysInfoUtils.getcwd(), // Run from current directory
          "command\t"+StrUtils.join(cmd, "\t"),
          "reqMemory\t"+thunkReqMemory,
          "priority\t"+thunkPriority));
      System.out.println(virtualExecDir);
    }
    else {
    	if (dontCatchExceptions){
    		((Runnable)mainObj).run();
    	}
    	else {
    		try {
    			((Runnable)mainObj).run();
    		} catch(Throwable t) {
    			raiseException(t);
    		}
    	}
    }
    finish();
  }

  // Handlers for before exiting
  public interface ExitHandler {
    public void run();
  }
  private static List<ExitHandler> exitHandlers = new ArrayList();
  public static void addExitHandler(ExitHandler handler) { exitHandlers.add(handler); }
  public static void beforeExit() {
    for(ExitHandler handler : exitHandlers) handler.run();
  }

  // Run a system command, keep track of orphans, and log the output nicely (with indents)
  public static void runSystemCommand(String header, final String cmd) {
    begin_track_printAll(header);
    logs("%s", cmd);
    try {
      final Process process = Utils.openSystem(cmd);
      addExitHandler(new ExitHandler() {
        public void run() {
          logs("KILLING PROCESS OF COMMAND: %s", cmd);
          process.destroy();
          try { process.waitFor(); }
          catch(InterruptedException e) { }
        }
      });
      process.getOutputStream().close();
      String line;

      begin_track_printAll("stdout");
      BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
      while ((line = in.readLine()) != null) logs("%s", line);
      in.close();
      end_track();

      begin_track_printAll("stderr");
      BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      while ((line = err.readLine()) != null) logs("%s", line);
      err.close();
      end_track();

      Utils.closeSystemHard(cmd, process);
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
    end_track();
  }
}
