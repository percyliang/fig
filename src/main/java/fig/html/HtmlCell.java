package fig.html;

import java.io.*;
import java.util.*;

public class HtmlCell extends HtmlElement {
  public HtmlCell(Object value) {
    this.value = value == null ? "" : value.toString();
    this.bold = false;
  }

  public String getTag() { return "td"; }

  protected void renderInnerHTML(StringBuilder sb,
      Map<String, Map<String, String>> inheritedAttributesSet) {
    if(bold) sb.append("<b>");
    sb.append(value);
    if(bold) sb.append("</b>");
  }

  public String value;
  public boolean bold;
}
