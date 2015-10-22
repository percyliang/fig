import fig.basic.*;
import fig.exec.*;

/**
 * An example to use Execution class just for setting the options.
 * Also shows that the init function sets all its options (including private) and
 * all the options of its superclasses (including private).
 *
 * Created by Sonal Gupta on 10/21/15.
 */
public class SampleMore extends Sample{

    public enum Test{One, Two};

    @Option
    final private String description = "Default";

    @Option
    private String title = "Default";

    @Option
    private Test test = Test.Two;

    @Option
    private double testNumber = 1;

    public void run() {

        LogInfo.begin_track("Super Class:");
        super.run();
        LogInfo.end_track();

        LogInfo.begin_track("Sub Class:");
        LogInfo.logs("Description: " + description);
        LogInfo.logs("Title: " + title);
        LogInfo.logs("Test:" + test);
        LogInfo.end_track();
    }

    public static void main(String[] args) {
        SampleMore s  = new SampleMore();
        Execution.init(args, s);
        s.run();
        Execution.finish();
    }

}
