package pacovfor$jbc.classloaders;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Class loader adapter either:
 * - makes a copy of the content of the wanted directory and paste it into a new destination directory,
 * - or extracts the jar file into a new destination directory.
 * <p>
 * It, then, loads all classes from that new destination directory.
 */
public class ClassLoaderAdapter {

    private final File dirOrJarFile;
    private final File destinationDirectory;
    private final ClassLoader classLoader;
    private final List<File> allFilesInDirOrJarFile;

    private File createOrGetDirectory(String pathToDirectory) {
        File destinationDirectory = new File(pathToDirectory);
        if (!destinationDirectory.exists()) {
            if (!destinationDirectory.mkdirs()) {
                throw new RuntimeException("path to destination directory: " +
                        destinationDirectory.getAbsolutePath() + " could not be created");
            }
        } else if (destinationDirectory.exists() && !destinationDirectory.isDirectory()) {
            throw new IllegalArgumentException("path to destination directory is not a directory.");
        } else if (destinationDirectory.exists() && destinationDirectory.isDirectory() &&
                Objects.requireNonNull(destinationDirectory.list()).length != 0) {
            throw new IllegalArgumentException("destination directory is not empty");
        }
        // else, do nothing. The reason is that, at this point in execution, destination path exists and is empty.

        return destinationDirectory;
    }

