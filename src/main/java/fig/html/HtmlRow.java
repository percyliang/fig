package fig.html;

import java.io.*;
import java.util.*;

public class HtmlRow extends HtmlElement {
  public HtmlRow() { }
  public HtmlRow(boolean isHeader, List<? extends Object> values) {
    setIsHeader(isHeader);
    addCells(values);
  }

  public void setIsHeader(boolean isHeader) { this.isHeader = isHeader; }
  public HtmlCell addCell(HtmlCell cell) { cells.add(cell); return cell; }
  public HtmlCell addCell(String value) {
    HtmlCell cell = new HtmlCell(value);
    cells.add(cell);
    return cell;
  }
  public void addCells(List<? extends Object> values) {
    for(Object value : values) addCell(new HtmlCell(value));
  }

  protected void renderInnerHTML(StringBuilder sb, 
      Map<String, Map<String, String>> inheritedAttributesSet) {
    for(HtmlCell cell : cells) {
      if(isHeader) cell.bold = true;
      cell.render(sb, inheritedAttributesSet);
    }
  }

  public String getTag() { return "tr"; }

  public HtmlCell getCell(int i) { return cells.get(i); }

  private boolean isHeader;
  private List<HtmlCell> cells = new ArrayList<HtmlCell>();
}
