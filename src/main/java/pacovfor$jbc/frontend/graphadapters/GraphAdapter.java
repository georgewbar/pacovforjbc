package pacovfor$jbc.frontend.graphadapters;

import pacovfor$jbc.analysis.graphtypes.ProbePositionIDCfg;
import pacovfor$jbc.analysis.nodetypes.ProbePositionID;
import pacovfor$jbc.config.Config;
import pacovfor$jbc.graph.Node;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// this will import all the ProbePositionStringCfgs from a given directory
public class GraphAdapter {

    private static final PrintStream logStream;
    private static final Map<String, ProbePositionIDCfg> cfgs;

    static {
        File logFile = new File(Config.logDir);
        if (!logFile.exists()) {
            Config.tryCreatingFilePathOrElseThrowExc(Config.logDir);
        }

        try {
            logStream = new PrintStream(Config.logDir + File.separator + "logs.txt", StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        cfgs = new ConcurrentHashMap<>();
        // add shut down hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // check the size of the cfgs
                int noOfLoadedMethodCfgs = cfgs.size();
                List<File> allMethodCfgFiles = getAllFiles(Config.cfgsDir);

                // output the name of the ones the were covered.
                logStream.println("loaded-cfgs: " + noOfLoadedMethodCfgs);
                logStream.println("total-cfgs: " + allMethodCfgFiles.size());
                cfgs.forEach((key, value) -> logStream.println(key + ": " + value.getFullMethodName()));

                // filter and output the cfgs that were entered
                Set<String> cfgNames = cfgs.entrySet().stream().
                        filter(entry -> entry.getValue().isEntered()).
                        map(entry -> entry.getKey() + ": " + entry.getValue().getFullMethodName()).
                        collect(Collectors.toSet());

                logStream.println("covered-cfgs: " + cfgNames.size());
                cfgNames.forEach(logStream::println);

                // output coverage for each method in each class
                // create a folder for each class in logs and create a file for each method
                // and output the coverage information of that method to the created file
                cfgs.forEach(GraphAdapter::outputCfgToFile);

            } finally {
                logStream.flush();
                logStream.close();
            }
        }));
    }

    private static void outputCfgToFile(String relativePathToMethod, ProbePositionIDCfg cfg) {
        File folder = new File(Config.logDir + File.separator +
                relativePathToMethod.substring(0, relativePathToMethod.indexOf(File.separator)));
        if (!folder.exists() && !folder.mkdirs()) {
            logStream.println("GraphAdapter [ERROR]: folder: " + folder.getPath() + " does not exist");
            throw new IllegalStateException("folder: " + folder.getPath() + " does not exist");
        }

        PrintStream methodOutputStream;
        try {
            methodOutputStream = new PrintStream(Config.logDir + File.separator +
                    relativePathToMethod, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        methodOutputStream.println(cfg.getRelativeFilePath()); // relative file path of cfg
        methodOutputStream.println(cfg.getFullMethodName()); // full method name of cfg
        cfg.getCoverageInfoKeyPairs().forEach((metric, value) -> methodOutputStream.println(metric + ": " + value));
        methodOutputStream.flush();
        methodOutputStream.close();
    }

    private static void getAllFilesHelper(String directoryName, List<File> files) {
        // https://stackoverflow.com/questions/14676407/list-all-files-in-the-folder-and-also-sub-folders
        File directory = new File(directoryName);

        // Get all files from a directory.
        File[] fList = directory.listFiles();
        if (fList != null) {
            for (File file : fList) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    getAllFilesHelper(file.getAbsolutePath(), files);
                }
            }
        }
    }

    private static List<File> getAllFiles(String directoryName) {
        List<File> allFiles = new ArrayList<>();
        getAllFilesHelper(directoryName, allFiles);
        return allFiles;
    }

    private static void loadCfg(String absolutePathOfMethodCfg) {
//        System.out.println("loadCfg(...): " + absolutePathOfMethodCfg);
        // search for key as: package.classname File.sep methodName methodDesc
        String splitChar = String.format("\\%s", File.separator); // escape '/' or '\'
        String[] pathComponents = absolutePathOfMethodCfg.split(splitChar);
        String methodRelativePath = pathComponents[pathComponents.length - 2] + File.separator +
                pathComponents[pathComponents.length - 1];

        if (cfgs.containsKey(methodRelativePath)) {
            return;
        }

        ProbePositionIDCfg cfg = ProbePositionIDCfg.readCfgFromFile(methodRelativePath);
        cfg.setErrStream(logStream);
        cfg.updateTestRequirements();
        cfgs.put(methodRelativePath, cfg);
    }

    public synchronized static void loadAllCfgsOfClass(String className) {
//        System.out.println("loadAllCfgsOfClass(...): " + className);
        List<File> allCfgsOfClass = getAllFiles(Config.cfgsDir + File.separator + className);
        allCfgsOfClass.forEach(file -> loadCfg(file.getAbsolutePath()));
    }

    /**
     * @param relativePathOfMethodCfg - should be the name of the file containing the cfg of the method
     */
    public static void cover(String relativePathOfMethodCfg, Path path) {
//        System.out.println("cover(...): " + relativePathOfMethodCfg);

        ProbePositionIDCfg cfg = cfgs.get(relativePathOfMethodCfg);
        if (cfg == null) {
            logStream.println("GraphAdapter [ERROR]: cfg of " + relativePathOfMethodCfg + " does not exist");
            throw new IllegalStateException("Error happened: cfg of " + relativePathOfMethodCfg + " does not exist");
        }

        if (!cfg.getRelativeFilePath().equals(relativePathOfMethodCfg)) {
            logStream.println("GraphAdapter [ERROR]: concurrency error when retrieving cfg of " + relativePathOfMethodCfg);
            throw new IllegalStateException("Error happened: concurrency error when retrieving cfg of " + relativePathOfMethodCfg);
        }

        // mark the cfg as entered.
        cfg.setEntered(true);

        // convert path to edges
        List<Node<ProbePositionID>> pathToCover = path.getPath().stream().map(Node::new).collect(Collectors.toList());
        cfg.coverTestRequirements(pathToCover);
    }
}
