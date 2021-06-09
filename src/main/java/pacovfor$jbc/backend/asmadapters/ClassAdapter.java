package pacovfor$jbc.backend.asmadapters;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import pacovfor$jbc.classloaders.ClassLoaderAdapter;
import pacovfor$jbc.utils.Utils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static pacovfor$jbc.backend.asmadapters.MethodAdapter.CLASS_OR_INTERFACE_INIT_METHOD;
import static org.objectweb.asm.Opcodes.*;

public class ClassAdapter {

    public static final String SYNTHETIC_ATTRIBUTE = "Synthetic";

    private final ClassNode classNode;
    private final ClassLoaderAdapter classLoaderAdapter;
    private List<MethodAdapter> methodAdapters;

    public ClassAdapter(byte[] classBytes, ClassLoaderAdapter classLoaderAdapter) {
        if (classBytes == null) {
            throw new IllegalArgumentException("classBytes is null");
        }

        this.classLoaderAdapter = classLoaderAdapter;
        this.classNode = new ClassNode();
        ClassReader cr = new ClassReader(classBytes);
        cr.accept(this.classNode, 0);
    }

    public boolean isSynthetic() {
        return (classNode.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 ||
                (classNode.attrs != null &&
                        classNode.attrs.stream().anyMatch(attribute -> attribute.type.equals(SYNTHETIC_ATTRIBUTE)));
    }

    /**
     * Get all non-native, non-abstract, non-constructors, non-class initializers methods,
     * non-synthetic (including private-ones) and that contain neither Jsr or Ret. Inherited
     * methods are not included.
     */
    public List<MethodAdapter> getMethods() {
        if (this.methodAdapters != null) {
            return this.methodAdapters;
        }


        this.methodAdapters = classNode.methods.stream().
                map(methodNode -> new MethodAdapter(this, methodNode)).
                filter(methodAdapter ->
                        !(methodAdapter.isAbstract() || methodAdapter.isNative() ||
                                methodAdapter.isConstructor() || methodAdapter.isClassOrInterfaceInitializer() ||
                                methodAdapter.isSynthetic())).
                collect(Collectors.toList());

        // assign ids that will be used to identify ids of the method.
        // the cfg is identified by string "package.name.ClassName/<methodId>"
        // where <methodId> is the method id.
        for (int i = 0; i < this.methodAdapters.size(); i++) {
            this.methodAdapters.get(i).build(i);
        }

        this.methodAdapters = this.methodAdapters.stream().
                filter(methodAdapter -> !methodAdapter.containsJsrOrRet()).
                collect(Collectors.toList());

        // if the class is an enum, get rid of values() and valueOf(...) methods
        if ((this.classNode.access & ACC_ENUM) != 0) {
            this.methodAdapters = this.methodAdapters.stream().
                    filter(method -> !(
                            (method.getName().equals("values") && method.getDescriptor().startsWith("()")) ||
                                    (method.getName().equals("valueOf") && method.getDescriptor().
                                            startsWith("(Ljava/lang/String;)"))
                    )).
                    collect(Collectors.toList());
        }

        return this.methodAdapters;
    }

    public boolean isAbstract() {
        return (classNode.access & Opcodes.ACC_ABSTRACT) != 0;
    }

    public int classVersion() {
        return classNode.version;
    }

    ClassNode getClassNode() {
        return classNode;
    }

    public void addLoadCfgsInstns() {
        Optional<MethodNode> optClassInitialzer = this.classNode.methods.stream().
                filter(methodNode -> methodNode.name.equals(CLASS_OR_INTERFACE_INIT_METHOD)).findFirst();

        // if there is no class initializer, create one, add it to the methods, and add RETURN instruction to it
        MethodNode classInitializer;

        if (optClassInitialzer.isEmpty()) {
            classInitializer = new MethodNode(ACC_STATIC, CLASS_OR_INTERFACE_INIT_METHOD,
                    "()V", null, null);
            this.classNode.methods.add(classInitializer);

            classInitializer.instructions.add(new InsnNode(RETURN));
            classInitializer.maxLocals = 0;
            classInitializer.maxStack = 0;
        } else {
            classInitializer = optClassInitialzer.get();
        }

        InsnList newInstnList = new InsnList();
        // insert call GraphAdapter.loadAllCfgsOfClass(className) at the beginning of the method
        newInstnList.add(new LdcInsnNode(Utils.getClassDirName(this.classNode.name)));
        newInstnList.add(new MethodInsnNode(INVOKESTATIC, "pacovfor$jbc/frontend/graphadapters/GraphAdapter",
                "loadAllCfgsOfClass", "(Ljava/lang/String;)V", false));

        classInitializer.instructions.insert(newInstnList);

        // change local variables max and stack max values
        classInitializer.maxStack += 1;
    }

    public String getName() {
        return this.classNode.name;
    }

    public byte[] toByteArray() {
        // iterate through methods and update max stack values.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected ClassLoader getClassLoader() {
                return ClassAdapter.this.classLoaderAdapter.getClassLoader();
            }
        };

        this.classNode.accept(cw);
        return cw.toByteArray();
    }
}
