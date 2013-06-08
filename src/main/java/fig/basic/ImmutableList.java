package fig.basic;

import java.util.*;

/**
 * An immutable single-linked list, which allows for sharing tails.
 */
public class ImmutableList<T> {
  public T value;
  public ImmutableList<T> next;

  private ImmutableList(T value, ImmutableList<T> next) {
    this.value = value;
    this.next = next;
  }

  public boolean isEmpty() { return this == empty; }

  public int size() { return isEmpty() ? 0 : 1 + next.size(); }

  public ImmutableList<T> prepend(T value) { return new ImmutableList(value, this); }

  public static ImmutableList empty = new ImmutableList(null, null);
}
