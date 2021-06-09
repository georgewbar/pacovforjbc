package pacovfor$jbc.analysis;

import pacovfor$jbc.backend.asmadapters.ClassAdapter;
import pacovfor$jbc.backend.asmadapters.ExceptionTableEntryAdapter;
import pacovfor$jbc.backend.asmadapters.InstructionAdapter;
import pacovfor$jbc.backend.asmadapters.MethodAdapter;
import pacovfor$jbc.analysis.graphtypes.BasicBlockCfg;
import pacovfor$jbc.analysis.graphtypes.SimpleCfg;
import pacovfor$jbc.analysis.nodetypes.BasicBlock;
import pacovfor$jbc.analysis.nodetypes.ProbePosition;
import pacovfor$jbc.analysis.graphtypes.ProbePositionCfg;
import pacovfor$jbc.classloaders.ClassLoaderAdapter;
import pacovfor$jbc.graph.Edge;
import pacovfor$jbc.graph.Node;
import pacovfor$jbc.utils.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CfgBuilder {

    /**
     * Builds simple cfg using methodAdapter
     */
    public static SimpleCfg buildSimpleCfg(MethodAdapter methodAdapter) {
        SimpleCfg simpleCfg = new SimpleCfg();
        List<InstructionAdapter> instructions = methodAdapter.getRealInstructions();

        // set root of cfg
        Node<InstructionAdapter> rootNode = new Node<>(instructions.get(0));
        simpleCfg.addNode(rootNode);

        simpleCfg.setRoot(rootNode);

        // construct cfg
        for (int i = 0; i < instructions.size(); i++) {
            InstructionAdapter instr = instructions.get(i);
            simpleCfg.addNode(new Node<>(instr));

            if (instr.isIF_X()) {
                // if jump instruction: IF_X, add outgoing edges from instruction to labels,
                // and one outgoing instruction to the next "fall-through" instruction if any (i.e.,
                // if the current instruction is not the last instruction)

                // "label" edges
                for (InstructionAdapter label : instr.getTargetLabels()) {
                    simpleCfg.addNode(new Node<>(label));
                    simpleCfg.addEdge(new Node<>(instr), new Node<>(label), FlowType.NORMAL_FLOW);
                }

                // "fall-through" edge
                if (i < instructions.size() - 1) {
                    simpleCfg.addNode(new Node<>(instructions.get(i + 1)));
                    simpleCfg.addEdge(new Node<>(instr), new Node<>(instructions.get(i + 1)),
                            FlowType.NORMAL_FLOW);
                }
            } else if (instr.isGoto() || instr.isLookupSwitch() || instr.isTableSwitch()) {
                // if jump instruction: goto, lookupSwitch, or tableSwitch, add outgoing
                // edges from instruction to labels

                for (InstructionAdapter label : instr.getTargetLabels()) {
                    simpleCfg.addNode(new Node<>(label));
                    simpleCfg.addEdge(new Node<>(instr), new Node<>(label), FlowType.NORMAL_FLOW);
                }

            } else if (instr.isReturnInstruction() || instr.isThrowInstruction()) {
                // if return instruction or throw instruction, do NOT add outgoing edges from instruction.
                // i.e., do nothing

            } else {
                // add one edge this instruction to the following instruction if they exist (i.e.,
                // if the current instruction is not the last instruction)
                if (i < instructions.size() - 1) {
                    simpleCfg.addNode(new Node<>(instructions.get(i + 1)));
                    simpleCfg.addEdge(new Node<>(instr), new Node<>(instructions.get(i + 1)),
                            FlowType.NORMAL_FLOW);
                }
            }
        }

        return simpleCfg;
    }

    private static int findFirstNonLabelInstrFrom(List<InstructionAdapter> instructions, int from) {
        for (int i = from; i < instructions.size(); i++) {
            if (!instructions.get(i).isLabel()) {
                return i;
            }
        }
        return instructions.size();
    }

    private static int findFirstLabelFrom(List<InstructionAdapter> instructions, int from) {
        for (int i = from; i < instructions.size(); i++) {
            if (instructions.get(i).isLabel()) {
                return i;
            }
        }

        return instructions.size();
    }

    private static int findFirstJumpReturnThrowInstrFrom(List<InstructionAdapter> instructions, int from) {
        for (int i = from; i < instructions.size(); i++) {
            InstructionAdapter instr = instructions.get(i);
            if (instr.isJumpInstruction() || instr.isReturnInstruction() || instr.isThrowInstruction()) {
                return i;
            }
        }
        return instructions.size();
    }

    private static boolean isLastBlock(int firstLabelInstrIndex, int firstJumpReturnThrowInstrIndex, int instrsSize) {
        return firstLabelInstrIndex == instrsSize && firstJumpReturnThrowInstrIndex == instrsSize;
    }

    private static BasicBlock getBasicBlockOf(List<BasicBlock> basicBlocks, InstructionAdapter instruction) {
        List<BasicBlock> bbs = basicBlocks.stream().
                filter(basicBlock -> basicBlock.hasInstruction(instruction)).
                collect(Collectors.toList());

        if (bbs.isEmpty()) {
            throw new IllegalStateException("there should be one basic block having instruction " + instruction);
        } else if (bbs.size() > 1) {
            throw new IllegalStateException("there should not be more than one basic having the same instruction " +
                    instruction);
        }

        return bbs.get(0);
    }

    private static List<BasicBlock> getAllBlocksInRange(List<BasicBlock> bbs, InstructionAdapter label,
                                                        InstructionAdapter endInstr) {

        List<BasicBlock> wantedBBs = bbs.stream().filter(bb -> bb.getFirstInstruction().index() >= label.index() &&
                bb.getLastInstruction().index() <= endInstr.index()).collect(Collectors.toList());

        // sanity check
        List<BasicBlock> toCheckBBs = bbs.stream().filter(bb -> bb.getFirstInstruction().index() >= label.index() &&
                bb.getFirstInstruction().index() <= endInstr.index()).collect(Collectors.toList());

        if (!wantedBBs.equals(toCheckBBs)) {
            throw new IllegalStateException("something is wrong with the blocks");
        }

        return wantedBBs;
    }

    public static BasicBlockCfg buildBasicBlockCfg(MethodAdapter methodAdapter, boolean constructTryCatchEdges) {
        // build a simpleCfg to calculate the normal-flow outgoing edges.
        SimpleCfg simpleCfg = buildSimpleCfg(methodAdapter);
        List<InstructionAdapter> instructions = methodAdapter.getRealInstructions();

        List<BasicBlock> basicBlocks = new ArrayList<>();
        BasicBlockCfg basicBlockCfg = new BasicBlockCfg();
        int basicBlockID = 0;
        int basicBlockStart = 0; // inclusive

        while (true) {

            // find first non-label instruction
            int firstNonLabelIndex = findFirstNonLabelInstrFrom(instructions, basicBlockStart);
            if (firstNonLabelIndex == instructions.size()) {
                // INVARIANT: there are no more blocks to process
                break;
            }

            int firstLabelInstrIndex = findFirstLabelFrom(instructions, firstNonLabelIndex);
            int firstJumpReturnThrowInstrIndex = findFirstJumpReturnThrowInstrFrom(instructions, firstNonLabelIndex);

            int basicBlockEnd;
            if (isLastBlock(firstLabelInstrIndex, firstJumpReturnThrowInstrIndex, instructions.size())) {
                basicBlockEnd = instructions.size() - 1;
            } else {
                // min(index before label, index of jump instr)
                basicBlockEnd = Math.min(firstLabelInstrIndex - 1, firstJumpReturnThrowInstrIndex);
            }

            // note that sublist "to" (index) is exclusive. Thus the "+ 1"
            List<InstructionAdapter> basicBlockInstrs = instructions.subList(basicBlockStart, basicBlockEnd + 1);

            // create a basic block and add it to the list of basic blocks
            BasicBlock basicBlock = new BasicBlock(basicBlockID, methodAdapter, instructions.get(basicBlockStart),
                    instructions.get(basicBlockEnd), basicBlockInstrs);

            basicBlocks.add(basicBlock);
            basicBlockCfg.addNode(new Node<>(basicBlock));

            // update basic block start and id
            basicBlockStart = basicBlockEnd + 1;
            basicBlockID++;
        }

        // set root
        basicBlockCfg.setRoot(new Node<>(basicBlocks.get(0)));

        // add normal edges to the basicBlockCfg
        for (BasicBlock bb : basicBlocks) {
            InstructionAdapter lastInstr = bb.getLastInstruction();

            for (Edge<InstructionAdapter, FlowType> edge : simpleCfg.outgoingEdges(new Node<>(lastInstr))) {
                InstructionAdapter outgoingInstruction = edge.getDestination().getData();
                // get basic block that has outgoing instruction
                BasicBlock containingBB = getBasicBlockOf(basicBlocks, outgoingInstruction);
                basicBlockCfg.addEdge(new Node<>(bb), new Node<>(containingBB), FlowType.NORMAL_FLOW);
            }
        }

        if (constructTryCatchEdges) {
            for (ExceptionTableEntryAdapter exceptionEntry : methodAdapter.getExceptionEntries()) {
                InstructionAdapter startLabel = exceptionEntry.getStartLabel();
                InstructionAdapter endInstr = exceptionEntry.getEndInstruction();
                InstructionAdapter handlerLabel = exceptionEntry.getHandlerLabel();

                List<BasicBlock> exceptionBlocks = getAllBlocksInRange(basicBlocks, startLabel, endInstr);
                BasicBlock handlerBlock = getBasicBlockOf(basicBlocks, handlerLabel);
                for (BasicBlock bb : exceptionBlocks) {
                    basicBlockCfg.addEdge(new Node<>(bb), new Node<>(handlerBlock), FlowType.EXCEPTIONAL_FLOW);
                }
            }
        }

        return basicBlockCfg;
    }

    private static Optional<ProbePosition> getEntryProbePositionOfBB(List<ProbePosition> probePositions,
                                                                     int basicBLockIndex) {
        return probePositions.stream().
                filter(probePosition -> probePosition.basicBlockIndex() == basicBLockIndex && probePosition.isEntry()).
                findFirst();
    }

    private static ProbePosition getExitProbePositionOfBB(List<ProbePosition> probePositions, int basicBLockIndex) {
        return probePositions.stream().
                filter(probePosition -> probePosition.basicBlockIndex() == basicBLockIndex && probePosition.isExit()).
                findFirst().
                orElseThrow(() -> {
                    throw new IllegalStateException("there is no entry probe position in block " + basicBLockIndex);
                });
    }

    public static ProbePositionCfg buildProbePositionCfg(MethodAdapter methodAdapter, boolean constructTryCatchEdges,
                                                         boolean constructTwoProbePositionsForExcBB) {
        BasicBlockCfg bbCfg = buildBasicBlockCfg(methodAdapter, true);

        ProbePositionCfg probePositionCfg = new ProbePositionCfg();
        // first pass: create probe positions of each basic block.
        List<ProbePosition> probePositions = new ArrayList<>();
        int probePositionId = 0;
        for (Node<BasicBlock> bbNode : bbCfg.getAllNodes()) {
            BasicBlock bb = bbNode.getData();

            InstructionAdapter lastInstr = bb.getLastInstruction(); // last instruction can NOT be a label
            // sanity check
            if (lastInstr.isLabel()) {
                System.out.println("GraphAdapter [ERROR]: last instruction must not be a label");
                throw new IllegalStateException("last instruction must not be a label");
            }

            if (!constructTryCatchEdges && !constructTwoProbePositionsForExcBB) {
                ProbePosition exitProbePosition = new ProbePosition(probePositionId++,
                        methodAdapter, bb, false, true, lastInstr);

                probePositions.add(exitProbePosition);
                probePositionCfg.addNode(new Node<>(exitProbePosition));
            } else {
                // if basic block does not have exceptional outgoing edges, add exit probe position node only.
                // Otherwise, add entry and exit probe positions nodes, and add a normal flow edge from entry to exit nodes.
                if (!bbCfg.hasOutgoingExceptionalEdgesFrom(bbNode)) {
                    ProbePosition exitProbePosition = new ProbePosition(probePositionId++,
                            methodAdapter, bb, false, true, lastInstr);

                    probePositions.add(exitProbePosition);
                    probePositionCfg.addNode(new Node<>(exitProbePosition));
                } else {
                    int firstNonLabelInstrIndex = findFirstNonLabelInstrFrom(bb.getInstructions(), 0);
                    InstructionAdapter firstNonLabelInstr = bb.getInstructions().get(firstNonLabelInstrIndex);

                    // if first and last instruction are the same, include it once in the graph
                    // and mark it as both entry and exit.
                    if (firstNonLabelInstr == lastInstr) {
                        ProbePosition entryExitProbePosition = new ProbePosition(probePositionId++,
                                methodAdapter, bb, true, true, lastInstr);

                        probePositions.add(entryExitProbePosition);
                        probePositionCfg.addNode(new Node<>(entryExitProbePosition));
                    } else {
                        ProbePosition entryProbePosition = new ProbePosition(probePositionId++,
                                methodAdapter, bb, true, false, firstNonLabelInstr);

                        ProbePosition exitProbePosition = new ProbePosition(probePositionId++,
                                methodAdapter, bb, false, true, lastInstr);

                        probePositions.add(entryProbePosition);
                        probePositionCfg.addNode(new Node<>(entryProbePosition));

                        probePositions.add(exitProbePosition);
                        probePositionCfg.addNode(new Node<>(exitProbePosition));

                        // IMPORTANT: add normal flow edge from entry to exit in probePositionCfg
                        probePositionCfg.addEdge(new Node<>(entryProbePosition), new Node<>(exitProbePosition),
                                FlowType.NORMAL_FLOW);
                    }
                }
            }

        }

        // set root to be entry probe position of first basic block if such an entry probe position exists.
        // Otherwise, set root to exit probe position of first basic block.
        ProbePosition rootData = probePositions.stream().
                filter(pb -> pb.basicBlockIndex() == 0 && pb.isEntry()).
                findFirst().
                orElseGet(() -> probePositions.stream().
                        filter(pb -> pb.basicBlockIndex() == 0 && pb.isExit()).
                        findFirst().
                        orElseThrow(() -> {
                            throw new IllegalStateException("neither entry nor exit probe position exists in " +
                                    "basic block 0");
                        }));

        probePositionCfg.setRoot(new Node<>(rootData));

        // second pass: add normal outgoing edges for every exit probe position
        for (ProbePosition probePosition : probePositions) {
            // if probe position is not an exit probe position, skip it
            if (!probePosition.isExit()) {
                continue;
            }

            // if probe position is an exit probe position, get the basic block containing it
            List<BasicBlock> bbs = bbCfg.getAllNodes().stream().map(Node::getData).collect(Collectors.toList());
            BasicBlock probePositionBasicBlock = getBasicBlockOf(bbs, probePosition.getInstruction());

            // get all normal outgoing edges from basic block of probe position
            List<BasicBlock> outgoingBBs = bbCfg.outgoingEdges(new Node<>(probePositionBasicBlock)).stream().
                    filter(edge -> edge.getData() == FlowType.NORMAL_FLOW).
                    map(Edge::getDestination).map(Node::getData).collect(Collectors.toList());

            // For each outgoing basic block, get the entry probe position of the block,
            // or else, get the exit probe position.
            // These are outgoing probe positions of the current position.
            List<ProbePosition> outgoingProbePositions = outgoingBBs.stream().
                    map(bb -> getEntryProbePositionOfBB(probePositions, bb.id()).
                            orElseGet(() -> getExitProbePositionOfBB(probePositions, bb.id()))).
                    collect(Collectors.toList());

            // add normal edges from current probe position to each destination entry (or exit) probe position
            // in outgoing probe positions.
            outgoingProbePositions.forEach(outgoingProbePosition ->
                    probePositionCfg.addEdge(
                            new Node<>(probePosition), new Node<>(outgoingProbePosition), FlowType.NORMAL_FLOW
                    )
            );
        }

        if (constructTryCatchEdges) {
            // third pass: add exceptional outgoing edges for every
            // - entry probe position, and
            // - exit probe position that is a return instruction or a throw instruction; The reason is that,
            //   for every exit probe positions that are Jumps, returns, throw instructions, the instrumentation
            //   instructions will be inserted before such exit probes. Jumps never throw exceptions (according
            //   to jvm spec), while returns and throw instructions could throw exceptions. That is why there
            //   should be an edge from exit probe positions that are either "return" or "throw" instruction,
            //   to indicate that the instruction have thrown an exception.
            for (ProbePosition probePosition : probePositions) {
                // if probe position is neither an entry nor a (return or throw) exit probe position, skip it.
                if (!   // neither
                        (probePosition.isEntry() ||
                                (probePosition.isExit() && (probePosition.getInstruction().isReturnInstruction()
                                        || probePosition.getInstruction().isThrowInstruction()))
                        )
                ) {
                    continue;
                }

                // if probe position is an entry or exit probe position, get the basic block containing it
                List<BasicBlock> bbs = bbCfg.getAllNodes().stream().map(Node::getData).collect(Collectors.toList());
                BasicBlock probePositionBasicBlock = getBasicBlockOf(bbs, probePosition.getInstruction());

                // get all exceptional outgoing edges from basic block of probe position
                List<BasicBlock> outgoingBBs = bbCfg.outgoingEdges(new Node<>(probePositionBasicBlock)).stream().
                        filter(edge -> edge.getData() == FlowType.EXCEPTIONAL_FLOW).
                        map(Edge::getDestination).map(Node::getData).collect(Collectors.toList());

                // For each outgoing basic block, get the entry probe position of the block,
                // or else, get the exit probe position.
                // These are outgoing probe positions of the current position.
                List<ProbePosition> outgoingProbePositions = outgoingBBs.stream().
                        map(bb -> getEntryProbePositionOfBB(probePositions, bb.id()).
                                orElseGet(() -> getExitProbePositionOfBB(probePositions, bb.id()))).
                        collect(Collectors.toList());

                // add exceptional edges from current entry or exit probe positions to each destination entry (or exit)
                // probe position in outgoing probe positions.
                outgoingProbePositions.forEach(outgoingProbePosition ->
                        probePositionCfg.addEdge(
                                new Node<>(probePosition), new Node<>(outgoingProbePosition), FlowType.EXCEPTIONAL_FLOW
                        )
                );
            }
        }

        // fourth pass: make all edges unique

        // create a new graph with unique edges:
        // - one edge -> should be the same
        // - at least one normal edge (and zero or more exc edges) -> merge all edges to one normal edge
        // - no normal edges and one or more exc edges -> merge all edges to one exc edge
        // Nodes and edges should be added to a new probePositionCfg
        ProbePositionCfg uniqueProbePositionCfg = new ProbePositionCfg();

        // set root
        uniqueProbePositionCfg.setRoot(probePositionCfg.getRoot());

        // group edges by tuple (source probe position id, destination probe position id).
        probePositionCfg.getAllNodes().forEach(uniqueProbePositionCfg::addNode);
        Map<Tuple<Integer, Integer>, List<Edge<ProbePosition, FlowType>>> groupedEdges = probePositionCfg.
                getAllEdges().stream().
                collect(Collectors.groupingBy(edge ->
                        new Tuple<>(edge.getSource().getData().getId(), edge.getDestination().getData().getId())));

        groupedEdges.forEach((fromTo, edges) -> {
            if (edges.size() == 1) {
                uniqueProbePositionCfg.addEdge(edges.get(0).getSource(), edges.get(0).getDestination(),
                        edges.get(0).getData());
            } else if (edges.size() > 1) {
                if (edges.stream().anyMatch(edge -> edge.getData() == FlowType.NORMAL_FLOW)) {
                    Edge<ProbePosition, FlowType> normalFlowEdge = edges.stream().
                            filter(edge -> edge.getData() == FlowType.NORMAL_FLOW).findFirst().
                            orElseThrow(() -> {
                                throw new IllegalStateException("a normal flow edge must exist");
                            });
                    uniqueProbePositionCfg.addEdge(normalFlowEdge.getSource(), normalFlowEdge.getDestination(),
                            normalFlowEdge.getData());
                } else {
                    // INVARIANT: no normal edges and more than one exc edges
                    Edge<ProbePosition, FlowType> excFlowEdge = edges.stream().
                            filter(edge -> edge.getData() == FlowType.EXCEPTIONAL_FLOW).findFirst().
                            orElseThrow(() -> {
                                throw new IllegalStateException("an exceptional flow edge must exist");
                            });
                    uniqueProbePositionCfg.addEdge(excFlowEdge.getSource(), excFlowEdge.getDestination(),
                            excFlowEdge.getData());
                } // else, number of edges is 0 -> do nothing
            }
        });

        return uniqueProbePositionCfg;
    }

    public static void main(String[] args) {
        ClassLoaderAdapter cla = new ClassLoaderAdapter("dump_result", "testnew-06-08", new ArrayList<>());
        List<String> classNames = cla.getAllClassNamesInPath();
        System.out.println(classNames);
        byte[] clz = cla.loadClassAsBytes(classNames.get(1));

        ClassAdapter ca = new ClassAdapter(clz, cla);
        List<MethodAdapter> methods = ca.getMethods();

        System.out.println(methods.get(0).getName());
//        SimpleCfg simpleCfg = CfgBuilder.buildSimpleCfg(methods.get(0));
//        System.out.println(simpleCfg.toString(true));

//        BasicBlockCfg basicBlockCfg = CfgBuilder.buildBasicBlockCfg(methods.get(0), true);
//        System.out.println(basicBlockCfg.toString(true));
//
        ProbePositionCfg probePositionCfg = CfgBuilder.buildProbePositionCfg(methods.get(0), true, true);
        System.out.println(probePositionCfg.toString(true));


//        System.out.println(methods.get(1).getFullName());
////        SimpleCfg simpleCfg = CfgBuilder.buildSimpleCfg(methods.get(0));
////        System.out.println(simpleCfg.toString());
//
//        basicBlockCfg = CfgBuilder.buildBasicBlockCfg(methods.get(1), true);
//        System.out.println(basicBlockCfg.toString(true));
////
//        probePositionCfg = CfgBuilder.buildProbePositionCfg(methods.get(1), true, true);
//        System.out.println(probePositionCfg.toString(true));

    }

}
