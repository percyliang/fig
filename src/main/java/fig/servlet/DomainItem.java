package fig.servlet;

import java.io.*;
import java.util.*;
import java.net.*;
import fig.basic.*;

/**
 * A domain encompases one research project, and includes:
 *  - A single set of executions
 *  - A single set of specifications
 *  - A single set of baskets.
 *
 * Example:
 * domainDir = /home/eecs/pliang/research/cortex/run/state
 */
public class DomainItem extends Item {
  public BasketView basketView;
  public FieldSpecView fieldSpecView;
  public ExecViewDB execViewDB;
  private BasketFactory basketFactory;
  // Keep track of the parameters used to create the children (basket, views, etc.)
  // so if they change, we can recreate them
  private String domainDir, basketFactoryClassName, classpath;
  private ClassLoader classLoader;

  public DomainItem(Item parent, String name, String sourcePath) {
    super(parent, name, sourcePath);
    IOUtils.createNewFileIfNotExistsEasy(sourcePath);
  }

  protected boolean isHidden() { return Boolean.parseBoolean(metadataMap.get("isHidden")); }
  public FieldListMap getItemsFields() { return countDescriptionFields; }

  protected void loadFromDisk() throws MyException {
    // Load the file so we can get the domain directory
    super.loadFromDisk();

    // Set up the class loader
    boolean classpathChanged = false;
    String newClasspath = metadataMap.get("classpath");
    if(!Utils.equals(newClasspath, classpath)) {
      classpathChanged = true;
      this.classpath = newClasspath;
      WebState.logs("New classpath: " + classpath);
      if(!StrUtils.isEmpty(classpath)) {
        try {
          List<URL> urls = new ArrayList();
          for(String path : StrUtils.split(classpath, ":"))
            urls.add(new File(path).toURI().toURL());
          this.classLoader = new URLClassLoader((URL[])urls.toArray(new URL[0]),
              this.getClass().getClassLoader());
        } catch(Throwable e) {
          WebState.logs("Error loading classpath: " + e);
          this.classLoader = null;
        }
      }
      else
        this.classLoader = null;
      this.basketView = null; // Force reloading
    }

    // Create the basket factory
    String newBasketFactoryClassName = metadataMap.get("basketFactory");
    if(classpathChanged || !Utils.equals(newBasketFactoryClassName, basketFactoryClassName)) {
      this.basketFactoryClassName = newBasketFactoryClassName;
      WebState.logs("New basketFactory: " + basketFactoryClassName);
      if(!StrUtils.isEmpty(basketFactoryClassName)) {
        try {
          if(classLoader != null) {
            // Need to load the class using the class loader
            Class cls = classLoader.loadClass(basketFactoryClassName);
            this.basketFactory = (BasketFactory)cls.newInstance();
          }
          else {
            // Probably already in class path
            this.basketFactory = (BasketFactory)Class.forName(basketFactoryClassName).newInstance();
          }
        } catch(Throwable e) {
          WebState.logs("Error dynamically creating class: " + e);
          this.basketFactory = BasketView.defaultBasketFactory;
        }
      }
      else
        this.basketFactory = BasketView.defaultBasketFactory;
      this.basketView = null; // Force reloading
    }

    // Get new domain dir
    String newDomainDir = metadataMap.get("domainDir");
    if(!Utils.equals(newDomainDir, domainDir) &&
        (newDomainDir != null && new File(newDomainDir).isDirectory())) {
      this.domainDir = newDomainDir;
      WebState.logs("New domainDir: " + domainDir);
      this.basketView = null; // Force reloading
      this.fieldSpecView = null; // Force reloading
      this.execViewDB = null; // Force reloading
    }

    // Create the views if they need to be created
    if(!StrUtils.isEmpty(domainDir)) {
      if(this.basketView == null)
        this.basketView = new BasketView(this, "baskets",
            new File(domainDir, "baskets").toString(), this.basketFactory, false);
      if(this.fieldSpecView == null)
        this.fieldSpecView = new FieldSpecView(this, "fieldSpecs",
            new File(domainDir, "fieldSpecs").toString());
      if(this.execViewDB == null)
        this.execViewDB = new ExecViewDB(this, "execs",
            new File(domainDir, "execs").toString(),
            new File(domainDir, "views").toString());
    }

    // If views exist, add them
    if(this.basketView != null)    addItem(this.basketView);
    if(this.fieldSpecView != null) addItem(this.fieldSpecView);
    if(this.execViewDB != null)    addItem(this.execViewDB);
  }

  public FieldListMap getMetadataFields() {
    FieldListMap fields = new FieldListMap();
    fields.add(mutableDescriptionField);
    fields.add("domainDir",      "Domain directory").mutable = true;
    fields.add("basketFactory",  "Class name of the basket factory (e.g., for visualization)").mutable = true;
    fields.add("classpath",      "Classpath used to dynamically load the needed classes").mutable = true;
    return fields;
  }

  public void update(UpdateSpec spec, UpdateQueue.Priority priority) throws MyException { // OVERRIDE
    super.update(spec, priority);
    if(this.fieldSpecView != null)
      spec.queue.enqueue(this.fieldSpecView, priority); // We need field specs right away
    updateChildren(spec, priority.next());
  }

  protected Item handleToItem(String handle) throws MyException { return null; }
  protected String itemToHandle(Item item) throws MyException { return null; }

  protected boolean isView() { return true; }
  protected Item newItem(String name) throws MyException { throw MyExceptions.unsupported; }
}
