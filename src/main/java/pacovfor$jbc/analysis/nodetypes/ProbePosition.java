package pacovfor$jbc.analysis.nodetypes;

import pacovfor$jbc.backend.asmadapters.InstructionAdapter;
import pacovfor$jbc.backend.asmadapters.MethodAdapter;

import java.util.Objects;

public class ProbePosition {

    private final int id;
    private final MethodAdapter methodAdapter;
    private final BasicBlock containingBasicBlock;
    private final boolean isEntry;
    private final boolean isExit;
    private final InstructionAdapter instruction;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProbePosition that = (ProbePosition) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public ProbePosition(int id, MethodAdapter methodAdapter, BasicBlock containingBasicBlock, boolean isEntry,
                         boolean isExit, InstructionAdapter instruction) {

        if (!isEntry && !isExit) {
            throw new IllegalArgumentException("probe position should be at least an exit position or " +
                    "an entry position");
        }

        if (methodAdapter == null || containingBasicBlock == null || instruction == null) {
            throw new IllegalArgumentException("one of the arguments is null");
        }

        this.id = id;
        this.methodAdapter = methodAdapter;
        this.containingBasicBlock = containingBasicBlock;
        this.isEntry = isEntry;
        this.isExit = isExit;
        this.instruction = instruction;
    }

    public BasicBlock containingBasicBlock() { return this.containingBasicBlock; }

    public int basicBlockIndex() {
        return containingBasicBlock.id();
    }

    public boolean isEntry() {
        return isEntry;
    }

    public boolean isExit() {
        return isExit;
    }

    public InstructionAdapter getInstruction() {
        return instruction;
    }

    public MethodAdapter getMethodAdapter() {
        return methodAdapter;
    }

    public int getId() {
        return id;
    }
}
