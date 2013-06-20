package fig.basic;

public class ConstantFunction<S, T> implements Function<S, T> {
  public final T value;
  public ConstantFunction(T value) { this.value = value; }
  public T apply(S input) { return value; }
}
