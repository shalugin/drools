package demo.drools;

public final class FileUtil {

    // returns null if file isn't relative to folder
    public static String getRelativePath(String filePath, String folderPath) {
        if (filePath.startsWith(folderPath)) {
            String substring = filePath.substring(folderPath.length() + 1);
            return substring.replace('\\', '/');
        } else {
            return null;
        }
    }

    private FileUtil() {
    }
}