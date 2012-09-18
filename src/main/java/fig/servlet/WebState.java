package fig.servlet;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import fig.basic.*;
import fig.html.*;

public class WebState {
  public WebState(HttpServletRequest request, HttpServletResponse response) throws IOException {
    this.request = request;
    this.response = response;
    this.session = request.getSession(true);
    this.params = new RequestParams(request);
    WebState.verboseLogs("============================================================");
  }

  public OutputStream getOutputStream() throws IOException {
    return response.getOutputStream();
  }
  public PrintWriter getWriter() throws IOException {
    return response.getWriter(); // Assume encoding is right
    // Argh, can't change the encoding
    //return new PrintWriter(new OutputStreamWriter(response.getOutputStream(), Utils.theCharEncoding));
  }

  public void setRawOutput() {
  }
  public void setPlainOutput() {
    response.setContentType("text/plain; charset=" + CharEncUtils.getCharEncoding());
  }
  public void setHtmlOutput() {
    response.setContentType("text/html; charset=" + CharEncUtils.getCharEncoding());
  }

  public void initOutput() throws IOException {
    setHtmlOutput();
    this.hw = new HtmlWriter(getWriter());
  }
  public void endOutput() {
    hw.flush();
  }

  // HACK, so we can write a log from anywhere
  public static void setServlet(HttpServlet servlet) {
    WebState.servlet = servlet;
  }
  public synchronized static void logs(Object arg) {
    if(!loadedNew) {
      servlet.log("NEWLY LOADED JVM");
      loadedNew = true;
    }
    servlet.log("" + arg);
  }
  public synchronized static void logs(String format, Object... args) {
    logs(String.format(format, args));
  }
  public synchronized static void verboseLogs(Object o) {
    if(verbose) logs(o);
  }
  public synchronized static void verboseLogs(String format, Object... args) {
    if(verbose) logs(format, args);
  }

  public HtmlWriter hw;
  public RequestParams params;
  public HttpServletRequest request;
  public HttpServletResponse response;
  public HttpSession session;

  // Hack: using static variables so we can refer to these from anywhere
  private static HttpServlet servlet;
  private static boolean loadedNew;
  public static boolean verbose, logUpdates, logWorkers;
}
