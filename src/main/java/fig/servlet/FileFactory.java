package fig.servlet;

import java.io.*;

public class FileFactory {
  public FileItem newFileItem(Item parent, String name, String path) {
    if(new File(path).isDirectory())
      return new FileView(parent, name, path, this, false, true);

    String ext = FileUtils.getExt(path);
    if(ext.equals("map")) return new MapFileItem(parent, name, path);
    if(ext.equals("bin")) return new ObjFileItem(parent, name, path);
    return new FileItem(parent, name, path);
  }
}
