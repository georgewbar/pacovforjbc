package pacovfor$jbc.backend.asmadapters;

import pacovfor$jbc.utils.Utils;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;

/**
 * Note: bytecode of a method must not be empty according to jvm spec
 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.3 (section 4.7.3)
 */
public class MethodAdapter {

    public static final String SYNTHETIC_ATTRIBUTE = "Synthetic";
    public static final String CONSTRUCTOR_METHOD_NAME = "<init>";
    public static final String CLASS_OR_INTERFACE_INIT_METHOD = "<clinit>";

    private final ClassAdapter classAdapter;
    private final MethodNode methodNode;
    private List<InstructionAdapter> instructionList; // already sorted by bytecode index and instruction index
    private List<ExceptionTableEntryAdapter> exceptionEntries;
    private int id;

    private boolean isChanged = false;

    public String getFullName() {
        return Utils.getRelativeFilePathOfMethod(this);
    }

    public String getName() {
        return methodNode.name;
    }

    public String getDescriptor() {
        return methodNode.desc;
    }

    MethodAdapter(ClassAdapter classAdapter, MethodNode methodNode) {
        if (classAdapter == null || methodNode == null) {
            throw new IllegalArgumentException("classAdapter is null or methodNode is null");
        }

        this.classAdapter = classAdapter;
        this.methodNode = methodNode;
    }

    MethodAdapter build(int id) {
        this.instructionList = getInstructionList(this.methodNode);
        this.exceptionEntries = getExceptionEntries(this.instructionList, this.methodNode);
        this.id = id;
        return this;
    }

    private List<ExceptionTableEntryAdapter> getExceptionEntries(List<InstructionAdapter> instructionList,
                                                                 MethodNode methodNode) {
        List<ExceptionTableEntryAdapter> exceptionEntries = new ArrayList<>();

        List<TryCatchBlockNode> tryCatchBlocks = methodNode.tryCatchBlocks;
        for (int i = 0; i < tryCatchBlocks.size(); i++) {
            TryCatchBlockNode tryCatchBlock = tryCatchBlocks.get(i);
            exceptionEntries.add(new ExceptionTableEntryAdapter(this, i, tryCatchBlock));
        }

        return exceptionEntries;
    }

    /**
     * Get all instructions (including non-real instructions).
     *
     * @see InstructionAdapter#isRealInstruction()
     */
    public List<InstructionAdapter> getInstructions() {
        return new ArrayList<>(this.instructionList);
    }

    /**
     * Get all real instructions (including used label)
     */
    public List<InstructionAdapter> getRealInstructions() {
        return this.instructionList.stream().filter(InstructionAdapter::isRealInstruction).collect(Collectors.toList());
    }

    InstructionAdapter getInstructionAdapter(AbstractInsnNode insnNode) {
        return getInstructionAdapter(this.instructionList, insnNode);
    }

    private InstructionAdapter getInstructionAdapter(List<InstructionAdapter> instrs,
                                                     AbstractInsnNode instrNode) {

        return instrs.stream().
                filter(instructionAdapter -> instructionAdapter.getAsmInstruction() == instrNode).
                findFirst().
                orElseThrow(() -> new IllegalStateException("instrNode does not exist; this should not happen"));
    }

    private void addLabelToInstrAndMark(InstructionAdapter instruction, InstructionAdapter label) {
        label.setUsedLabel(true);
        instruction.addTargetLabel(label);
    }

    private String getInstructionAsString(AbstractInsnNode asmInstruction, Printer printer, TraceMethodVisitor mp) {
        asmInstruction.accept(mp);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().strip();
    }

