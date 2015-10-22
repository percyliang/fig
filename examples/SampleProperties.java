import fig.basic.OptionsParser;

/** Parsing of a properties file
 * Created by Sonal Gupta (sonal@cs.stanford.edu) on 10/21/15.
 */
public class SampleProperties {

    public static void main(String[] args) {
        String propsfile = args[0];
        SampleMore more = new SampleMore();

        //fullname match -- note that you have to write SampleMore.N instead of Sample.N
        OptionsParser.parsePropertiesFile(propsfile, true, true, true, more);
        more.run();

        //for setting just the static options
        //OptionsParser.parsePropertiesFile(propsfile, true, true, SampleMore.class);

    }
}
