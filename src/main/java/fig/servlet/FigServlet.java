package fig.servlet;

import java.io.*;
import java.util.*;
import java.lang.Thread;
import javax.servlet.*;
import javax.servlet.http.*;

import fig.basic.*;
import fig.html.*;

public class FigServlet extends HttpServlet {
  private static final long serialVersionUID = 42;

  private String prependFile;
  private RootItem rootItem;
  private Authenticator authenticator;
  private UpdaterThread updaterThread;

  private static class UpdaterThread extends Thread {
    public UpdaterThread(RootItem rootItem) {
      this.rootItem = rootItem;
      this.updateQueue = new UpdateQueue();
      this.done = false;
      this.sleepInterval = initSleepInterval;
    }

    // Go through all the items in the queue and update them.
    public void update() {
      if(updateQueue.queueSize() == 0) return;
      try {
        // Do a round of updating.
        WebState.logs("UpdaterThread.update(): " + updateQueue);
        while(true) {
          Pair<Item, UpdateQueue.Priority> pair = updateQueue.dequeue();
          if(pair == null) break;
          pair.getFirst().update(getUpdateSpec(), pair.getSecond());
        }
        updateQueue.clearEnqueued();
        WebState.logs("UpdaterThread.update(): finished");
      } catch(MyException e) {
        WebState.logs("UpdaterThread.update() failed: " + Utils.getStackTrace(e));
      }
    }

    public void run() {
      WebState.logs("UpdaterThread.run(): begin");
      while(!done) {
        update();
        Utils.sleep(sleepInterval);
        // Gradually increase the sleep interval
        // This would be useful if we kept track of something to
        // add to the update queue, but right now, we don't have anything.
        sleepInterval = (int)(sleepInterval * sleepGrowthFactor);
      }
      WebState.logs("UpdaterThread.run(): done");
    }

    public void terminate() { this.done = true; }
    public void hit() { sleepInterval = initSleepInterval; interrupt(); }
    public UpdateSpec getUpdateSpec() { return new UpdateSpec(updateQueue); }

    private RootItem rootItem;
    private UpdateQueue updateQueue;
    private int sleepInterval; // In milliseconds
    private boolean done;
    
    private int initSleepInterval = 1000000000;
    //private int initSleepInterval = 1000;
    private double sleepGrowthFactor = 1.2;
  }

  public void init() {
    // Initialize the servlet: load some properties
    String propertiesFile = getServletName() + ".properties";
    ServletContext context = getServletContext();
    Properties properties = Utils.loadProperties(context.getRealPath(propertiesFile));

    // Set character encoding
    CharEncUtils.setCharEncoding(properties.getProperty("encoding"));

    // Global variables
    WebState.setServlet(this);
    WebState.logs(getServletName() + ".init()");

    // Set variables
    this.prependFile = context.getRealPath(properties.getProperty("prependFile"));
    String varDir = parseVarDir(properties.getProperty("varDir"));
    this.rootItem = new RootItem(varDir.startsWith("/") ? varDir : context.getRealPath(varDir));
    this.authenticator = new Authenticator(rootItem);

    // Start the updator thread
    this.updaterThread = new UpdaterThread(rootItem);
    this.updaterThread.start();
  }

  private static String parseVarDir(String s) {
    return s.replace("HOSTNAME", SysInfoUtils.getShortHostName());
  }

  public void destroy() {
    WebState.logs(getServletName() + ".destroy()");
    this.updaterThread.terminate();
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    doGetPost(request, response);
  }
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    doGetPost(request, response);
  }
  public void doGetPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    WebState state = new WebState(request, response);
    Permissions perm = authenticator.getPermissions(state.request);
    printInfo(state, perm);

    String mode = state.params.get("mode", "display");
    try {
           if(mode.equals("auth"))    givePermissions(state);
      else if(mode.equals("op"))      handleOperation(state, perm);
      else if(mode.equals("display")) displayStuff(state, perm);
      else throw new MyException("Invalid mode: " + mode);
    } catch(MyException e) {
      new ResponseParams(e).dump(state);
    }

    updaterThread.hit(); // Update the items as a result of this operation

    WebState.verboseLogs("doGetPost(): finished");
  }

  protected void printInfo(WebState state, Permissions perm) {
    // Print some info
    //WebState.logs("Info:");
    //WebState.logs("  HOST: " + state.request.getRemoteHost());
    //WebState.logs("  USER: " + state.request.getUserPrincipal() + " " + state.request.getRemoteUser());
    //WebState.logs("  PERM: " + perm);
    //WebState.logs("  QUERY: " + state.request.getQueryString());
    //state.params.dumpToLog();
    String op = state.params.get("op");
    // Avoid high frequency
    boolean involveWorkers = "setStatus".equals(op) || "getJob".equals(op);
    if (WebState.verbose)
      WebState.logs("QUERY: " + state.request.getQueryString());
  }

  private void givePermissions(WebState state) throws MyException, IOException {
    String auth = state.params.get("auth");
    if(auth == null)
      throw new ArgumentException("No authentication string specified");

    boolean ok = authenticator.givePermissions(auth, state.response);
    new ResponseParams(ok, "Authenticated", "Invalid authentication").dump(state);
  }

  private void handleOperation(WebState state, Permissions perm, OperationRP req, Item item) throws MyException, IOException {
    ResponseObject resp = item.handleOperation(req, perm);
    resp.dump(state);
  }
  private void handleOperation(WebState state, Permissions perm, OperationRP req) throws MyException, IOException {
    try {
      Item item = rootItem.trailToItem(req.trail);
      handleOperation(state, perm, req, item);
    } catch(NameNotFoundException e) {
      // This could happen if we just restarted the servlet and
      // it hasn't loaded this trail yet.
      // So we throw an error for now, but queue the trail
      updaterThread.updateQueue.enqueue(e.item, UpdateQueue.Priority.HIGH);
      throw e;
    }
  }
  private void handleOperation(WebState state, Permissions perm) throws MyException, IOException {
    OperationRP req = new OperationRP(state.params, updaterThread.getUpdateSpec());
    handleOperation(state, perm, req);
  }

  private void displayStuff(WebState state, Permissions perm) throws MyException, IOException {
    state.initOutput();
    if(new File(prependFile).exists()) state.hw.writeFile(prependFile);
    //state.hw.begin("fig", "onload='onLoad()' onkeypress='onKeyPress(event)'", false);
    state.hw.begin("fig", "onload='onLoad()' onkeydown='onKeyPress(event)'", false);

    if(state.params.containsKey("op"))
      handleOperation(state, perm);
    else {
      OperationRP req = new OperationRP("getItemsTable", updaterThread.getUpdateSpec());
      req.put("name", "root");
      handleOperation(state, perm, req, rootItem);
    }

    state.hw.end();
    state.endOutput();
  }
}
