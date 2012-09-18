package fig.servlet;

import java.io.*;
import java.util.*;
import fig.basic.*;

/**
 * Contains a list of all the workers.
 * sourcePath = directory with all the worker files.
 */
public class WorkerView extends Item {
  public WorkerView(Item parent, String name, String sourcePath) {
    super(parent, name, sourcePath);
    IOUtils.createNewDirIfNotExistsEasy(sourcePath);
  }

  public FieldListMap getItemsFields() {
    return new WorkerItem(null, null, null).getMetadataFields();
  }

  // Create items on the fly
  protected Item getItem(String name) throws MyException {
    return getItemOrNewAdd(name);
  }

  protected String fileSourcePath() { return null; }
  public void update(UpdateSpec spec, UpdateQueue.Priority priority) throws MyException {
    updateItemsFromDir(-1, FileUtils.TraverseSpec.matchExt("index"), true);
    updateChildren(spec, priority);
  }

  protected boolean isView() { return true; }
  protected Item newItem(String name) throws MyException {
    return new WorkerItem(this, name, childNameToIndexSourcePath(name));
  }
  protected String tableType() { return "WorkerView"; }
  protected String getDescription() { return "List of worker machines"; }
  protected Pair<String,Boolean> getDefaultSortSpec() { return new Pair("name", false); }

  // We have a job.  Which worker is qualified to do it?
  // We want to assign jobs to workers with higher priority (manually specified)
  // or with more resources (CPU, memory).
  public boolean isQualified(WorkerItem queryItem) {
    // Find best priority of all the workers
    int bestPriority = Integer.MAX_VALUE;
    for(Item _item : items.values()) {
      WorkerItem item = (WorkerItem)_item;
      if(!item.isSeekingJob()) continue;
      bestPriority = Math.min(bestPriority, item.getPriority());
    }
    if(bestPriority != queryItem.getPriority()) return false;

    // We are one of the workers with the highest priority
    // Now make sure we're among the best choices.
    for(Item _item : items.values()) {
      WorkerItem item = (WorkerItem)_item;
      if(!item.isSeekingJob()) continue;
      if(bestPriority != item.getPriority()) continue;
      if(item.muchBetter(queryItem)) return false;
    }

    return true;
  }
}
