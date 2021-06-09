package pacovfor$jbc.analysis.graphtypes;

import pacovfor$jbc.analysis.FlowType;
import pacovfor$jbc.backend.asmadapters.InstructionAdapter;
import pacovfor$jbc.graph.Edge;
import pacovfor$jbc.graph.Graph;
import pacovfor$jbc.graph.Node;
import org.apache.commons.text.StringEscapeUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

public class SimpleCfg extends Graph<InstructionAdapter, FlowType> {

    public SimpleCfg() {
        super();
    }

    @Override
    public String toString() {
        return this.toString(true);
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

        // print all nodes and their instructions if needed
        out.println("  {");
        out.println("    node [ shape = \"box\"]");
        Set<Node<InstructionAdapter>> nodes = this.getAllNodes();
        for (Node<InstructionAdapter> node : nodes) {
            int instrIndex = node.getData().index();
            if (!printInstructions) {
                out.printf("    \"%d\";%n", instrIndex);
            } else {
                String instrString = instrIndex + ": " + node.getData();
                out.println("    \"" + node.getData().index() + "\" [label =<" +
                        StringEscapeUtils.escapeHtml3(instrString).replace("\n", "<br/>") + ">]");
            }
        }
        out.println("  }");

        Node<InstructionAdapter> rootNode = this.getRoot();
        // print an edge to the root state
        out.println("  \"\" -> \"" + rootNode.getData().index() + "\";");


        // create a discovered set of nodes and add the root to it
        Set<Node<InstructionAdapter>> discoveredNodes = new HashSet<>();
        discoveredNodes.add(rootNode);

        // visit all edges.
        for (Edge<InstructionAdapter, FlowType> edge : this.getAllEdges()) {
            Node<InstructionAdapter> src = edge.getSource();
            Node<InstructionAdapter> dest = edge.getDestination();
            out.println("  \"" + src.getData().index() + "\"\t-> \"" + dest.getData().index() + "\";");

            // add source node and destination node to discovered set
            discoveredNodes.add(edge.getSource());
            discoveredNodes.add(edge.getDestination());
        }

        // log all undiscovered nodes
        for (Node<InstructionAdapter> node : this.getAllNodes()) {
            if (!discoveredNodes.contains(node)) {
                // undiscovered nodes were already printed in dot format under "nodes" part
                System.err.println("SimpleCfg [INFO] node: " + node.getData().index() + " has neither outgoing nor " +
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
