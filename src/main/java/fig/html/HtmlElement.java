package fig.html;

import java.io.*;
import java.util.*;

import fig.basic.*;

public abstract class HtmlElement {
  public HtmlElement() { }

  public HtmlElement setId(String id) { return setAttr("id", id); }

  public HtmlElement setAttr(String key) { return setAttr(key, ""); }
  public HtmlElement setAttr(String key, Object value) {
    if(attributes == null)
      attributes = new HashMap<String, String>(2);
    attributes.put(key, StrUtils.toString(value));
    return this;
  }
  public String getAttr(String key) {
    return MapUtils.get(attributes, key, null);
  }

  public abstract String getTag();
  protected abstract void renderInnerHTML(StringBuilder sb,
      Map<String, Map<String, String>> inheritedAttributesSet);

  public void render(StringBuilder sb, Map<String, Map<String, String>> inheritedAttributesSet) {
    // Opening tag
    sb.append("<"); sb.append(getTag());
    if(attributes != null) { // Put our attributes first
      for(Map.Entry<String, String> e : attributes.entrySet())
        renderAttr(sb, e);
    }
    Map<String, String> inheritedAttributes = MapUtils.get(inheritedAttributesSet, getTag(), null);
    if(inheritedAttributes != null) { // Then put inherited attributes that we don't have
      for(Map.Entry<String, String> e : inheritedAttributes.entrySet())
        if(attributes == null || !attributes.containsKey(e.getKey()))
          renderAttr(sb, e);
    }
    sb.append(">");

    // Body
    renderInnerHTML(sb, inheritedAttributesSet);

    // Closing tag
    sb.append("</"); sb.append(getTag()); sb.append(">");
    sb.append("\n");
  }

  public void render(StringBuilder sb) {
    render(sb, new HashMap<String, Map<String, String>>());
  }
  public String render() {
    StringBuilder sb = new StringBuilder();
    render(sb);
    return sb.toString();
  }

  private void renderAttr(StringBuilder sb, Map.Entry<String, String> e) {
    sb.append(' ');
    sb.append(e.getKey());
    if(!StrUtils.isEmpty(e.getValue())) {
      sb.append("=\"");
      sb.append(e.getValue());
      sb.append("\"");
    }
  }

  private Map<String, String> attributes;
}
