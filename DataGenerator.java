import java.util.Arrays;
import java.util.Random;

/**
 * DataGenerator is a pure utility class responsible for producing integer arrays
 * in various configurations used to benchmark sorting and selection algorithms.
 *
 * This class is intentionally stateless (aside from a shared Random instance) and
 * exposes only static methods, so it never needs to be instantiated. Every benchmark
 * trial calls {@link #generate(int, String)} to get a fresh, independent copy of the
 * requested dataset, ensuring that no trial contaminates the next.
 *
 * Supported configurations
 * ─────────────────────────
 * RANDOM     – Values are drawn uniformly at random from [0, n*10).
 *              Represents typical, unstructured input.
 *
 * SORTED     – The same random values are sorted in ascending order with
 *              {@link Arrays#sort}. Many naive quicksort/quickselect pivoting
 *              strategies degrade to O(n²) on sorted input, making this a
 *              stress-test for pivot selection quality.
 *
 * REVERSE    – The sorted array is then reversed to produce descending order.
 *              Another classic worst-case for algorithms that always pick the
 *              first or last element as their pivot.
 *
 * DUPLICATES – Values are restricted to the tiny range [0, 4], guaranteeing
 *              a very high collision rate. This stresses algorithms that do not
 *              handle equal elements efficiently (e.g., standard two-way partition
 *              vs. three-way/Dutch-flag partition).
 *
 * Usage example
 * ─────────────
 * {@code
 *   int[] data = DataGenerator.generate(10_000, "SORTED");
 * }
 */
public class DataGenerator {

    /**
     * Shared, class-level Random instance.
     *
     * Using a single static instance avoids the overhead of constructing a new
     * Random object on every call to generate(). It is sufficient for benchmarking
     * because we do not require cryptographic randomness — we only need statistically
     * varied data to exercise the algorithms across many trials.
     */
    private static final Random random = new Random();

    /**
     * Generates an integer array of length {@code n} whose contents match the
     * requested {@code config} label.
     *
     * The method is case-insensitive: "sorted", "Sorted", and "SORTED" are all
     * treated identically.
     *
     * @param n      the number of elements in the returned array; must be > 0
     * @param config one of "RANDOM", "SORTED", "REVERSE", or "DUPLICATES"
     *               (case-insensitive); any unrecognised value falls back to RANDOM
     * @return       a freshly allocated int[] of length n in the requested configuration
     */
    public static int[] generate(int n, String config) {

        // Allocate a new array for every call so each benchmark trial gets an
        // independent copy and earlier trials cannot influence later ones.
        int[] arr = new int[n];

        // Populate the array with uniformly distributed random values in [0, n*10).
        // The upper bound scales with n so the value range grows with the array size,
        // keeping the density of duplicates roughly constant across different n values
        // (except in the DUPLICATES configuration, which deliberately overrides this).
        for (int i = 0; i < n; i++) {
            arr[i] = random.nextInt(n * 10);
        }

        // Apply the structural transformation requested by the caller.
        // toUpperCase() normalises the config string so the switch is case-insensitive.
        switch (config.toUpperCase()) {

            case "SORTED":
                // Sort the randomly generated values into ascending order.
                // This simulates a best-case scenario for insertion sort but a
                // potential worst case for naive quicksort/quickselect implementations.
                Arrays.sort(arr);
                break;

            case "REVERSE":
                // First sort ascending, then reverse to produce descending order.
                // Descending order is another classic adversarial input for algorithms
                // that always choose the last element as the pivot.
                Arrays.sort(arr);
                reverse(arr);
                break;

            case "DUPLICATES":
                // Overwrite every element with a value in [0, 4].
                // With only 5 distinct values across potentially 100,000 elements,
                // this configuration highlights how well an algorithm handles equal keys.
                // Three-way (Dutch-flag) partitioning excels here; two-way partitioning
                // can degrade badly because equal elements are not grouped efficiently.
                for (int i = 0; i < n; i++) {
                    arr[i] = random.nextInt(5);
                }
                break;

            case "RANDOM":
            default:
                // The array was already filled with random values in the loop above,
                // so no additional transformation is needed. Unrecognised config labels
                // also fall through to this branch, treating them as RANDOM.
                break;
        }

        return arr;
    }

    /**
     * Reverses the elements of {@code arr} in place using a two-pointer approach.
     *
     * This helper is only called internally by {@link #generate} to build the
     * REVERSE configuration. It runs in O(n) time and O(1) extra space.
     *
     * How it works:
     *   - {@code i} starts at the beginning (index 0).
     *   - The symmetric partner is at index {@code arr.length - 1 - i}.
     *   - The loop continues until the two pointers meet in the middle.
     *
     * @param arr the array to reverse; modified in place
     */
    private static void reverse(int[] arr) {
        for (int i = 0; i < arr.length / 2; i++) {
            // Classic three-variable swap: save, overwrite left, overwrite right.
            int temp = arr[i];
            arr[i] = arr[arr.length - 1 - i];
            arr[arr.length - 1 - i] = temp;
        }
    }
}
