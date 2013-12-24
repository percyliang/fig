package fig.basic;

import java.util.*;
import java.lang.reflect.*;

/**
Simple utility for *approximately* estimating memory usage of objects on Java.
When to use this rather than a memory profiler?  When you want targeted control
over part of your program and you don't want to pay the overhead of the
profiler.

This utility is based on simple calculations (doesn't detect shared object), so
in a way it provides an upper bound on what the program reasonably should be
implemented (of course, the JVM will do complicated things that make memory
usage hard to compute).

To compute the number of bytes used an object:
  MemUsage.getBytes(object)

To convert this to a user-friendly string:
  MemUsage.getBytesStr(object)

For your custom classes:
  class Foo implements MemUsage.Instrumented {
    int[] a;
    int[] b;
    public long getBytes() {
      // Remember to add the overhead
      return MemUsage.objectOverhead + MemUsage.getBytes(a) + MemUsage.getBytes(b);
    }
  }
*/
public class MemUsage {
  public static int pointerSize = 4;
  public static int objectOverhead = 16;

  // Implement this interface if you want getBytes() to be called on your object
  // or collections containing your object.
  public interface Instrumented {
    public long getBytes();
  }

  public static long getBytes(Object o) {
    if (o == null) return 0;

    // Primitives
    if (o instanceof Byte) return 1;
    if (o instanceof Character) return 2;
    if (o instanceof Integer) return 4;
    if (o instanceof Long) return 8;
    if (o instanceof Float) return 4;
    if (o instanceof Double) return 8;

    // Primitive arrays
    if (o instanceof byte[]) return ((byte[])o).length * 1;
    if (o instanceof char[]) return ((char[])o).length * 2;
    if (o instanceof int[]) return ((int[])o).length * 4;
    if (o instanceof long[]) return ((long[])o).length * 8;
    if (o instanceof float[]) return ((float[])o).length * 4;
    if (o instanceof double[]) return ((double[])o).length * 8;
    if (o instanceof Object[]) {
      Object[] l = (Object[])o;
      long sum = l.length * pointerSize;
      for (Object x : l) {
        sum += getBytes(x);
        if (x != null) sum += objectOverhead;
      }
      return sum;
    }

    // This is not reliable because of string sharing.
    if (o instanceof String)
      return 28 + 2 * ((String)o).length();  // Determined empirically

    if (o instanceof ArrayList) {
      ArrayList l = (ArrayList)o;
      long sum = pointerSize * getArrayListCapacity(l);
      for (Object x : l) sum += getBytes(x) + objectOverhead;
      return sum;
    }

    if (o instanceof HashMap) {
      HashMap m = (HashMap)o;
      //System.out.println("capacity: " + getHashMapCapacity(m));
      long sum = pointerSize * getHashMapCapacity(m);
      for (Object e : m.entrySet()) {
        sum += getBytes(((Map.Entry)e).getKey());
        sum += getBytes(((Map.Entry)e).getValue());
        sum += objectOverhead * 4;  // Determined empirically
      }
      return sum;
    }

    if (o instanceof Instrumented)
      return ((Instrumented)o).getBytes();

    throw new RuntimeException("Unhandled: " + o);
  }

