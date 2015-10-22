import fig.basic.*;
import fig.exec.*;

import java.util.Arrays;
import java.util.List;

/**
 * An example to use OptionsParser class just for setting the options.
 * Also shows  different options that get set, including all the options of its superclasses (including private).
 *
 * Created by Sonal Gupta on 10/21/15.
 */
public class SampleMore extends Sample{

    public enum Test{One, Two};

    @Option(gloss="CANNOT be set")
    final private String description = "Default";

    @Option
    private static String title = "Default";

    @Option(gloss="testing of enum, pass Two or One")
    private Test test = Test.Two;

    @Option(required = true, gloss="testing required field and parsing of arrays")
    List<String> nums = Arrays.asList("hey");

    public void run() {

        LogInfo.begin_track("Super Class:");
        super.run();
        LogInfo.end_track();

        LogInfo.begin_track("Sub Class:");
        LogInfo.logs("Description: " + description);
        LogInfo.logs("Title: " + title);
        LogInfo.logs("Test:" + test);
        LogInfo.logs("List:"+ nums);
        LogInfo.end_track();
    }

    public static void main(String[] args) {
        SampleMore more = new SampleMore();
        OptionsParser parser = new OptionsParser(more);
        if(!parser.parse(args))
            System.exit(1);
        more.run();
    }

}
