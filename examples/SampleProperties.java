import fig.basic.OptionsParser;

/**
 * Created by Sonal Gupta (sonal@cs.stanford.edu) on 10/21/15.
 */
public class SampleProperties {

    public static void main(String[] args) {
        String propsfile = args[0];
        Sample more = new Sample();
        OptionsParser.parsePropertiesFile(propsfile, true, more);
        more.run();
    }
}
