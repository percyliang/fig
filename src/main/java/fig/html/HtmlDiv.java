package fig.html;

import java.io.*;
import java.util.*;

public class HtmlDiv extends HtmlElement {
  public HtmlDiv() {
    this.text = "";
    this.elements = new ArrayList<HtmlElement>();
  }

  public HtmlDiv(String text) {
    this.text = text;
    this.elements = new ArrayList<HtmlElement>();
  }

  public HtmlDiv(HtmlElement... elements) {
    this.text = "";
    this.elements = new ArrayList<HtmlElement>(Arrays.asList(elements));
  }

  public void add(HtmlElement el) {
    elements.add(el);
  }

  protected void renderInnerHTML(StringBuilder sb, 
      Map<String, Map<String, String>> inheritedAttributesSet) {
    sb.append("\n");
    for(HtmlElement element : elements) {
      element.render(sb, inheritedAttributesSet);
      sb.append("\n");
    }
    sb.append(text);
  }

  public String getTag() { return "div"; }

  private List<HtmlElement> elements;
  private String text;
}
