package de.flashheart.rlg.commander.misc;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.ValueGraph;

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

    /**
     * write for directed, valued graphs
     * b -> a [color="blue"]
     *
     * @param graph
     * @param state_map
     * @return
     */
    public static String write(final ValueGraph<String, String> graph, Map<String, String> state_map) {
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
                    .append(" -> ")
                    .append(e.nodeV())
                    .append(state_map.getOrDefault(e.nodeU() + "," + e.nodeV(), "")) // add coloring if necessary
                    .append(";\n");
        }

        sb.append("}");
        return sb.toString();
    }

    private static String get_node_format(String state) {
        return "[fillcolor = \"red\" fontcolor = \"yellow\" style = filled]";
    }
}
