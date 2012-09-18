package fig.servlet;

import java.io.*;
import java.util.*;
import fig.basic.*;

/**
 * The sourcePath is file containing all the information.
 *
 * Items are the executions that it has run.
 *
 * TODO: dependencies between executions
 */
public class WorkerItem extends Item {
  private static final int messageTimeoutMs = 5*60*1000; // 5 minutes

  private long lastMessageTime = -1; // Time of last message
  private long lastGetJobTime = -1; // Last time this worker tried to get a job
  private boolean hasUpdated;
  private boolean kill, terminate; // Client sets this flag; cleared when issued to worker

  public WorkerItem(Item parent, String name, String sourcePath) {
    super(parent, name, sourcePath);
  }

  protected FieldListMap getMetadataFields() {
    FieldListMap fields = new FieldListMap();
    fields.add("host",     "Host name").processor = new ValueProcessor("s/\\..*/");
    fields.add(countField);
    fields.add("sinceLastMessageTime", "last", "Time elapsed since last message from worker");
    fields.add("status", "Status of the worker");
    fields.add("version", "ver", "Version of the worker code");
    fields.add("uptime", "Time worker has spent up");
    fields.add("cpuSpeed", "CPU speed (MHz)").setNumeric(true).processor = new ValueProcessor("s/$/ MHz");
    fields.add("freeCPUs", "#fcpu", "Number of free CPUs", new Object[] {"$numFreeCPUs", "/", "$numTotalCPUs"}).numeric = true;
    fields.add("freeMemory", "fmem", "Free memory").setNumeric(true).processor = new ValueProcessor("BYTES");
    fields.add("procSummary", "The heavy processes running on the machine (%cpu, %mem, user, command)");
    fields.add("successJobs", "jobs", "Fraction of successful jobs", new Object[] {"$numSuccessJobs", "/", "$numJobs"}).numeric = true;
    fields.add("qualified", "If this worker asks for a job, will it be able to get one?");
    fields.add("nice", "What to set the nice value to when running jobs").setMutable(true).numeric = true;
    fields.add("disabled", "Whether this worker is temporarily disabled").setMutable(true);
    fields.add("priority", "Who has first dibs on jobs (lower is better)").setMutable(true).numeric = true;
    return fields;
  }
  protected FieldListMap getItemsFields() { return ExecItem.createThunkFields(); }

  public void update(UpdateSpec spec, UpdateQueue.Priority priority) throws MyException {
    updateItemsFromFile(spec);
  }

  public Value getSinceLastMessageTimeValue() throws MyException {
    if(lastMessageTime == -1) return new Value(null);
    Value value = FileItem.getSinceLastModifiedTimeValue(lastMessageTime);
    // Put an asterisk if the worker hasn't responded in a while
    if(FileItem.getSinceLastModifiedTime(lastMessageTime) >= messageTimeoutMs)
      return new Value(value.value+"*", value.cmpKey);
    return value;
  }
  protected int getNice() { return Utils.parseIntEasy(metadataMap.get("nice"), 0); }
  protected int getNumFreeCPUs() { return Utils.parseIntEasy(metadataMap.get("numFreeCPUs"), 0); }
  protected long getFreeMemory() { return Utils.parseLongEasy(metadataMap.get("freeMemory"), 0); }
  protected boolean isDisabled() { return Boolean.parseBoolean(metadataMap.get("disabled")); }
  protected int getPriority() { return Utils.parseIntEasy(metadataMap.get("priority"), Integer.MAX_VALUE); }
  protected boolean isQualified() { return ((WorkerView)parent).isQualified(this); } // Ask parent

  // If I have recently asked for a job, then that probably means I'm looking actively.
  public boolean isSeekingJob() { 
    if(isDisabled()) return false;
    final int getJobTimeoutMs = 10*1000; // 10 seconds (2x default sleepInterval for a ExecWorker)
    return new Date().getTime() - lastGetJobTime < getJobTimeoutMs;
  }

