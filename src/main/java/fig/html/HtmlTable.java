package fig.html;

import java.io.*;
import java.util.*;

public class HtmlTable extends HtmlElement {
  public HtmlTable() { }
  public HtmlTable(String id) {
    setAttr("id", id);
  }

  public void addRow(HtmlRow row) { rows.add(row); }

  public String getTag() { return "table"; }

  protected void renderInnerHTML(StringBuilder sb, 
      Map<String, Map<String, String>> inheritedAttributesSet) {
    inheritedAttributesSet.put("td", cellAttributes);
    for(HtmlRow row : rows) {
      row.render(sb, inheritedAttributesSet);
    }
  }

  public void setNoWrap(boolean noWrap) {
    if(noWrap) cellAttributes.put("nowrap", null);
    else cellAttributes.remove("nowrap");
  }

  private List<HtmlRow> rows = new ArrayList<HtmlRow>();
  private Map<String, String> cellAttributes = new HashMap<String, String>();
}
