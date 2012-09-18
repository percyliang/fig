package fig.servlet;

import java.io.*;
import java.util.*;
import fig.basic.*;

/**
 * List of executions that are ready to be executed.
 * Doesn't allow duplicates.
 */
class ReadyExecView extends Item {
  public ReadyExecView(Item parent, String name, String sourcePath) {
    super(parent, name, sourcePath);
    IOUtils.createNewFileIfNotExistsEasy(sourcePath);
  }

  public void update(UpdateSpec spec, UpdateQueue.Priority priority) throws MyException {
    super.update(spec, priority);
    // Keep only the exec items that are still thunks
    OrderedMap<String,Item> newItems = new OrderedMap();
    for(Item item : items.values()) {
      if(!(item instanceof ExecItem)) continue;
      if(((ExecItem)item).isThunk())
        addItem(newItems, item);
    }
    items = newItems; // Fast switch!
  }

  // Return an exec item with the smallest priority
  // Only consider exec items with memory requirements less than freeMemory (in bytes)
  public ExecItem popAReadyExecItem(long freeMemory) throws MyException {
    // Find best item
    ExecItem bestExecItem = null;
    int bestPriority = Integer.MAX_VALUE;
    for(Item _execItem : items.values()) {
      if(!(_execItem instanceof ExecItem)) continue;
      ExecItem execItem = (ExecItem)_execItem;
      if(!execItem.isThunk()) continue;
      if(execItem.getReqMemory() > freeMemory/(1024*1024)) continue;
      if(execItem.getPriority() < bestPriority) {
        bestPriority = execItem.getPriority();
        bestExecItem = execItem;
      }
    }
    // Remove it
    if(bestExecItem != null) {
      WebState.logs("ReadyExecView: popped " + bestExecItem);
      OrderedMap<String,Item> newItems = new OrderedMap();
      for(Item item : items.values()) {
        if(item != bestExecItem)
          addItem(newItems, item);
      }
      items = newItems; // Fast switch!
      saveToDisk();
    }

    return bestExecItem;
  }

  protected void addItem(Item item) {
    // Don't add execution items again if they have already been queued
    if(item instanceof ExecItem) {
      ExecItem execItem = (ExecItem)item;
      if(containsItem(execItem)) return; // Redundant
      // Only queue thunks that haven't been queued before
      try {
        if(!execItem.isThunk() || execItem.getThunkHasBeenQueued()) return;
        execItem.setThunkHasBeenQueued(true);
      } catch(MyException e) {
        // Don't queue because we can't remember that we already queued it.
        WebState.logs("ReadyExecView: Unable to save thunkHasBeenQueued", item);
        return;
      }
      WebState.logs("ReadyExecView: queueing %s", item);
    }
    super.addItem(item);
  }

  protected FieldListMap getItemsFields() { return ExecItem.createThunkFields(); }
  protected boolean isView() { return true; }
  public Item newItem(String name) throws MyException { throw MyExceptions.unsupported; }
  protected String getDescription() { return "List of executions ready to be run (thunks)"; }
}
