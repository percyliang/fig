package fig.basic;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import fig.basic.*;
import fig.exec.*;
import fig.prob.*;
import static fig.basic.LogInfo.*;

public class Parallelizer<T> {
  public interface Processor<T> {
    public void process(T x, int i, int n);
  }

  int numThreads;

  public Parallelizer(int numThreads) {
    this.numThreads = numThreads;
  }

  public void process(final List<T> points, final Processor<T> processor) {
    // Loop over examples in parallel
    final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    final Ref<Throwable> exception = new Ref(null);
    for(int i = 0; i < points.size(); i++) {
      final int I = i;
      final T x = points.get(i);
      executor.execute(new Runnable() {
        public void run() {
          if(Execution.shouldBail()) return;
          try {
            if(exception.value == null) {
              processor.process(x, I, points.size());
            }
          } catch(Throwable t) {
            exception.value = t; // Save exception
          }
        }
      });
    }
    executor.shutdown();
    try {
      while(!executor.awaitTermination(1, TimeUnit.SECONDS));
    } catch(InterruptedException e) {
      throw Exceptions.bad("Interrupted");
    }
    if(exception.value != null) throw new RuntimeException(exception.value);
  }

  // Test
  public static void main(String[] args) {
    Parallelizer<String> p = new Parallelizer<String>(10);
    List<String> items = new ArrayList<String>();
    for (int i = 0; i < 10; i++) items.add("item " + i);
    begin_track("main");
    LogInfo.begin_threads();
    p.process(items, new Processor<String>() {
      public void process(String s, int i, int n) {
        begin_track("%d/%d: %s", i, n, s);
        logs("begin");
        Utils.sleep((i/2) * 1000);
        logs("done");
        end_track();
      }
    });
    LogInfo.end_threads();
    end_track();
  }
}
