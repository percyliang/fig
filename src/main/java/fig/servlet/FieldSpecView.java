package fig.servlet;

import java.util.*;
import fig.basic.*;

/**
 * The master list of field specs.
 * sourcePath is the directory containing all the paths.
 */
public class FieldSpecView extends Item {
  public final FieldSpecItem defaultExecItemFieldSpecItem;
  public final FieldSpecItem defaultExecViewFieldSpecItem;

  public FieldSpecView(Item parent, String name, String sourcePath) {
    super(parent, name, sourcePath);
    addItem(this.defaultExecItemFieldSpecItem =
      new FieldSpecItem(this, "(execs)", null,
        ExecItem.createDefaultFields()));
    addItem(this.defaultExecViewFieldSpecItem =
      new FieldSpecItem(this, "(views)", null,
        new GroundedExecView(null, null, null).getMetadataFields()));
    IOUtils.createNewDirIfNotExistsEasy(sourcePath);
    IOUtils.createNewFileIfNotExistsEasy(fileSourcePath());
  }

  public FieldListMap getItemsFields() {
    return new FieldSpecItem(null, null, null).getMetadataFields();
  }

  protected String getDescription() {
    return "Field specifications (determines what table columns to display).";
  }

  public void update(UpdateSpec spec, UpdateQueue.Priority priority) throws MyException {
    updateItemsFromFile(spec,
        ListUtils.newList(defaultExecItemFieldSpecItem, defaultExecViewFieldSpecItem),
        Collections.EMPTY_LIST);
    updateChildren(spec, priority);
  }

  protected boolean isView() { return true; }
  protected Item newItem(String name) {
    return new FieldSpecItem(this, name, childNameToIndexSourcePath(name));
  }
  protected String itemToHandle(Item item) throws MyException {
    if(item == defaultExecItemFieldSpecItem ||
       item == defaultExecViewFieldSpecItem)
      return null;
    return super.itemToHandle(item);
  }
}
