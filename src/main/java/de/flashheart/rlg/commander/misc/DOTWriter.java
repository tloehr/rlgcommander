package de.flashheart.rlg.commander.misc;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;

import java.util.Map;

public class DOTWriter {

    public static String write(final Graph<String> graph, Map<String, String> state_map) {
        final StringBuilder sb = new StringBuilder();
        sb.append("""
                strict graph G {
                    layout = circo
                """);

        for (String node : graph.nodes()) {
            sb.append("  ")
                    .append(node)
                    .append(state_map.getOrDefault(node, "")) // add coloring if necessary
                    .append(";\n");
        }

        for (EndpointPair<String> e : graph.edges()) {

            sb.append("  ")
                    .append(e.nodeU())
                    .append(" -- ")
                    .append(e.nodeV())
                    .append(";\n");
        }

        sb.append("}");
        return sb.toString();
    }

    private static String get_node_format(String state) {
        return "[fillcolor = \"red\" fontcolor = \"yellow\" style = filled]";
    }
}
