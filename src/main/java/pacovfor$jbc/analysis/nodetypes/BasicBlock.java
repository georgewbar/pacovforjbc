package pacovfor$jbc.analysis.nodetypes;

import pacovfor$jbc.backend.asmadapters.InstructionAdapter;
import pacovfor$jbc.backend.asmadapters.MethodAdapter;

import java.util.List;
import java.util.Objects;

public class BasicBlock {

    private final int id;
    private final MethodAdapter methodAdapter;
    private final InstructionAdapter startInstr;
    private final InstructionAdapter endInstr;
    private final List<InstructionAdapter> instructions;

    public BasicBlock(int id, MethodAdapter methodAdapter,
                      InstructionAdapter startInstr, InstructionAdapter endInstr,
                      List<InstructionAdapter> instructions) {
        if (methodAdapter == null || startInstr == null || endInstr == null ||
                instructions == null || instructions.size() == 0) {
            throw new IllegalArgumentException("basic block argument(s) are illegal");
        }

        this.id = id;
        this.methodAdapter = methodAdapter;
        this.startInstr = startInstr;
        this.endInstr = endInstr;
        this.instructions = instructions;
    }

    public boolean hasInstruction(InstructionAdapter instruction) {
        return instructions.stream().anyMatch(instr -> instr == instruction);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasicBlock that = (BasicBlock) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public int id() {
        return this.id;
    }

    public String getBlockID() {
        return String.format("bb_%s_%d", methodAdapter.getFullName(), this.id);
    }

    public InstructionAdapter getFirstInstruction() {
        return this.startInstr;
    }

    public InstructionAdapter getLastInstruction() {
        return this.endInstr;
    }

    public MethodAdapter getMethodAdapter() {
        return methodAdapter;
    }

    public List<InstructionAdapter> getInstructions() {
        return instructions;
    }
}