    /**
     * create an instruction list of BytecodeInstructionAdapter containing: 1) method adapter,
     * 2) instruction index, 3) asm instruction.
     */
    private List<InstructionAdapter> getInstructionList(MethodNode methodNode) {
        List<InstructionAdapter> newInstructions = new ArrayList<>();
        Printer pr = new Textifier();
        TraceMethodVisitor mp = new TraceMethodVisitor(pr);

        // first pass: add all instructions to instructions list
        List<AbstractInsnNode> instructionsList = Arrays.asList(methodNode.instructions.toArray());
        for (int i = 0; i < instructionsList.size(); i++) {
            AbstractInsnNode instruction = instructionsList.get(i);
            newInstructions.add(
                    new InstructionAdapter(this, i, instruction, getInstructionAsString(instruction, pr, mp))
            );
        }

        // second pass: mark all used labels
        for (int i = 0; i < newInstructions.size(); i++) {
            InstructionAdapter instrAdapter = newInstructions.get(i);
            AbstractInsnNode instruction = instrAdapter.getAsmInstruction();

            if (instruction.getType() == AbstractInsnNode.JUMP_INSN) {
                JumpInsnNode jumpInstr = (JumpInsnNode) instruction;

                // search for label in newInstructions and add it to labels of jumpInstr
                addLabelToInstrAndMark(instrAdapter, getInstructionAdapter(newInstructions, jumpInstr.label));

            } else if (instruction.getType() == AbstractInsnNode.LOOKUPSWITCH_INSN) {
                LookupSwitchInsnNode lookupInstr = (LookupSwitchInsnNode) instruction;

                // search for labels in newInstructions and add it to labels of lookupInstr
                for (LabelNode labelNode : lookupInstr.labels) {
                    addLabelToInstrAndMark(instrAdapter, getInstructionAdapter(newInstructions, labelNode));
                }

                // add default label (if it exists)
                if (lookupInstr.dflt == null) {
                    System.out.println("lookupSwitch instr #" + instrAdapter.index() + " of method: " +
                            classAdapter.getClassNode().name + "." + methodNode.name + methodNode.desc + " is null");
                } else {
                    addLabelToInstrAndMark(instrAdapter, getInstructionAdapter(newInstructions, lookupInstr.dflt));
                }

            } else if (instruction.getType() == AbstractInsnNode.TABLESWITCH_INSN) {
                TableSwitchInsnNode tableSwitchInstr = (TableSwitchInsnNode) instruction;

                // search for labels in newInstructions and add it to labels of tableSwitchInstr
                for (LabelNode labelNode : tableSwitchInstr.labels) {
                    addLabelToInstrAndMark(instrAdapter, getInstructionAdapter(newInstructions, labelNode));
                }

                // add default label (if it exists)
                if (tableSwitchInstr.dflt == null) {
                    System.out.println("tableSwitch instr #" + instrAdapter.index() + " of method: " +
                            classAdapter.getClassNode().name + "." + methodNode.name + methodNode.desc + " is null");
                } else {
                    addLabelToInstrAndMark(instrAdapter, getInstructionAdapter(newInstructions, tableSwitchInstr.dflt));
                }
            }
        }

        return newInstructions;
    }

    /**
     * Returns the first real instruction before a given instruction, or null if there is none.
     */
    public InstructionAdapter firstInstructionBefore(InstructionAdapter instruction) {
        if (instruction == null) {
            throw new IllegalArgumentException("instruction is null");
        }

        int instrIndex = instruction.index();

        // search for the first real instruction before "instruction"
        for (int i = instrIndex - 1; i >= 0; i--) {
            InstructionAdapter instr = instructionList.get(i);
            if (instr.getAsmInstruction().getOpcode() >= 0) {
                return instr;
            }
        }

        return null;
    }

