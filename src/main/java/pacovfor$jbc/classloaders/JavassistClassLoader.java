package pacovfor$jbc.classloaders;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

public class JavassistClassLoader {

    private final ClassPool classPool;

    public JavassistClassLoader() {
        classPool = new ClassPool();
    }

    public ClassPool getClassPool() {
        return classPool;
    }

    /**
     * Insert a class path into the class loader
     */
    public JavassistClassLoader insertClassPath(String classPath) {
        try {
            classPool.insertClassPath(classPath);
            return this;
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public JavassistClassLoader appendSystemPath() {
        classPool.appendSystemPath();
        return this;
    }

    /**
     * Get CtClass given a class name of format
     */
    public CtClass getClass(String className) {
        try {
            return classPool.get(className);
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
