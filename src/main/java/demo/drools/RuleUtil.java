package demo.drools;

/**
 * @author shalugin
 */
public final class RuleUtil {

    private RuleUtil() {
    }

    public static String getRulesFolder() {
        return System.getenv("user.dir");
    }
}