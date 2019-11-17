package petrinet;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Representation of a transition in a Petri net.
 * <p>Possible arcs:
 * input arc - from a place to the transition, subtracts from the place number of tokens equal to the weight of that arc
 * reset arc - sets number of tokens in the connected place to 0
 * inhibitor arc - requires from connected place to have 0 tokens
 * output arc - from the transition to a place, adds to the place number of tokens equal to the weight of that arc
 * </p>
 *
 * @param <T> type of places in the Petri net.
 */
public class Transition<T> {
    // Map of places connected by input arcs with their weights.
    HashMap<T, Integer> input;

    // Set of places connected by reset arcs.
    HashSet<T> reset;

    // Set of places connected by inhibitor arcs.
    HashSet<T> inhibitor;

    // Map of places connected by output arcs with their weights.
    HashMap<T, Integer> output;

    public Transition(Map<T, Integer> input, Collection<T> reset, Collection<T> inhibitor, Map<T, Integer> output) {
        this.input = new HashMap<>(input);
        this.reset = new HashSet<>(reset);
        this.inhibitor = new HashSet<>(inhibitor);
        this.output = new HashMap<>(output);
    }
}