    private void close(ZipFile zipFile) {
        if (zipFile != null) {
            try {
                zipFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void close(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void close(OutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void unzip(File file, File destinationDirectory) {
        if (!destinationDirectory.exists()) {
            if (!destinationDirectory.mkdirs()) {
                throw new RuntimeException("destination " + destinationDirectory.getAbsolutePath() +
                        " could not be created");
            }
        }

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);
            for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                String filePath = Paths.get(destinationDirectory.getAbsolutePath(), entry.getName()).toString();
                if (!entry.isDirectory()) {
                    extractFile(zipFile, entry, filePath);
                } else {
                    File dir = new File(filePath);
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new RuntimeException("directory " + dir.getAbsolutePath() + " could not be created");
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            close(zipFile);
        }
    }

    private void extractFile(ZipFile zipFile, ZipEntry zipEntry, String outFilePath) {
        InputStream in = null;
        OutputStream out = null;

        try {
            // fix for Windows
            File parentFolder = new File(outFilePath).getParentFile();
            if (!parentFolder.exists() && !parentFolder.mkdirs()) {
                throw new IOException("Failed to create parent directory " + parentFolder);
            }

            in = zipFile.getInputStream(zipEntry);
            out = new BufferedOutputStream(new FileOutputStream(outFilePath));
            byte[] bytesIn = new byte[1024];
            int read;
            while ((read = in.read(bytesIn)) != -1) {
                out.write(bytesIn, 0, read);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            close(out);
            close(in);
        }
    }

    private void copyFilesFrom(File srcDir, File destDir) {
        try {
            FileUtils.copyDirectory(srcDir, destDir, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void copyOrExtractDirOrJarFile(File dirOrJarFile, File destinationDirectory) {
        assert destinationDirectory.exists();

        if (dirOrJarFile.getAbsolutePath().endsWith(".jar")) {
            // extract "dirOrJarFile" to a new directory "destinationDirectory"
            unzip(dirOrJarFile, destinationDirectory);
        } else if (dirOrJarFile.isDirectory()) {
            // copy "dirOrJarFile" to a new directory "destinationDirectory"
            copyFilesFrom(dirOrJarFile, destinationDirectory);
        } else {
            throw new IllegalArgumentException("the wanted directory is neither a directory nor a jar file");
        }
    }

    private ClassLoader createURLClassLoader(File destinationDirectory, List<File> additionalClassPaths) {
        final URL dirOrJarFileURL;
        final List<URL> additionalUrls;
        try {
            dirOrJarFileURL = destinationDirectory.toURI().toURL();
            additionalUrls = additionalClassPaths.stream().map(file -> {
                try {
                    return file.toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException(e);
                }
            }).collect(Collectors.toList());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }

        List<URL> urls = new ArrayList<>();
        // order of paths is important
        urls.add(dirOrJarFileURL);
        urls.addAll(additionalUrls);

        // child first - parent last class loader
        return new ClassLoader() {
            private final URLClassLoader childClassLoader = new URLClassLoader(urls.toArray(new URL[0]), null);
            private final URLClassLoader parentClassLoader = new URLClassLoader(new URL[]{});

            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                try {
                    return childClassLoader.loadClass(name);
                } catch (ClassNotFoundException e) {
                    return parentClassLoader.loadClass(name);
                }
            }

            @Override
            public InputStream getResourceAsStream(String name) {
                InputStream in = childClassLoader.getResourceAsStream(name);
                if (in == null) {
                    in = parentClassLoader.getResourceAsStream(name);
                }

                return in;
            }
        };
    }

    /**
     * Get all ".class" files in "rootClassPathDir".
     */
    private Collection<File> getAllClassFilesInDir(File rootClassPathDir) {
        return FileUtils.listFiles(rootClassPathDir, new String[]{"class"}, true);
    }

    private File getDirOrJarFile(String pathToDirOrJarFile) {
        File f = new File(pathToDirOrJarFile);
        if (!f.exists()) {
            throw new IllegalArgumentException("Dir or Jar file: " + pathToDirOrJarFile + " is not found.");
        }

        return f;
    }

    /**
     * Constructor
     */
    public ClassLoaderAdapter(String pathToDirOrJarFile, String pathToDestinationDirectory, List<String> classPaths) {
        if (pathToDirOrJarFile == null || pathToDestinationDirectory == null) {
            throw new IllegalArgumentException("arguments shall not be null");
        }

        // check if dir or jar file exists and get dir or jar file
        this.dirOrJarFile = getDirOrJarFile(pathToDirOrJarFile);
        // try to create or get the new destination directory
        this.destinationDirectory = createOrGetDirectory(pathToDestinationDirectory);
        // try to copy (or unzip) the content in the wanted directory (or jar.file) to
        // the "destinationDirectory"
        copyOrExtractDirOrJarFile(dirOrJarFile, destinationDirectory);

        List<File> additionalClassPaths = classPaths.stream().map(File::new).collect(Collectors.toList());
        this.classLoader = createURLClassLoader(destinationDirectory, additionalClassPaths);
        this.allFilesInDirOrJarFile = new ArrayList<>();

        // populate a list with the all ".class" files in destination directory
        allFilesInDirOrJarFile.addAll(getAllClassFilesInDir(destinationDirectory));
    }

    public Class<?> loadClassFromPath(String className) {
        if (className == null) {
            throw new IllegalArgumentException("class name is null");
        }

        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String convertClassFileToClassName(File classFile) {
        URL classFileURL;
        URL classPathRoot;
        try {
            classFileURL = classFile.toURI().toURL();
            classPathRoot = destinationDirectory.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // change to normal class names of java: mypackage.MyClass
        // replace "/" with "." (because URLs always have "/" and not "\" regardless of the operating system)
        String s = classFileURL.toString().substring(classPathRoot.toString().length()).replace("/", ".");
        // remove ".class" from the end of the string (i.e., remove the last 6 chars)
        return s.substring(0, s.length() - 6);
    }

    /**
     * Loads bytes from and closes inputstream
     */
    public byte[] loadBytesFrom(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("input stream is null");
        }

        try {
            return IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            close(inputStream);
        }
    }

    /**
     * Load the bytes of a class in a given path. Format of path is platform-dependent.
     */
    public byte[] loadClassAsBytesFromPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }

        InputStream in;
        try {
            in = new BufferedInputStream(new FileInputStream(path));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        return loadBytesFrom(in);
    }

    /**
     * Write bytes to and closes output stream
     */
    public void writeBytesTo(OutputStream outputStream, byte[] bytes) {
        if (outputStream == null || bytes == null) {
            throw new IllegalArgumentException("output stream or bytes is null");
        }

        try {
            IOUtils.write(bytes, outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            close(outputStream);
        }
    }

    /**
     * Write the bytes of a class by format package.ClassName
     */
    public void writeClassAsBytes(String className, byte[] bytes) {
        if (className == null || bytes == null) {
            throw new IllegalArgumentException("class name or bytes is null");
        }

        // destination directory is the directory that has the classes to change
        String pathToClass = destinationDirectory.getPath() + File.separator +
                className.replace(".", File.separator) + ".class";

        OutputStream outputStream;
        try {
            outputStream = new FileOutputStream(pathToClass);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        writeBytesTo(outputStream, bytes);
    }

    /**
     * Load the bytes of a class by format package.ClassName
     */
    public byte[] loadClassAsBytes(String className) {
        if (className == null) {
            throw new IllegalArgumentException("class name is null");
        }

        // convert class name of format mypackage.MyClass to mypackage/Myclass.class
        String resourceName = className.replace(".", "/") + ".class";
        InputStream in = classLoader.getResourceAsStream(resourceName);
        if (in == null) {
            throw new IllegalStateException("input stream of class name: " + className + " is null");
        }

        return loadBytesFrom(in);
    }

    /**
     * Create an instrumented jar file called "instrumentedJarFile.jar" if the original source
     * of the class adapter is a jar file.
     */
    public void createInstrumentedJarFile() {
        if (dirOrJarFile.isDirectory()) {
            throw new RuntimeException("The source from while classes are instrumented is a directory.");
        }

        // create a zip file
        JarOutputStream target;
        try {
            target = new JarOutputStream(new FileOutputStream("instrumentedJarFile.jar"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // adapted from https://stackoverflow.com/questions/1281229/how-to-use-jaroutputstream-to-create-a-jar-file
        Iterator<File> filesOrDirs = FileUtils.iterateFiles(destinationDirectory, null, true);

        try {
            while (filesOrDirs.hasNext()) {
                File source = filesOrDirs.next();
                BufferedInputStream in = null;

                try {
                    StringBuilder name = new StringBuilder();

                    String sourceName = source.getPath().substring(destinationDirectory.getPath().length() + 1);
                    name.append(sourceName.replace("\\", "/"));

                    if (source.isDirectory()) {
                        if (name.length() > 0) {
                            if (!name.toString().endsWith("/"))
                                name.append("/");
                        }
                    }

                    JarEntry entry = new JarEntry(name.toString());
                    entry.setTime(source.lastModified());
                    target.putNextEntry(entry);
                    in = new BufferedInputStream(new FileInputStream(source));

                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        target.write(buffer, 0, count);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    close(in);
                }
            }
        } finally {
            close(target);
        }
    }

    //=====================================
    // getters and setters

    public List<String> getAllClassNamesInPath() {
        return allFilesInDirOrJarFile.stream().map(this::convertClassFileToClassName).collect(Collectors.toList());
    }

    public Iterable<byte[]> getAllClassesAsBytesFromPath() {
        return allFilesInDirOrJarFile.stream().map(this::convertClassFileToClassName)
                .map(this::loadClassAsBytes).collect(Collectors.toList());
    }

    public List<File> getAllClassFilesInPath() {
        return Collections.unmodifiableList(allFilesInDirOrJarFile);
    }

    public File getSourceDirectory() {
        return dirOrJarFile;
    }

    public File getDestinationDirectory() {
        return destinationDirectory;
    }

    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    public static void main(String[] args) {
        // test scenario
        ClassLoaderAdapter cla = new ClassLoaderAdapter("GraphLibrary-1.0-SNAPSHOT.jar", "newdir", new ArrayList<>());
        cla.getAllClassNamesInPath().forEach(System.out::println);
        List<String> classNames = cla.getAllClassNamesInPath();
        byte[] clz = cla.loadClassAsBytes(classNames.get(0));

        System.out.println();
        System.out.println(clz.length);
        System.out.println();
        cla.getAllClassesAsBytesFromPath().forEach(bytes -> System.out.println(bytes.length));
        cla.createInstrumentedJarFile();
    }
}
