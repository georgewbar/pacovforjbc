package pacovfor$jbc.analysis;

import pacovfor$jbc.analysis.graphtypes.ProbePositionCfg;
import pacovfor$jbc.analysis.nodetypes.ProbePosition;
import pacovfor$jbc.backend.asmadapters.ClassAdapter;
import pacovfor$jbc.backend.asmadapters.MethodAdapter;
import pacovfor$jbc.graph.Node;

import java.util.List;
import java.util.stream.Collectors;

public class Instrumenter {

    public static void instrument(ClassAdapter classAdapter) {
        // instrument class by adding creating a class initializer (if not already exists) and
        // adding instructions at the beginning to load the cfg of all the methods in the class
        // from the file by calling the static methods of the GraphAdapter.
        classAdapter.addLoadCfgsInstns();

        // instrument each method in class
        for (MethodAdapter methodAdapter : classAdapter.getMethods()) {
            instrument(methodAdapter, true);
        }

    }

    public static void instrument(MethodAdapter methodAdapter, boolean saveMethodCfgToFile) {
        // build a ProbePositionCfg and a ProbePositionIDCfg
        ProbePositionCfg pbCfg = CfgBuilder.buildProbePositionCfg(methodAdapter, true, true);

        // add one local variable that keeps track of path at the beginning of the method
        // and get the local variable index
        int localVariableIndex = methodAdapter.addLocalVariableAtMethodEntry();

        // get all probe positions and instrument either before or after the probe position
        // depending on whether it is an entry probe position or exit probe position
        List<ProbePosition> probePositions = pbCfg.getAllNodes().stream().map(Node::getData).
                collect(Collectors.toList());

        final boolean before = true;
        final boolean after = false;
        /* If probe position is an entry, instructions that add the probe id to the path
           should be inserted before the instruction. If the probe position is both an entry
           and an exit instruction, the instruction is handled as an entry probe position.

           Otherwise, the instructions should be inserted either before or after the probe id
           depending on whether the instruction is a return, throw, jump instruction or not. i.e.,
           if the instruction is a return, throw, jump instruction, instrumentation should happen
           before the instruction. Otherwise, instrumentation should happen after the instruction. */
        for (ProbePosition probePosition : probePositions) {

            // if probePosition is both an entry and an exit, entry takes precedence.
            if (probePosition.isEntry()) {
                // entry takes precedence. instrument before the instruction.
                methodAdapter.insertAddToPathInstructions(probePosition.getInstruction(), localVariableIndex,
                        probePosition.getId(), before);
            } else if (probePosition.isExit()) {
                if (probePosition.getInstruction().isJumpInstruction() ||
                        probePosition.getInstruction().isReturnInstruction() ||
                        probePosition.getInstruction().isThrowInstruction()) {
                    methodAdapter.insertAddToPathInstructions(probePosition.getInstruction(), localVariableIndex,
                            probePosition.getId(), before);
                } else {
                    methodAdapter.insertAddToPathInstructions(probePosition.getInstruction(), localVariableIndex,
                            probePosition.getId(), after);
                }
            }
        }

        // add a try-finally block where the finally block calls GraphAdapter.cover(methodName, path),
        // where methodName is the filename that includes the cfg of the method being instrumented.
        methodAdapter.addTryFinallyBlockInstructions(localVariableIndex);

//        System.out.println(pbCfg.getAllNodes().size());

        if (saveMethodCfgToFile) {
            pbCfg.toProbePositionIDCfg(methodAdapter).printToFile();
        }
    }
}
