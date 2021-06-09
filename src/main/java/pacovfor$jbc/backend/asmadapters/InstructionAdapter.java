package pacovfor$jbc.backend.asmadapters;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InstructionAdapter {

    private final MethodAdapter methodAdapter;
    private final int instructionIndex;
    private final AbstractInsnNode asmInstruction;
    private List<InstructionAdapter> targetLabels;
    private boolean isUsedLabel;
    private final String stringRepresentation;

    @Override
    public String toString() {
        return this.stringRepresentation;
    }

    InstructionAdapter(MethodAdapter methodAdapter, int instructionIndex, AbstractInsnNode asmInstruction,
                       String stringRepresentation) {
        if (methodAdapter == null || instructionIndex < 0 || asmInstruction == null || stringRepresentation == null) {
            throw new IllegalArgumentException("one of the following arguments is invalid: " +
                    "method adaptor, instruction index, asm instruction, stringRepresentation");
        }

        this.methodAdapter = methodAdapter;
        this.instructionIndex = instructionIndex;
        this.asmInstruction = asmInstruction;
        this.targetLabels = null;
        this.isUsedLabel = false;
        this.stringRepresentation = stringRepresentation;
    }

    public MethodAdapter getMethodAdapter() {
        return methodAdapter;
    }

    public List<InstructionAdapter> getTargetLabels() {
        if (!isJumpInstruction()) {
            throw new IllegalStateException("getting labels is not allowed, because it is not a jump instruction");
        }

        return this.targetLabels;
    }

    public boolean isLookupSwitch() {
        return this.asmInstruction.getOpcode() == Opcodes.LOOKUPSWITCH;
    }

    public boolean isTableSwitch() {
        return this.asmInstruction.getOpcode() == Opcodes.TABLESWITCH;
    }

    public boolean isGoto() {
        return this.asmInstruction.getOpcode() == Opcodes.GOTO;
    }

    public boolean isIF_X() {
        int opcode = this.asmInstruction.getOpcode();
        switch (opcode) {
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                return true;
            default:
                return false;
        }
    }

    void addTargetLabel(InstructionAdapter label) {
        if (!isJumpInstruction()) {
            throw new IllegalStateException("adding label is not allowed, because it is not a jump instruction");
        }

        if (this.targetLabels == null) {
            this.targetLabels = new ArrayList<>();
        }

        this.targetLabels.add(label);
    }

    public boolean isRealInstruction() {
        return asmInstruction.getOpcode() >= 0 || isUsedLabel;
    }

    public boolean isJumpInstruction() {
        return asmInstruction.getType() == AbstractInsnNode.JUMP_INSN ||
                asmInstruction.getType() == AbstractInsnNode.TABLESWITCH_INSN ||
                asmInstruction.getType() == AbstractInsnNode.LOOKUPSWITCH_INSN;
    }

    public boolean isReturnInstruction() {
        int opcode = asmInstruction.getOpcode();
        return opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN ||
                opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN || opcode == Opcodes.RETURN;
    }

    public boolean isThrowInstruction() {
        return asmInstruction.getOpcode() == Opcodes.ATHROW;
    }

    public boolean isLabel() {
        return asmInstruction.getType() == AbstractInsnNode.LABEL;
    }

    public boolean isUsedLabel() {
        if (!isLabel()) {
            throw new IllegalStateException("can not call isUsedLabel. the instruction is not a label");
        }

        return isUsedLabel;
    }

    public void setUsedLabel(boolean isUsedLabel) {
        if (!isLabel()) {
            throw new IllegalStateException("can not call setUsedLabel. the instruction is not a label");
        }

        this.isUsedLabel = isUsedLabel;
    }

    public int index() {
        return instructionIndex;
    }

    AbstractInsnNode getAsmInstruction() {
        return this.asmInstruction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstructionAdapter that = (InstructionAdapter) o;
        return Objects.equals(this.asmInstruction, that.asmInstruction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.asmInstruction);
    }
}
