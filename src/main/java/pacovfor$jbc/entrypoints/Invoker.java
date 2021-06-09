package pacovfor$jbc.entrypoints;

import javassist.CannotCompileException;
import javassist.CtClass;
import pacovfor$jbc.classloaders.JavassistClassLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Invoker {
    public static void main(String[] args)
            throws CannotCompileException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        JavassistClassLoader js = new JavassistClassLoader();
        js.appendSystemPath();
        js.insertClassPath("target\\classes");
        js.insertClassPath("newdir");
        CtClass c = js.getClass("example.pack.WhileClass");
        Class<?> x = c.toClass();

        System.out.println(x.getName());
//        x.getConstructor().setAccessible(true);
        Object ex3 = x.getConstructor().newInstance();
//        Method m = x.getDeclaredMethod("whileMethod2", int.class);
        Method m = x.getDeclaredMethod("whileMethod", int.class);
//        int res = (int) m.invoke(ex3, 3);
        Object invoke = m.invoke(ex3, 3);
        System.out.println("result: ... " + invoke);
    }
}
