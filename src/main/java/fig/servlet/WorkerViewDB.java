package fig.servlet;

import java.io.*;
import java.util.*;
import fig.basic.*;

/**
 * Items are worker views (a worker view is a group of workers).
 * sourcePath = the workers directory.
 */
public class WorkerViewDB extends Item {
  // View of all the execution items ready to be run (thunks)
  public final ReadyExecView readyExecView;
  public final WorkerView workerView;

  public WorkerViewDB(Item parent, String name, String workersDir) {
    super(parent, name, null);
    IOUtils.createNewDirIfNotExistsEasy(workersDir);
    addItem(this.readyExecView = new ReadyExecView(this, "readyExecs", 
        new File(workersDir, "readyExecs").toString()));
    addItem(this.workerView = new WorkerView(this, "workers", 
        new File(workersDir, "workers").toString()));
  }

  public void update(UpdateSpec spec, UpdateQueue.Priority priority) throws MyException {
    //updateItemsFromFile(spec,
        //ListUtils.newList(readyExecView),
        //Collections.EMPTY_LIST);
    super.update(spec, priority);
    updateChildren(spec, priority);
  }

  protected boolean isView() { return true; }
  //protected Item newItem(String name) throws MyException {
    //return new WorkerView(this, name, new File(sourcePath, name).toString());
  //}
  protected Item newItem(String name) throws MyException { throw MyExceptions.unsupported; }
  protected Item handleToItem(String handle) throws MyException { return null; }
  protected String itemToHandle(Item item) throws MyException { return null; }

  protected String getDescription() {
    return "Manage the worker machines that run executions.";
  }
}
