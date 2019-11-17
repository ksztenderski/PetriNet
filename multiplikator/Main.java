package multiplikator;

import petrinet.PetriNet;
import petrinet.Transition;

import java.util.Collections;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Scanner;

/**
 * Program builds Petri net, which multiplies 2 non-negative numbers from standard input.
 * At the beginning only places with tokens are Place.A and Place.B with A and B tokens respectively.
 * Product A * B is calculated by multiple firing transitions.
 * When calculation is finished:
 * - the ending transition is enabled
 * - all other transitions are disabled
 * - in Place.PRODUCT is A * B tokens
 * After building Petri net program starts 4 side threads, which fire all transition except the ending transition.
 * The main thread awaits finishing of calculation by firing the ending transition then writes product A * B.
 * After being interrupted each side thread writes the number of fired transitions.
 * <p>
 * Multiplication scheme:
 * - take 1 token from Place.B and fill Place.To_ADD with tokens from Place.A
 * - transport all tokens from Place.TO_ADD to Place.Product and create equal number of tokens in Place.A
 * repeat until the end of tokens in Place.B
 * <p>
 * Thread-safety:
 * - in any moment only 1 type of transition can be fired
 * - transporting tokens from Place.TO_ADD to Place.Product when number of tokens in Place.MUTEX == 0
 * - refilling Place.TO_ADD when number of tokens in Place.MUTEX == 1
 */
public class Main {
    private enum Place {
        TO_ADD, B, PRODUCT, MUTEX, A, END
    }

    private static class Multiplicate implements Runnable {
        private PetriNet<Place> net;
        private Collection<Transition<Place>> transitions;

        Multiplicate(PetriNet<Place> net, Collection<Transition<Place>> transitions) {
            this.net = net;
            this.transitions = transitions;
        }

        @Override
        public void run() {
            int firedTransitions = 0;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    net.fire(transitions);
                    ++firedTransitions;
                }
            } catch (InterruptedException e) {
                System.out.println(Thread.currentThread().getName() + " - " + firedTransitions + " fired transitions.");
            }
        }
    }

    private static Transition<Place> getAddingTransition(int value) {
        Map<Place, Integer> input = Collections.singletonMap(Place.TO_ADD, value);
        Set<Place> inhibitor = new HashSet<>();

        inhibitor.add(Place.MUTEX);
        inhibitor.add(Place.END);

        Map<Place, Integer> output = new HashMap<>(2);

        output.put(Place.PRODUCT, value);
        output.put(Place.A, value);

        return new Transition<>(input, new HashSet<>(), inhibitor, output);
    }

    private static Transition<Place> getFillingTransition(int value) {
        Map<Place, Integer> input = new HashMap<>(2);
        Set<Place> inhibitor = Collections.singleton(Place.END);
        Map<Place, Integer> output = new HashMap<>();

        input.put(Place.MUTEX, 1);
        input.put(Place.A, value);

        output.put(Place.MUTEX, 1);
        output.put(Place.TO_ADD, value);

        return new Transition<>(input, new HashSet<>(), inhibitor, output);
    }

    private static Transition<Place> getRestartTransition() {
        Map<Place, Integer> input = Collections.singletonMap(Place.B, 1);
        Set<Place> inhibitor = new HashSet<>(3);
        Map<Place, Integer> output = Collections.singletonMap(Place.MUTEX, 1);

        inhibitor.add(Place.MUTEX);
        inhibitor.add(Place.TO_ADD);
        inhibitor.add(Place.END);

        return new Transition<>(input, new HashSet<>(), inhibitor, output);
    }

    private static Transition<Place> getEndFillingTransition() {
        Map<Place, Integer> input = Collections.singletonMap(Place.MUTEX, 1);
        Set<Place> inhibitor = new HashSet<>(2);

        inhibitor.add(Place.A);
        inhibitor.add(Place.END);

        return new Transition<>(input, new HashSet<>(), inhibitor, new HashMap<>());
    }

    private static Transition<Place> getEndingTransition() {
        Collection<Place> inhibitor = new HashSet<>();
        inhibitor.add(Place.TO_ADD);
        inhibitor.add(Place.B);
        inhibitor.add(Place.MUTEX);

        Map<Place, Integer> output = new HashMap<>();
        output.put(Place.END, 1);
        output.put(Place.MUTEX, 1);

        return new Transition<>(new HashMap<>(), new HashSet<>(), inhibitor, output);
    }

    private static Map<Place, Integer> marking(int a, int b) {
        Map<Place, Integer> result = new HashMap<>();
        if (a > 0) {
            result.put(Place.A, a);
        }

        if (b > 0) {
            result.put(Place.B, b);
        }
        return result;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int A, B, value = 1;

        A = scanner.nextInt();
        B = scanner.nextInt();

        Map<Place, Integer> begin = marking(A, B);

        PetriNet<Place> net = new PetriNet<>(begin, false);

        Collection<Transition<Place>> endingTransition = Collections.singleton(getEndingTransition());
        Collection<Transition<Place>> transitions = new HashSet<>();

        while (value > 0 && value < Integer.MAX_VALUE) {
            transitions.add(getAddingTransition(value));
            transitions.add(getFillingTransition(value));

            value *= 2;
        }

        transitions.add(getRestartTransition());
        transitions.add(getEndFillingTransition());

        try {
            Thread[] threads = new Thread[4];

            for (int i = 0; i < 4; ++i) {
                threads[i] = new Thread(new Multiplicate(net, transitions), "Side Thread " + (i + 1));
                threads[i].start();
            }

            net.fire(endingTransition);

            Map<Place, Integer> result = net.reachable(endingTransition).iterator().next();

            System.out.println(A + " * " + B + " = " + ((result.get(Place.PRODUCT) == null) ? 0 : result.get(Place.PRODUCT)));

            for (Thread thread : threads) {
                thread.interrupt();
            }
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted");
        }

    }
}
