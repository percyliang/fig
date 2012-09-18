package fig.servlet;

import java.io.*;
import java.util.*;
import fig.basic.*;

/**
 * Represents a single file on disk.
 */
public class FileItem extends Item {
  public enum FileType { file, dir };

  protected FileType fileType;
  protected long fileSize;
  protected long lastModifiedTime;

  public FileItem(Item parent, String name, String sourcePath) {
    super(parent, name, sourcePath);
    this.fileType = null;
    this.fileSize = -1;
    this.lastModifiedTime = -1;
  }

  protected Value getIntrinsicFieldValue(String fieldName) throws MyException {
    if(fieldName.equals("type"))         return new Value(""+fileType);
    if(fieldName.equals("size"))         return getFileSizeValue();
    if(fieldName.equals("lastModified")) return getLastModifiedTimeValue();
    return super.getIntrinsicFieldValue(fieldName);
  }

  public FieldListMap getMetadataFields() {
    FieldListMap fields = new FieldListMap();
    fields.add("type",         "File type");
    fields.add("size",         "Size of file").numeric = true;
    fields.add("lastModified", "Time of last modification").numeric = true;
    return fields;
  }

  public void updateShallow(UpdateSpec spec) throws MyException {
    File path = new File(sourcePath);
    if(path.exists()) {
           if(path.isDirectory()) this.fileType = FileType.dir;
      else if(path.isFile())      this.fileType = FileType.file;
      else                        this.fileType = null;
      this.fileSize = path.length();
      this.lastModifiedTime = path.lastModified();
    }
    else {
      this.fileType = null;
      this.fileSize = -1;
      this.lastModifiedTime = -1;
    }
  }

  public void update(UpdateSpec spec, UpdateQueue.Priority priority) throws MyException {
    updateShallow(spec);
  }

  public ResponseObject handleOperation(OperationRP req, Permissions perm) throws MyException {
    String op = req.op;
    File path = new File(sourcePath);

    if(op.equals("summary")) {
      return new ResponseParams("Will be supported soon");
    }

    if(op.equals("cmd")) {
      perm.checkCanExecute();
      // Execute the command with this file as the standard input
      String cmd = req.getReq("cmd");
      cmd = "cat " + path + " | " + cmd;
      WebState.logs("Executing: " + cmd);
      try {
        Process proc = Utils.openSystem(cmd);
        return new ResponseStream(proc.getInputStream()); // Get results
      } catch(IOException e) {
        throw new MyException("Error executing: " + e);
      }
    }

    if(op.equals("download")) {
      try {
        if(!path.getCanonicalPath().toString().startsWith(perm.accessRootDir.toString()))
          throw new MyException("Can only access " + perm.accessRootDir);

        //String mimeType = new MimetypesFileMap().getContentType(path);
        //WebState.logs("Downloading " + path + " with mime type " + mimeType);

        return new ResponseStream(new FileInputStream(path));
      } catch(IOException e) {
        throw new FileException(e);
      }
    }

    return super.handleOperation(req, perm);
  }

  protected boolean isView() { return false; }
  protected Item newItem(String name) throws MyException { throw MyExceptions.unsupported; }

  public Value getFileSizeValue() {
    if(fileSize == -1) return new Value(null);
    return new Value(Fmt.bytesToString(fileSize), ""+fileSize);
  }
  public Value getLastModifiedTimeValue() {
    if(lastModifiedTime == -1) return new Value(null);
    return new Value(Fmt.formatEasyDateTime(lastModifiedTime), ""+lastModifiedTime);
  }
  public Value getSinceLastModifiedTimeValue() {
    if(lastModifiedTime == -1) return new Value(null);
    return getSinceLastModifiedTimeValue(getSinceLastModifiedTime());
  }
  public static Value getSinceLastModifiedTimeValue(long lastModifiedTime) {
    long time = getSinceLastModifiedTime(lastModifiedTime);
    return new Value(new StopWatch(time).toString(), ""+time);
  }

  public long getFileSize() { return fileSize; }
  public long getLastModifiedTime() { return lastModifiedTime; }
  public long getSinceLastModifiedTime() { return getSinceLastModifiedTime(lastModifiedTime); }
  public static long getSinceLastModifiedTime(long lastModifiedTime) { return new Date().getTime() - lastModifiedTime; }
}
