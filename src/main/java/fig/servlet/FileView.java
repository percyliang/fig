package fig.servlet;

import java.io.*;
import java.util.*;
import fig.basic.*;

/**
 * Listing of a directory.
 */
public class FileView extends FileItem {
  private FileFactory factory;
  private boolean recursive;
  // Whether we should load all the files
  private boolean listAll;

  public FileView(Item parent, String name, String sourcePath,
      FileFactory factory, boolean recursive, boolean listAll) {
    super(parent, name, sourcePath);
    this.factory = factory;
    this.recursive = recursive;
    this.listAll = listAll;
  }

  protected boolean isView() { return true; }
  protected Item newItem(String name) throws MyException { 
    File file = new File(sourcePath, name);
    if(!file.exists()) throw new MyException("Doesn't exist: " + file);
    return factory.newFileItem(this, name, file.toString());
  }

  public FieldListMap getItemsFields() {
    return new FileItem(null, null, null).getMetadataFields();
  }

  protected String fileSourcePath() { return null; }
  public void update(UpdateSpec spec, UpdateQueue.Priority priority) throws MyException {
    super.update(spec, priority);
    if(listAll)
      updateItemsFromDir(recursive ? -1 : 1, FileUtils.TraverseSpec.allowAll(), false);
    // Update basic information about files in this directory
    for(Item item : items.values())
      ((FileItem)item).updateShallow(spec);
  }

  protected String tableType() { return "FileView"; }
  protected String getDescription() {
    return "Browse the file system.";
  }
}
