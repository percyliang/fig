import fig.basic.OptionsParser;

/**
 * Created by Sonal Gupta (sonal@viv.ai) on 10/21/15.
 */
public class SampleProperties {


    public static void main(String[] args) {
        String propsfile = args[0];
        SampleMore more = new SampleMore();
        OptionsParser.parsePropertiesFile(propsfile, true, more);
        more.run();
    }
}