    public boolean containsJsrOrRet() {
        for (AbstractInsnNode instr : this.methodNode.instructions) {
            if (instr.getOpcode() == JSR || instr.getOpcode() == RET) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the first real instruction after a given instruction, or null if there is none.
     */
    public InstructionAdapter firstInstructionAfter(InstructionAdapter instruction) {
        if (instruction == null) {
            throw new IllegalArgumentException("instruction is null");
        }

        int instrIndex = instruction.index();

        // search for the first real instruction before "instruction"
        for (int i = instrIndex + 1; i < instructionList.size(); i++) {
            InstructionAdapter instr = instructionList.get(i);
            if (instr.getAsmInstruction().getOpcode() >= 0) {
                return instr;
            }
        }

        return null;
    }

    public boolean isNative() {
        return (methodNode.access & Opcodes.ACC_NATIVE) != 0;
    }

    public boolean isAbstract() {
        return (methodNode.access & Opcodes.ACC_ABSTRACT) != 0;
    }

    public boolean isSynthetic() {
        return (methodNode.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 ||
                (methodNode.attrs != null &&
                        methodNode.attrs.stream().anyMatch(attribute -> attribute.type.equals(SYNTHETIC_ATTRIBUTE)));
    }

    public boolean isConstructor() {
        return methodNode.name.equals(CONSTRUCTOR_METHOD_NAME);
    }

    public boolean isClassOrInterfaceInitializer() {
        return methodNode.name.equals(CLASS_OR_INTERFACE_INIT_METHOD);
    }

    public ClassAdapter getClassAdapter() {
        return classAdapter;
    }

    MethodNode getMethodNode() {
        return methodNode;
    }

    public int getID() {
        return id;
    }

    public List<ExceptionTableEntryAdapter> getExceptionEntries() {
        return exceptionEntries;
    }

    public int addLocalVariableAtMethodEntry() {
        isChanged = true;
//        Label methodStart = new Label(); // inclusive
//        Label methodEnd = new Label(); // exclusive
//        this.methodNode.instructions.insert(new LabelNode(methodStart));
//        this.methodNode.instructions.add(new LabelNode(methodEnd));

        // get the local variable index and increase the number of local variables by one
        int newLocalVariableIndex = this.methodNode.maxLocals++;

        InsnList newInstList = new InsnList();
        newInstList.add(new TypeInsnNode(NEW, "pacovfor$jbc/frontend/graphadapters/Path"));
        newInstList.add(new InsnNode(DUP));
        newInstList.add(new MethodInsnNode(INVOKESPECIAL, "pacovfor$jbc/frontend/graphadapters/Path",
                CONSTRUCTOR_METHOD_NAME, "()V", false));
        newInstList.add(new VarInsnNode(ASTORE, newLocalVariableIndex));

        this.methodNode.instructions.insert(newInstList);

        return newLocalVariableIndex;
    }

    public void insertAddToPathInstructions(InstructionAdapter instruction, int localVariableIndex,
                                            int probePositionID, Boolean before) {
        isChanged = true;

        InsnList newInstList = new InsnList();
        newInstList.add(new VarInsnNode(ALOAD, localVariableIndex));
        newInstList.add(new LdcInsnNode(Integer.valueOf(probePositionID).toString()));
        newInstList.add(new MethodInsnNode(INVOKEVIRTUAL, "pacovfor$jbc/frontend/graphadapters/Path",
                "addProbePositionID", "(Ljava/lang/String;)V", false));

        if (before) {
            this.methodNode.instructions.insertBefore(instruction.getAsmInstruction(), newInstList);
        } else {
            this.methodNode.instructions.insert(instruction.getAsmInstruction(), newInstList);
        }
    }

    public boolean isChanged() {
        return this.isChanged;
    }

    private InsnList createNewCoverInstnList(int localVariableIndex) {
        InsnList newInstList = new InsnList();
        String methodName = Utils.getRelativeFilePathOfMethod(this);
        newInstList.add(new LdcInsnNode(methodName));
        newInstList.add(new VarInsnNode(ALOAD, localVariableIndex));
        newInstList.add(new MethodInsnNode(INVOKESTATIC, "pacovfor$jbc/frontend/graphadapters/GraphAdapter",
                "cover", "(Ljava/lang/String;Lpacovfor$jbc/frontend/graphadapters/Path;)V",
                false));

        return newInstList;
    }

    public void addTryFinallyBlockInstructions(int localVariableIndex) {
        // insert the instructions list before every return instruction
        instructionList.stream().
                filter(InstructionAdapter::isReturnInstruction).
                map(InstructionAdapter::getAsmInstruction).
                forEach(returnInstrNode -> {
                    // be careful to create a new instruction list every time because it is cleared after every
                    // insertion
                    InsnList instructionsToInsert = createNewCoverInstnList(localVariableIndex);
                    this.methodNode.instructions.insertBefore(returnInstrNode, instructionsToInsert);
                });

        /* create an exception table (if it does not already exist) and add an exception entry that
           catches "any" exception thrown within the block.
           Note that: we do not want to include the creation of path local variable.
           We want to get the following:
           Path path = new Path();
           try {
              ...
              cover(methodName, path)
              return;
              ...
           } finally {
              cover(methodName, path)
           }
         */

        // get the first original instruction in the method
        Label exceptionStartLabel = new Label(); // inclusive
        Label exceptionEndLabel = new Label(); // exclusive

        AbstractInsnNode firstInstruction = instructionList.get(0).getAsmInstruction();
        this.methodNode.instructions.insertBefore(firstInstruction, new LabelNode(exceptionStartLabel));
        this.methodNode.instructions.add(new LabelNode(exceptionEndLabel));
        this.methodNode.instructions.add(createNewCoverInstnList(localVariableIndex));
        this.methodNode.instructions.add(new InsnNode(ATHROW));

        this.methodNode.tryCatchBlocks.add(new TryCatchBlockNode(
                new LabelNode(exceptionStartLabel), new LabelNode(exceptionEndLabel), new LabelNode(exceptionEndLabel),
                null)
        );
    }


}
