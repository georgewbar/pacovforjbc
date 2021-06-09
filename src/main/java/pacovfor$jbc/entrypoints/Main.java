package pacovfor$jbc.entrypoints;

import pacovfor$jbc.analysis.Instrumenter;
import pacovfor$jbc.backend.asmadapters.ClassAdapter;
import pacovfor$jbc.classloaders.ClassLoaderAdapter;
import pacovfor$jbc.config.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static pacovfor$jbc.config.Config.checkIfFileExistsElseCreateFile;

public class Main {

    public static void main(String[] args) {
        String pathToDirOrJarFile = args[0];
        String pathToDestinationDirectory = args[1];

        checkIfFileExistsElseCreateFile(Config.cfgsDir);
        checkIfFileExistsElseCreateFile(Config.logDir);

        List<String> additionalClassPaths = new ArrayList<>(Arrays.asList(args).subList(2, args.length));
        ClassLoaderAdapter cla = new ClassLoaderAdapter(pathToDirOrJarFile, pathToDestinationDirectory, additionalClassPaths);
        List<String> classNames = cla.getAllClassNamesInPath();
        System.out.println("Classes to instrument ...: " + classNames.size());
        int index = 0;
        for (String className : classNames) {

            byte[] clzBytes = cla.loadClassAsBytes(className);
            ClassAdapter classAdapter = new ClassAdapter(clzBytes, cla);

            if (classAdapter.isSynthetic()) {
                // log synthetic classes
                System.out.println("[INFO] class " + className + " is synthetic...Check if it is needed to skip");
            }

            if (classAdapter.classVersion() < 50) {
                // class version of java 6 == 50
                System.out.println("[WARNING] class version of class " + className + " < 50: " + classAdapter.classVersion());
            }

            System.out.println("[INFO]: Instrumenting " + className + " ... " + (index + 1) + "/" + classNames.size() +
                   " " + String.format("%.0f", (index + 1) * 1.0d / classNames.size() * 100.0d) + "%");

            Instrumenter.instrument(classAdapter);
            byte[] newClzBytes = classAdapter.toByteArray();

            cla.writeClassAsBytes(className, newClzBytes);

            index++;
        }

        if (pathToDirOrJarFile.endsWith(".jar")) {
            System.out.println("rezipping into a jar file ...");
            // rezip file
            cla.createInstrumentedJarFile();
        }
    }
}
