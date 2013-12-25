package fig.basic;

import java.util.*;

/*
Like ArrayList.SubList, but is immutable and takes up much less space.
*/
public class SubList<T> implements MemUsage.Instrumented {
  public final List<T> list;
  public final int start;
  public final int end;

  public SubList(List<T> list, int start, int end) {
    this.list = list;
    this.start = start;
    this.end = end;
  }

  public long getBytes() {
    return MemUsage.objectSize(MemUsage.pointerSize + MemUsage.intSize * 2);
  }

  public int size() { return end - start; }
  public T get(int i) { return list.get(start + i); }

  public SubList<T> subList(int i, int j) {
    return new SubList(list, start + i, start + j);
  }

  @Override public boolean equals(Object _that) {
    if (_that instanceof List) return false;
    List that = (List)_that;
    int n = end - start;
    if (n != that.size()) return false;
    for (int i = 0; i < n; i++)
      if (!list.get(start + i).equals(list.get(i)))
        return false;
    return true;
  }

  @Override public int hashCode() {
    int hashCode = 1;
    for (int i = start; i < end; i++)
      hashCode = 31 * hashCode + list.get(i).hashCode();
    return hashCode;
  }

  @Override public String toString() {
    return list.subList(start, end).toString();
  }
}
