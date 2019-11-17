package alternator;

import petrinet.PetriNet;
import petrinet.Transition;

import java.util.Collections;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;


/**
 * Program shows solution of mutual exclusion for 3 processes, with additional condition that
 * the same process cannot enter critical section 2 times in a row.
 * Program builds Petri net for this system. Writes how many markings are reachable from
 * the beginning state and checks if all are thread-safety.
 * Then program begins simulation of the system by starting 3 threads A, B, C which run in
 * infinite loops. Each thread in critical section writes its name and a period (.).
 * For each thread entry protocol is firing its starting transition and exit protocol is
 * firing its ending transition.
 * After 30 seconds from the start of the simulation the main thread interrupts all other threads.
 */
public class Main {
    private enum Place {
        A, B, C, PAST_A, PAST_B, PAST_C
    }

    private static class Alternate implements Runnable {
        private PetriNet<Place> net;
        Collection<Transition<Place>> startingTransition;
        Collection<Transition<Place>> endingTransition;

        Alternate(PetriNet<Place> net, Collection<Transition<Place>> startingTransition, Collection<Transition<Place>> endingTransition) {
            this.net = net;
            this.startingTransition = startingTransition;
            this.endingTransition = endingTransition;
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // Entry protocol.
                    net.fire(startingTransition);

                    // Critical section.
                    System.out.print(Thread.currentThread().getName());
                    System.out.print(".");

                    // Exit protocol
                    net.fire(endingTransition);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Transition<Place> getStartingTransition(Place place) {
        Set<Place> reset = new HashSet<>();
        Set<Place> inhibitor = new HashSet<>();
        Map<Place, Integer> output = Collections.singletonMap(place, 1);

        inhibitor.add(Place.A);
        inhibitor.add(Place.B);
        inhibitor.add(Place.C);

        switch (place) {
            case A:
                inhibitor.add(Place.PAST_A);
                reset.add(Place.PAST_B);
                reset.add(Place.PAST_C);
                break;

            case B:
                inhibitor.add(Place.PAST_B);
                reset.add(Place.PAST_A);
                reset.add(Place.PAST_C);
                break;

            case C:
                inhibitor.add(Place.PAST_C);
                reset.add(Place.PAST_A);
                reset.add(Place.PAST_B);
                break;

            default:
                throw new IllegalStateException("Unexpected value: " + place);
        }

        return new Transition<>(new HashMap<>(), reset, inhibitor, output);
    }

    private static Transition<Place> getEndingTransition(Place place) {
        Map<Place, Integer> input = Collections.singletonMap(place, 1);
        Set<Place> inhibitor = new HashSet<>();
        Map<Place, Integer> output = new HashMap<>();

        switch (place) {
            case A:
                inhibitor.add(Place.PAST_A);
                output.put(Place.PAST_A, 1);
                break;

            case B:
                inhibitor.add(Place.PAST_B);
                output.put(Place.PAST_B, 1);
                break;

            case C:
                inhibitor.add(Place.PAST_C);
                output.put(Place.PAST_C, 1);
                break;

            default:
                throw new IllegalStateException("Unexpected value: " + place);
        }

        return new Transition<>(input, new HashSet<>(), inhibitor, output);
    }

    private static <T> void putPositive(Map<T, Integer> map, T key, Integer value) {
        if (value > 0) {
            map.put(key, value);
        }
    }

    private static Map<Place, Integer> marking(int a, int b, int c, int pastA, int pastB, int pastC) {
        Map<Place, Integer> result = new HashMap<>();
        putPositive(result, Place.A, a);
        putPositive(result, Place.B, b);
        putPositive(result, Place.C, c);
        putPositive(result, Place.PAST_A, pastA);
        putPositive(result, Place.PAST_B, pastB);
        putPositive(result, Place.PAST_C, pastC);
        return result;
    }

    public static void main(String[] args) {
        Map<Place, Integer> begin = new HashMap<>();

        // At the beginning all places in the net have 0 tokens.
        PetriNet<Place> net = new PetriNet<>(begin, false);

        HashMap<Place, Transition<Place>> startingTransitions = new HashMap<>(3);
        HashMap<Place, Transition<Place>> endingTransitions = new HashMap<>(3);

        startingTransitions.put(Place.A, getStartingTransition(Place.A));
        startingTransitions.put(Place.B, getStartingTransition(Place.B));
        startingTransitions.put(Place.C, getStartingTransition(Place.C));

        endingTransitions.put(Place.A, getEndingTransition(Place.A));
        endingTransitions.put(Place.B, getEndingTransition(Place.B));
        endingTransitions.put(Place.C, getEndingTransition(Place.C));

        Collection<Transition<Place>> transitions = new HashSet<>();
        transitions.addAll(startingTransitions.values());
        transitions.addAll(endingTransitions.values());

        Set<Map<Place, Integer>> reachable = net.reachable(transitions);

        Collection<Map<Place, Integer>> safeMarkings = new HashSet<>();
        safeMarkings.add(marking(0, 0, 0, 0, 0, 0));
        safeMarkings.add(marking(1, 0, 0, 0, 0, 0));
        safeMarkings.add(marking(0, 1, 0, 0, 0, 0));
        safeMarkings.add(marking(0, 0, 1, 0, 0, 0));
        safeMarkings.add(marking(0, 0, 0, 1, 0, 0));
        safeMarkings.add(marking(0, 0, 0, 0, 1, 0));
        safeMarkings.add(marking(0, 0, 0, 0, 0, 1));

        System.out.println("Number of reachable markings: " + reachable.size());

        // Checking safety.
        boolean safe;
        for (Map<Place, Integer> marking : reachable) {
            safe = false;
            for (Map<Place, Integer> safeMarking : safeMarkings) {
                if (marking.equals(safeMarking)) {
                    safe = true;
                    break;
                }
            }
            if (!safe) {
                System.err.println("Unsafe marking:" + marking);
            }
        }

        try {
            Thread threadA = new Thread(new Alternate(net, Collections.singleton(startingTransitions.get(Place.A)),
                    Collections.singleton(endingTransitions.get(Place.A))), "A");
            Thread threadB = new Thread(new Alternate(net, Collections.singleton(startingTransitions.get(Place.B)),
                    Collections.singleton(endingTransitions.get(Place.B))), "B");
            Thread threadC = new Thread(new Alternate(net, Collections.singleton(startingTransitions.get(Place.C)),
                    Collections.singleton(endingTransitions.get(Place.C))), "C");

            threadA.start();
            threadB.start();
            threadC.start();

            Thread.sleep(30000);

            threadA.interrupt();
            threadB.interrupt();
            threadC.interrupt();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread interrupted");
        }
    }
}
