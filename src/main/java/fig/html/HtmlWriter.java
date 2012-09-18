package fig.html;

import java.io.*;
import java.util.*;
import fig.basic.*;

public class HtmlWriter {
  public HtmlWriter(PrintWriter out) {
    this.out = out;
  }

  public void includeScript(String... scriptFiles) {
    for(String file : scriptFiles)
      out.println("<script src=\""+file+"\"></script>");
  }

  public void begin(String title) { begin(title, null, true); }
  public void begin(String title, String bodyAttr, boolean printHeader) {
    out.println("<html>");
    out.println("<head>");
    out.println("<title>" + title + "</title>");
    if(printHeader)
      out.println("<h1>" + title + "</h1>");
    out.println("</head>");

    out.println("<body" + (bodyAttr != null ? " "+bodyAttr : "") + ">");
  }

  public void end() {
    out.println("</body>");
    out.println("</html>");
  }

  public static String redirect(String url, int time) {
    return String.format("<meta http-equiv=\"refresh\" content=\"%s; url=%s\">",
        time, url);
  }

  public static String msgLoc(String loc) {
    // A place to put messages; need to put in a table for some reason
    return "<table><tr><td><div id=\"" + loc + "\"/></td></tr></table>";
  }

  public static String msg(String text, String loc, String msg, boolean hide) {
    String hideStr = ""; // Whether to hide text when mouse moves off
    if(hide) hideStr = " onMouseOut=\"hidetext('"+loc+"')\"";
    return String.format(
      "<div onMouseOver=\"showtext('%s','%s')\"%s>%s</div>",
      loc, msg, hideStr, text);
  }

  public static String color(String text, String color) {
    return "<font color=\"" + color + "\">" + text + "</font>";
  }
  public static String link(String text, String url) {
    return "<a href=\"" + url + "\">" + text + "</a>";
  }
  public static String tag(String tag, String text) {
    return "<" + tag + ">" + text + "</" + tag + ">";
  }
  public static String bold(String text) { return tag("b", text); }
  public static String underline(String text) { return tag("u", text); }
  public static String img(String url) { return "<img src=\"" + url + "\"/>"; }

  /*public void dumpFile(String file) {
    out.println("<pre>\n");
    try {
      BufferedReader r = Utils.openIn(file);
      String line;
      int numLines = 0;
      while((line = r.readLine()) != null) {
        out.println(line);
        numLines++;
        if(numLines >= 5000) {
          out.println("... (truncated) ...");
          break;
        }
      }
    } catch(IOException e) {
      out.println("Failed to read: " + e);
    }
    out.println("</pre>\n");
  }*/

  public void writeFile(String file) {
    try {
      BufferedReader in = IOUtils.openIn(file);
      String line;
      while((line = in.readLine()) != null)
        out.println(line);
    } catch(IOException e) {
      printLine("Failed to read " + file + ": " + e);
    }
  }

  public void printBlock(String id, String title, String contents) {
    out.println("<div id='"+id+"'>");
    out.println("<div>");
    out.println(bold(title));
    out.println("</div>");
    out.println("<div>");
    out.println(contents);
    out.println("</div>");
    out.println("</div>");
  }

  public PrintWriter getWriter() { return out; }
  public void println(Object obj) { out.println(obj); }
  public void printLine(Object obj) { out.println(obj + "<br>"); }
  public void flush() { out.flush(); }
  public void newParagraph() { out.println("<p>"); }

  PrintWriter out;
}
