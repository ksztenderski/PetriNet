package petrinet;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Representation of places with tokens in a Petri net.
 * Place is present in the net only if it has positive number of tokens.
 *
 * @param <T> type of places in net.
 */
public class PetriNet<T> {
    private HashMap<T, Integer> net;
    private ReentrantLock fireTransitionLock;
    private Condition noTransitionEnabled;

    public PetriNet(Map<T, Integer> initial, boolean fair) {
        // Creating lock with specified fairness.
        fireTransitionLock = new ReentrantLock(fair);

        //Creating conditions for threads to wait on.
        noTransitionEnabled = fireTransitionLock.newCondition();

        // Adding initial places to map.
        net = new HashMap<>(initial);
    }

    /**
     * Adds new reached markings to the set of already reached.
     *
     * @param transitions collection of transitions to fire
     * @param reached     set of reached markings
     * @param petriNet    representation of current marking
     */
    private void reaching(Collection<Transition<T>> transitions, Set<Map<T, Integer>> reached, PetriNet<T> petriNet) {
        // Iterating over collection of transitions.
        for (Transition<T> transition : transitions) {
            // Creating copy of current marking.
            PetriNet<T> newNet = new PetriNet<>(petriNet.net, false);
            if (newNet.fireTransition(transition) && !reached.contains(newNet.net)) {
                // If reached marking is new adding it to the set of reached markings.
                reached.add(newNet.net);
                reaching(transitions, reached, newNet);
            }
        }
    }

    /**
     * Calculates all reachable markings from current marking.
     *
     * @param transitions collection of transitions to fire
     * @return set of reached markings
     */
    public Set<Map<T, Integer>> reachable(Collection<Transition<T>> transitions) {
        Set<Map<T, Integer>> reached = new HashSet<>();

        // Adding starting marking to the set of reached markings.
        reached.add(new HashMap<>(net));

        // Adding all markings reachable from starting marking directly and indirectly.
        reaching(transitions, reached, this);

        return reached;
    }

    /**
     * Checks if given transition is enabled.
     *
     * @param transition given transition.
     * @return true if transition is enabled, false otherwise.
     */
    private boolean isTransitionEnabled(Transition<T> transition) {
        try {
            fireTransitionLock.lock();

            // Checking if in every place connected with the given transition by input arc
            // number of tokens is greater or equal to weight of that arc.
            for (Map.Entry<T, Integer> entry : transition.input.entrySet()) {
                if (net.get(entry.getKey()) == null || net.get(entry.getKey()) < entry.getValue()) {
                    return false;
                }
            }

            // Checking if in every place connected with the given transition by inhibitor arc
            // number of tokens is equal 0.
            for (T entry : transition.inhibitor) {
                if (net.get(entry) != null) {
                    return false;
                }
            }
        } finally {
            fireTransitionLock.unlock();
        }
        return true;
    }

    /**
     * Fires given transition if it is enabled.
     *
     * @param transition given transition
     * @return true if transition was successfully fired, false otherwise
     */
    private boolean fireTransition(Transition<T> transition) {
        try {
            fireTransitionLock.lock();

            if (isTransitionEnabled(transition)) {
                // Subtracting from every place connected with given transition by input arc
                // number of tokens equal to the weight of that arc.
                for (Map.Entry<T, Integer> entry : transition.input.entrySet()) {
                    int newValue = net.get(entry.getKey()) - entry.getValue();

                    if (newValue > 0) {
                        net.replace(entry.getKey(), newValue);
                    }
                    else {
                        net.remove(entry.getKey());
                    }
                }

                // Adding to every place connected with given transition by output arc
                // number of tokens equal to the weight of that arc.
                for (Map.Entry<T, Integer> entry : transition.output.entrySet()) {
                    net.putIfAbsent(entry.getKey(), 0);
                    net.replace(entry.getKey(), net.get(entry.getKey()) + entry.getValue());
                }

                // Setting number of tokens in every place connected with the given transition
                // by reset arc to 0.
                for (T entry : transition.reset) {
                    net.remove(entry);
                }
                return true;
            }
            else return false;
        } finally {
            fireTransitionLock.unlock();
        }
    }

    /**
     * Checks if any of the given transitions is enabled.
     *
     * @param transitions collection of given transitions
     * @return true if in collection exists enable transition, false otherwise
     */
    private boolean isAnyTransitionEnabled(Collection<Transition<T>> transitions) {
        for (Transition<T> transition : transitions) {
            if (isTransitionEnabled(transition)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Fires one of the enabled transitions from the given collection of transitions (nondeterministically).
     * If none of the given transitions is enabled thread waits until it changes.
     *
     * @param transitions collection of given transitions
     * @return fired transition
     * @throws InterruptedException when waiting thread is interrupted
     */
    public Transition<T> fire(Collection<Transition<T>> transitions) throws InterruptedException {
        try {
            fireTransitionLock.lock();

            Transition<T> chosen = null;

            while (!isAnyTransitionEnabled(transitions)) {
                // Waiting until some transition is enabled.
                noTransitionEnabled.await();
            }

            for (Transition<T> transition : transitions) {
                if (fireTransition(transition)) {
                    chosen = transition;
                    break;
                }
            }

            noTransitionEnabled.signal();

            return chosen;
        } finally {
            fireTransitionLock.unlock();
        }
    }
}