  // Hack: to get ArrayList.elementData
  private static Field arrayListDataField;
  private static <T> int getArrayListCapacity(ArrayList<T> l) {
    if (arrayListDataField == null) {
      try {
        arrayListDataField = ArrayList.class.getDeclaredField("elementData");
        arrayListDataField.setAccessible(true);
      } catch (Exception e) {
        throw new ExceptionInInitializerError(e);
      }
    }
    try {
      final T[] elementData = (T[])arrayListDataField.get(l);
      return elementData.length;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Hack: to get HashMap.table
  private static Field hashMapDataField;
  private static <S, T> int getHashMapCapacity(HashMap<S, T> m) {
    if (hashMapDataField == null) {
      try {
        hashMapDataField = HashMap.class.getDeclaredField("table");
        hashMapDataField.setAccessible(true);
      } catch (Exception e) {
        throw new ExceptionInInitializerError(e);
      }
    }
    try {
      final Map.Entry<S, T>[] table = (Map.Entry<S, T>[])hashMapDataField.get(m);
      return table.length;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static String getBytesStr(Object o) {
    return Fmt.bytesToString(getBytes(o));
  }

  ////////////////////////////////////////////////////////////

  // Test MemUsage class and compare with real memory usage.
  // Getting a hold of real memory usage is a bit delicate and hacky right now,
  // but it's just used as a sanity check.
  public static class Tester {
    long initFreeMemory = -1;
    long initTotalMemory = -1;

    static class C1 implements MemUsage.Instrumented {
      public long getBytes() {
        return 0;
      }
    }

    static class C2 implements MemUsage.Instrumented {
      int[] x = new int[64];
      int[] y = new int[64];
      public long getBytes() {
        // Need 16 extra padding
        return MemUsage.objectOverhead + MemUsage.getBytes(x) + MemUsage.getBytes(y);
      }
    }

    // First time this method is called, assume o is null: used to calibrate.
    void run(String description, Object o) {
      gc();
      if (o == null) {
        //System.out.println(Runtime.getRuntime().freeMemory() + " " + Runtime.getRuntime().totalMemory());
        initFreeMemory = Runtime.getRuntime().freeMemory();
        initTotalMemory = Runtime.getRuntime().totalMemory();
      }
      long actual = (Runtime.getRuntime().totalMemory() - initTotalMemory) -
                    (Runtime.getRuntime().freeMemory() - initFreeMemory);
      long predicted = getBytes(o);
      double error = Math.abs(1.0 * (predicted - actual) / Math.max(actual, 1));
      System.out.println(description + ": " +
                         "predicted: " + predicted + " (" + Fmt.bytesToString(predicted) +"); " +
                         "actual: " + actual + " (" + Fmt.bytesToString(actual) + "); " + 
                         "error: " + error + (error > 0.1 ? " (BIG ERROR)" : ""));
    }

    String newString(int n) {
      StringBuilder buf = new StringBuilder();
      for (int i = 0; i < n; i++)
        buf.append('*');
      return buf.toString();
    }

    ArrayList newObjectList(int n) {
      ArrayList l = new ArrayList();
      for (int i = 0; i < n; i++)
        l.add(newObject());
      return l;
    }

    Object[] newObjectArray(int n) {
      Object[] l = new Object[n];
      for (int i = 0; i < n; i++)
        l[i] = newObject();
      return l;
    }

    Object[] newStringArray(int n, int k) {
      Object[] l = new Object[n];
      for (int i = 0; i < n; i++)
        l[i] = newString(k);
      return l;
    }

    ArrayList newC1(int n) {
      ArrayList l = new ArrayList();
      for (int i = 0; i < n; i++) l.add(new C1());
      return l;
    }

    ArrayList newC2(int n) {
      ArrayList l = new ArrayList();
      for (int i = 0; i < n; i++) l.add(new C2());
      return l;
    }

    HashMap newObjectMap(int n) {
      HashMap m = new HashMap();
      for (int i = 0; i < n; i++)
        //m.put("S"+i, "T"+i);
        m.put(newObject(), newObject());
      return m;
    }

    Object newObject() { return new int[2]; }

    void gc() { System.gc(); }

    void runAll(String[] args) {
      run("null", null); gc();
      run("null", null); gc();

      // Generally have to run things separately, or else measurements won't be accurate.
      for (String arg : args) {
        int i = Integer.parseInt(arg);

        if (i == 0) {
          run("byte[]", new byte[10000000]); gc();
          run("char[]", new char[10000000]); gc();
          run("int[]", new int[10000000]); gc();
          run("long[]", new long[10000000]); gc();
          run("Object[]", new Object[10000000]); gc();
          run("Object[]", newObjectArray(100000)); gc();
          run("int[][]", new int[1000][10000]); gc();
          run("int[][0]", new int[1000000][0]); gc();  // Not accurate
          run("int[][1]", new int[1000000][1]); gc();
          run("int[][4]", new int[1000000][4]); gc();
          run("int[][][]", new int[100][100][100]); gc();
        } else if (i == 1) {
          run("String", newString(1000000)); gc();  // Not accurate
          run("String", newString(10000000)); gc();
          run("String", newStringArray(10000, 10)); gc();
          run("String", newStringArray(1000, 100)); gc();
        } else if (i == 2) {
          run("ArrayList", new ArrayList(10000000)); gc();
          run("ArrayList", newObjectList(10000)); gc();
          run("ArrayList", newObjectList(100000)); gc();
          run("ArrayList", newObjectList(1000000)); gc();
        } else if (i == 3) {
          run("HashMap", newObjectMap(1000)); gc();
          run("HashMap", newObjectMap(10000)); gc();
          run("HashMap", new HashMap(1000000)); gc();
        } else if (i == 4) {
          run("C1", newC1(100000)); gc();
          run("C2", newC2(100000)); gc();
        }
      }
    }
  }

  public static void main(String[] args) {
    new Tester().runAll(args);
  }
}
