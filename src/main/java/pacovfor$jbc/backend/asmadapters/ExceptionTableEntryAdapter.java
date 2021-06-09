package pacovfor$jbc.backend.asmadapters;

import org.objectweb.asm.tree.TryCatchBlockNode;

public class ExceptionTableEntryAdapter {

    private final MethodAdapter methodAdapter;
    private final int index;
    private final TryCatchBlockNode tryCatchBlock;
    private final InstructionAdapter startLabel;
    private final InstructionAdapter endLabel;
    private final InstructionAdapter endInstruction;
    private final InstructionAdapter handlerLabel;

    ExceptionTableEntryAdapter(MethodAdapter methodAdapter, int index, TryCatchBlockNode tryCatchBlock) {
        this.methodAdapter = methodAdapter;
        this.index = index;
        this.tryCatchBlock = tryCatchBlock;

        // start label
        InstructionAdapter startLabel = methodAdapter.getInstructionAdapter(tryCatchBlock.start);
        startLabel.setUsedLabel(true);
        this.startLabel = startLabel;

        // end label and end instruction before end label
        InstructionAdapter endLabel = methodAdapter.getInstructionAdapter(tryCatchBlock.end);
        endLabel.setUsedLabel(true);
        this.endLabel = endLabel;
        this.endInstruction = methodAdapter.firstInstructionBefore(endLabel);

        // handler label
        InstructionAdapter handlerLabel = methodAdapter.getInstructionAdapter(tryCatchBlock.handler);
        handlerLabel.setUsedLabel(true);
        this.handlerLabel = handlerLabel;
    }

    public InstructionAdapter getStartLabel() {
        return startLabel;
    }

    public InstructionAdapter getEndInstruction() {
        return endInstruction;
    }

    public InstructionAdapter getHandlerLabel() {
        return handlerLabel;
    }

    public int getIndex() {
        return index;
    }

    public MethodAdapter getMethodAdapter() {
        return methodAdapter;
    }

    public InstructionAdapter getEndLabel() {
        return endLabel;
    }

    TryCatchBlockNode getTryCatchBlock() {
        return tryCatchBlock;
    }
}