  public ResponseObject setStatus(RequestParams params) throws MyException {
    lastMessageTime = new Date().getTime();
    if(!hasUpdated && new File(fileSourcePath()).exists()) loadFromDisk(); // Don't destroy settings
    metadataMap.put("host", params.get("host"));
    metadataMap.put("status", params.get("status"));
    metadataMap.put("version", params.get("version"));
    metadataMap.put("uptime", params.get("uptime"));
    metadataMap.put("cpuSpeed", params.get("cpuSpeed"));
    metadataMap.put("numFreeCPUs", params.get("numFreeCPUs"));
    metadataMap.put("numTotalCPUs", params.get("numTotalCPUs"));
    metadataMap.put("freeMemory", params.get("freeMemory"));
    metadataMap.put("procSummary", params.get("procSummary"));
    metadataMap.put("numSuccessJobs", params.get("numSuccessJobs"));
    metadataMap.put("numJobs", params.get("numJobs"));
    saveToDisk();
    hasUpdated = true;
    ResponseParams resp = new ResponseParams(true, "Status set");  
    if(kill) { resp.put("kill", "true"); kill = false; }
    if(terminate) { resp.put("terminate", "true"); terminate = false; }
    return resp;
  }

  public ResponseObject getJob(RequestParams params) throws MyException {
    lastGetJobTime = new Date().getTime();
    ReadyExecView readyExecView = ((WorkerViewDB)parent.parent).readyExecView;
    if(isDisabled()) return new ResponseParams(true, "Disabled");
    if(!isQualified()) return new ResponseParams(true, "Not qualified");

    ExecItem execItem = readyExecView.popAReadyExecItem(getFreeMemory());
    if(execItem == null)
      return new ResponseParams(true, "No ready exec items for you");
    execItem.assignToWorker(this);
    addItem(execItem); saveToDisk();
    ResponseParams resp = new ResponseParams("Got a job");
    resp.put("nice", Math.max(execItem.getNice(), getNice()));
    resp.put("workingDir", execItem.getWorkingDir());
    resp.put("command", execItem.getCommand());
    return resp;
  }

  protected Value getIntrinsicFieldValue(String fieldName) throws MyException {
    if(fieldName.equals("procSummary")) {
      // Because procSummary is usually several lines and same for all workers on a machine,
      // we just want to display it once for the first worker (with name ending in -1)
      // Otherwise, suppress it
      if(name.matches(".+-\\d+$") && !name.endsWith("-1"))
        return new Value("(suppressed)");
    }
    if(fieldName.equals("sinceLastMessageTime")) return getSinceLastMessageTimeValue();
    if(fieldName.equals("qualified")) return new Value(""+isQualified());
    return super.getIntrinsicFieldValue(fieldName);
  }

  public ResponseObject handleOperation(OperationRP req, Permissions perm) throws MyException {
    String op = req.op;
    if(op.equals("setStatus")) return setStatus(req);
    if(op.equals("getJob"))    return getJob(req);
    if(op.equals("kill")) {
      kill = true;
      return new ResponseParams("Set the kill flag; will take effect when worker contacts server");
    }
    if(op.equals("terminate")) {
      terminate = true;
      return new ResponseParams("Set the terminate flag; will take effect when worker contacts server");
    }
    return super.handleOperation(req, perm);
  }

  protected boolean isView() { return true; }
  protected Item newItem(String name) throws MyException { throw MyExceptions.unsupported; }

  // Is this better than that by a lot?
  // Need to dominate in memory and # cpus.
  boolean muchBetter(WorkerItem that) { return cpuMuchBetter(that) && memMuchBetter(that); }
  boolean cpuMuchBetter(WorkerItem that) {
    int n1 = this.getNumFreeCPUs();
    int n2 = that.getNumFreeCPUs();
    return n1 > n2;
  }
  boolean memMuchBetter(WorkerItem that) {
    long n1 = this.getFreeMemory();
    long n2 = that.getFreeMemory();
    return n1 > n2 * 2; // Has to be better by 2x
  }
}
