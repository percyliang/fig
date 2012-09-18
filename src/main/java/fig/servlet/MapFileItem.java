package fig.servlet;

import java.util.*;

public class MapFileItem extends FileItem {
  public MapFileItem(Item parent, String name, String sourcePath) {
    super(parent, name, sourcePath);
  }

  public FieldListMap getMetadataFields() {
    FieldListMap fields = new FieldListMap();
    for(Map.Entry<String,String> e : metadataMap.entrySet())
      fields.add(e.getKey(), e.getKey(), e.getValue()).mutable = true;
    return fields;
  }

  public void update(UpdateSpec spec, UpdateQueue.Priority priority) throws MyException {
    super.update(spec, priority);
    loadFromDisk();
  }
}
