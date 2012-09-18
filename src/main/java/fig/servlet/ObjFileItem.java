package fig.servlet;

import java.io.*;
import java.util.*;
import fig.basic.*;

// For loading objects using a custom class loader
class CustomObjectInputStream extends ObjectInputStream {
  private ClassLoader loader;
  public CustomObjectInputStream(ClassLoader loader, String path) throws IOException {
    super(new FileInputStream(path));
    this.loader = loader;
  }

  protected Class resolveClass(ObjectStreamClass osc) throws ClassNotFoundException {
    return loader.loadClass(osc.getName());
  }
}

public class ObjFileItem extends FileItem {
  protected Object obj;

  public ObjFileItem(Item parent, String name, String sourcePath) {
    super(parent, name, sourcePath);
  }

  protected Value getIntrinsicFieldValue(String name) throws MyException {
    if(name.equals("class"))
      return obj == null ? new Value(null) : new Value(obj.getClass().toString());
    return super.getIntrinsicFieldValue(name);
  }

  public FieldListMap getMetadataFields() {
    FieldListMap fields = super.getMetadataFields();
    fields.add("class", "Class name");
    return fields;
  }

  public void load() throws MyException {
    load(null);
  }

  public void load(ClassLoader loader) throws MyException {
    if(obj != null) return; // Already loaded
    if(fileSize > 10*1024*1024)
      throw new MyException("File too big to load: " + Fmt.bytesToString(fileSize));
    WebState.logs("Loading object " + sourcePath + ", class loader = " + loader);
    try {
      if(loader == null)
        obj = IOUtils.readObjFile(sourcePath);
      else
        obj = new CustomObjectInputStream(loader, sourcePath).readObject();
    } catch(ClassNotFoundException e) {
      throw new MyException(e.getMessage());
    } catch(IOException e) {
      throw new FileException(e);
    }
  }

  public ResponseObject handleOperation(OperationRP req, Permissions perm) throws MyException {
    String op = req.op;

    if(op.equals("load")) {
      load();
      return new ResponseParams("Loaded.");
    }
    return super.handleOperation(req, perm);
  }

  public Object getObj() { return obj; }
}
