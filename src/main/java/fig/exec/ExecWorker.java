package fig.exec;

import static fig.basic.LogInfo.end_track;
import static fig.basic.LogInfo.error;
import static fig.basic.LogInfo.logs;
import static fig.basic.LogInfo.logss;
import static fig.basic.LogInfo.begin_track;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import fig.basic.CharEncUtils;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.OrderedStringMap;
import fig.basic.StrUtils;
import fig.basic.SysInfoUtils;
import fig.basic.Utils;

class SimpleHTTP {
  private static String encode(String s) throws UnsupportedEncodingException {
    return URLEncoder.encode(s, "UTF-8");
  }

  public static String createURL(String url, OrderedStringMap params) throws UnsupportedEncodingException {
    StringBuilder buf = new StringBuilder();
    buf.append(url);
    boolean isFirst = true;
    for(String key : params.keys()) {
      buf.append(isFirst ? '?' : '&');
      buf.append(encode(key) + "=" + encode(params.get(key)));
      isFirst = false;
    }
    return buf.toString();
  }

  public static InputStream getInputStream(String url) throws IOException {
    URLConnection connection = new URL(url).openConnection();
    return connection.getInputStream();
  }

  public static List<String> getLines(String url) throws IOException {
    return IOUtils.readLines(CharEncUtils.getReader(getInputStream(url)));
  }
}

class Job {
  public int nice;
  public String workingDir; // Path to run it in
  public String command;

  public static Job parse(OrderedStringMap params) {
    if(params == null) return null;
    Job job = new Job();
    job.nice = Utils.parseIntEasy(params.get("nice"), 0);
    job.workingDir = params.get("workingDir");
    job.command = params.get("command");
    if(job.command == null) return null; // No job
    return job;
  }

  public void log(String s) {
    LogInfo.begin_track_printAll(s);
    logs("workingDir = " + workingDir);
    logs("command = " + command);
    logs("nice = " + nice);
    LogInfo.end_track();
  }
}

/**
 * Runs on a machine.
 * Periodically sends the master information about the worker.
 * Contacts the execution servlet master for executions to run
 * and runs them.
 * Settings can be changed from the servlet.
 *
 * TODO: killing jobs doesn't work
 */
public class ExecWorker implements Runnable {
  @Option public String masterURL = "http://localhost:8080/fig/Fig";
  @Option(required=true) public String workerName;
  @Option public int sleepInterval = 5;
  @Option(gloss="CPU must be free for this many consecutive periods before asking for a job")
    public int conseqCPUFreeBeforeGetJob = 3;
  @Option public boolean verbose = false;
  @Option public int numSuccessJobs, numJobs;
  @Option(gloss="Create a log file (worker.log) in the execution directory when running a job") public boolean logInWorkingDir = false;
  @Option(gloss="RMI port number (for distributed jobs) (-1 for don't enable") public int rmiPort = -1;

  public static final String version = "7";

  private Thread runnerThread;
  private Job currJob; // Current job that's being run (shared with runner thread)
  private boolean terminate;

  // To make sure that we don't ask for a job when the machine is actually
  // busy, we make sure that the CPU is free for at least conseqCPUFreeBeforeGetJob
  // before getting a job
  private int conseqCPUFree;

  public static void main(String[] args) {
    Execution.run(args, new ExecWorker());
  }

  public void run() {
    // Start thread to run jobs
    this.runnerThread = new Thread(new Runnable() {
      public void run() {
        while(true) {
          if(currJob != null) {
            processJob(currJob);
            currJob = null;
          }
          else
            Utils.sleep(sleepInterval*1000);
        }
      }

      public void processJob(Job job) {
        if(job == null) return;

        try {
          currJob.log("[WORKER] Processing job");
          String command = job.command;
          if(job.nice != 0)
            command = "nice -n " + job.nice + " " + command; 
          if(!StrUtils.isEmpty(job.workingDir))
            command = Utils.makeRunCommandInDir(command, job.workingDir);
          logs("[WORKER] Running: " + command);
          
          boolean success = false;
          if (logInWorkingDir)
          {
		
        	  OutputStream out = new FileOutputStream(new File(job.workingDir + "/worker.log"));
        	  success = Utils.systemLogin(command,out,out);
          }
          else
          {
        	  success = Utils.systemLogin(command);
          }
          if (success)
            numSuccessJobs++;
          else
            logs("[WORKER] Job failed");
        } catch(Exception e) {
          error("[WORKER] processJob() failed: " + e);
        }

        numJobs++;
        logs("[WORKER] Completed %d/%d jobs successfully", numSuccessJobs, numJobs);
      }
    });
    runnerThread.start();
//    if (rmiPort > 0)
//    {
//    	startRmiHard(rmiPort);
//    }

    // Keep on communicating with master
    logss("[WORKER] " + workerName + " started");
    while(true) {
      if(sendStatus()) {
        if(terminate) { runnerThread.interrupt(); break; }
        if(shouldGetJob())
          currJob = getJob();
      }
      Utils.sleep(sleepInterval*1000);
    }

    logss("[WORKER] " + workerName + " terminated");
  }

