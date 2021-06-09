package pacovfor$jbc.utils;

import pacovfor$jbc.backend.asmadapters.MethodAdapter;

import java.io.File;

public class Utils {

    private static String getRelativeFilePathOfMethod(String className, int methodID) {
        return className.replace("/", ".") + File.separator + methodID;
    }

    private static String getFullMethodName(String className, String methodName, String descriptor) {
        return className.replace("/", ".") + File.separator +
                (methodName + descriptor).replace("/", ".");
    }

    /**
     * Returns file path of format "package . classname / methodname descriptor" (WITHOUT the spaces)
     * where all "/" in descriptor is replaced with ".".
     */
    public static String getFullMethodName(MethodAdapter methodAdapter) {
        return getFullMethodName(methodAdapter.getClassAdapter().getName(), methodAdapter.getName(),
                methodAdapter.getDescriptor());
    }


    /**
     * Returns file path of format "package . classname / methodID " (WITHOUT the spaces)
     * where all "/" in descriptor is replaced with ".", and methodID is an integer
     */
    public static String getRelativeFilePathOfMethod(MethodAdapter methodAdapter) {
        return getRelativeFilePathOfMethod(methodAdapter.getClassAdapter().getName(), methodAdapter.getID());
    }

    /**
     * Class name is of format package/ClassName
     */
    public static Object getClassDirName(String className) {
        return className.replace("/", ".");
    }
}
