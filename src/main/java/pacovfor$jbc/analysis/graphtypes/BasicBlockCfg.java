package pacovfor$jbc.analysis.graphtypes;

import pacovfor$jbc.analysis.FlowType;
import pacovfor$jbc.backend.asmadapters.InstructionAdapter;
import pacovfor$jbc.analysis.nodetypes.BasicBlock;
import pacovfor$jbc.graph.Edge;
import pacovfor$jbc.graph.Graph;
import pacovfor$jbc.graph.Node;
import org.apache.commons.text.StringEscapeUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BasicBlockCfg extends Graph<BasicBlock, FlowType> {

    public BasicBlockCfg() {
        super();
    }

    public boolean hasOutgoingExceptionalEdgesFrom(Node<BasicBlock> fromNode) {
        return this.outgoingEdges(fromNode).stream().anyMatch(edge ->
                edge.getData() == FlowType.EXCEPTIONAL_FLOW);
    }

    private void printBasicBlock(PrintStream out, BasicBlock basicBlock) {
        out.printf("  \"bb_%d_entry\" -> \"%s\";%n", basicBlock.id(), basicBlock.getFirstInstruction().index());

        List<InstructionAdapter> instrs = basicBlock.getInstructions();
        for (int i = 1; i < instrs.size(); i++) {
            out.printf("  \"%d\" -> \"%d\";%n", instrs.get(i - 1).index(), instrs.get(i).index());
        }

        out.printf("  \"%s\" -> \"bb_%d_exit\";%n", basicBlock.getLastInstruction().index(), basicBlock.id());
    }

    @Override
    public String toString() {
        return toString(true);
    }

    public String toString(boolean printInstructions) {
        OutputStream baOut = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baOut);

        out.println("digraph model {");
        out.println("  graph [ rankdir = \"TB\", ranksep=\"0.4\", nodesep=\"0.2\" ];");
        out.println("  node [ fontname = \"Helvetica\", fontsize=\"12.0\"," + " margin=\"0.07\" ];");
        out.println("  edge [ fontname = \"Helvetica\", fontsize=\"12.0\"," + " margin=\"0.05\" ];");
        // initial state
        out.println("  \"\" [ shape = \"point\", height=\"0.1\" ];");

        // print nodes
        out.println("  {");
        out.println("    node [ shape = \"box\"]");
        Set<Node<BasicBlock>> nodes = this.getAllNodes();
        for (Node<BasicBlock> basicBlockNode : nodes) {
            BasicBlock bb = basicBlockNode.getData();
            out.printf("    \"bb_%d_entry\" [shape = \"point\" label=\"\" color=\"black\"];%n", bb.id());

            for (InstructionAdapter instr : basicBlockNode.getData().getInstructions()) {
                if (!printInstructions) {
                    out.println("    \"" + instr.index() + "\";");
                } else {
                    String sourceString = instr.index() + ": " + instr;
                    out.println("    \"" + instr.index() + "\" [label =<" +
                            StringEscapeUtils.escapeHtml3(sourceString).replace("\n", "<br/>") + ">];");
                }
            }

            out.printf("    \"bb_%d_exit\" [shape = \"point\" label=\"\" color=\"black\"];%n", bb.id());
        }

        out.println("  }");

        // print clusters
        for (Node<BasicBlock> basicBlockNode : nodes) {
            BasicBlock bb = basicBlockNode.getData();
            out.printf("  subgraph cluster_%d {%n", bb.id());
            out.printf("  label = \"bb_%d\"%n", bb.id());
            out.printf("    \"bb_%d_entry\";%n", bb.id());
            for (InstructionAdapter instr : basicBlockNode.getData().getInstructions()) {
                out.println("    \"" + instr.index() + "\";");
            }
            out.printf("    \"bb_%d_exit\";%n", bb.id());
            out.println("  }");
        }


        Node<BasicBlock> rootNode = this.getRoot();

        // print an edge to the root state
        out.println("  \"\" -> \"" + String.format("bb_%d_entry", rootNode.getData().id()) + "\";");

        // create a discovered set of nodes and add the root to it
        Set<Node<BasicBlock>> discoveredNodes = new HashSet<>();
        discoveredNodes.add(rootNode);

        // print edges inside each cluster
        for (Node<BasicBlock> node : this.getAllNodes()) {
            printBasicBlock(out, node.getData());
        }

        // visit all edges.
        for (Edge<BasicBlock, FlowType> edge : this.getAllEdges()) {
            Node<BasicBlock> src = edge.getSource();
            Node<BasicBlock> dest = edge.getDestination();

            // - print normal outgoing edges from bb_(src)_exit to bb_(dest)_entry,
            //   where (src) and (dest) are src and dest basic block indices resp.
            // - print exceptional outgoing edges from bb_(src)_entry to bb_(dest)_entry,
            //   where (src) and (dest) are src and dest basic block indices resp.
            if (edge.getData() == FlowType.NORMAL_FLOW) {
                out.printf("  \"bb_%d_exit\" -> \"bb_%d_entry\";%n", src.getData().id(), dest.getData().id());
            } else if (edge.getData() == FlowType.EXCEPTIONAL_FLOW) {
                out.printf("  \"bb_%d_entry\" -> \"bb_%d_entry\" [color=\"red\"];%n",
                        src.getData().id(), dest.getData().id());

//                out.printf("  \"bb_%d_exit\" -> \"bb_%d_entry\" [color=\"red\"];%n",
//                        src.getData().id(), dest.getData().id());
            } else {
                throw new IllegalStateException("flow edge data is null");
            }

            // add source node and destination node to discovered set
            discoveredNodes.add(src);
            discoveredNodes.add(dest);
        }

        // log all undiscovered nodes
        for (Node<BasicBlock> node : this.getAllNodes()) {
            if (!discoveredNodes.contains(node)) {
                // undiscovered nodes were already printed in dot format under "nodes" part
                System.err.println("BasicBlockCfg [INFO] node: " + node.getData().getBlockID() + " has neither outgoing nor " +
                        "ingoing edges.");
            }
        }

        // print a rectangle around basic blocks
        out.println();
        out.println("}");
        out.flush();
        out.close();

        return baOut.toString();
    }

}