  private boolean shouldGetJob() {
    if(currJob != null) return false; // Already running a job

    // Make sure CPU is really free
    int numFreeCPUs = SysInfoUtils.getNumFreeCPUs();
    if(numFreeCPUs > 0) conseqCPUFree++;
    else conseqCPUFree = 0;
    if(conseqCPUFree < conseqCPUFreeBeforeGetJob) return false;

    return true;
  }

  private String getStatus() {
    if(currJob == null) return "idle";
    else return "busy";
  }

  private String getProcSummary() {
    String cmd = "ps --no-headers ax -o %cpu,%mem,user,comm 2>/dev/null";
    try {
      // Print out information about active processes
      List<String> heavyProcs = new ArrayList();
      for(String line : Utils.systemGetStringOutput(cmd).split("\n")) {
        String[] tokens = line.trim().split(" ");
        if(Utils.parseDoubleEasy(tokens[0]) > 50)
          heavyProcs.add(StrUtils.join(tokens, " "));
      }
      return StrUtils.join(heavyProcs, "<br>");
    } catch(Exception e) {
      return "";
    }
  }

  // Observe things like the amount of memory available and the CPU free
  // and send a status message to master
  // Return whether the communication was successful
  public boolean sendStatus() {
    String host = SysInfoUtils.getHostName();
    int cpuSpeed = SysInfoUtils.getCPUSpeed();
    int numFreeCPUs = SysInfoUtils.getNumFreeCPUs();
    int numTotalCPUs = SysInfoUtils.getNumCPUs();
    long freeMemory = SysInfoUtils.getFreeMemory();
    if(verbose)
      logs("[WORKER] %s: CPU speed = %d MHz, %d/%d CPUs free", workerName, cpuSpeed, numFreeCPUs, numTotalCPUs);

    OrderedStringMap request = newRequest("setStatus");
    request.put("host", host);
    request.put("status", getStatus());
    request.put("version", version);
    request.put("uptime", LogInfo.getWatch().stop());
    request.put("cpuSpeed", ""+cpuSpeed);
    request.put("numFreeCPUs", ""+numFreeCPUs);
    request.put("numTotalCPUs", ""+numTotalCPUs);
    request.put("freeMemory", ""+freeMemory);
    request.put("procSummary", getProcSummary());
    request.put("numSuccessJobs", ""+numSuccessJobs);
    request.put("numJobs", ""+numJobs);
    OrderedStringMap response = makeHTTPRequest(request);
    if(response == null) return false;
    if("true".equals(response.get("kill"))) runnerThread.interrupt();
    if("true".equals(response.get("terminate"))) terminate = true;
    return true;
  }

  public Job getJob() {
    OrderedStringMap request = newRequest("getJob");
    OrderedStringMap response = makeHTTPRequest(request);
    return Job.parse(response);
  }

  private OrderedStringMap newRequest(String op) {
    OrderedStringMap request = new OrderedStringMap();
    request.put("mode", "op");
    request.put("trail", "workers\tworkers\t"+workerName);
    request.put("op", op);
    return request;
  }

  public OrderedStringMap makeHTTPRequest(OrderedStringMap request) {
    try {
      if(verbose) request.log("[WORKER] Request");

      // Make the connection
      String url = SimpleHTTP.createURL(masterURL, request);
      List<String> lines = SimpleHTTP.getLines(url);

      // Get reply
      OrderedStringMap response = new OrderedStringMap();
      for(String line : lines) {
        String[] tokens = line.split("\t", 2);
        if(tokens.length != 2) continue;
        response.put(tokens[0], tokens[1]);
      }
      if(!"true".equals(response.get("success"))) {
        logs("[WORKER] Request failed: " + url);
        response.log("[WORKER] Response");
        return null;
      }
      if(verbose) response.log("[WORKER] Response");
      return response;
    } catch(Exception e) {
      logs("[WORKER] Unable to contact %s right now: %s", masterURL, e);
      return null;
    }
  }
  
  
//  private static void startRmiHard(int rmiPort){
//	 RemoteExecutorImpl.startRmi(rmiPort);
//  }
}
