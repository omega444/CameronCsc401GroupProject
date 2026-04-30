import java.util.Arrays;

/**
 * BenchmarkRunner is the entry-point (main class) for the algorithm performance study.
 *
 * PURPOSE
 * ───────
 * This class orchestrates a systematic comparison between two strategies for finding
 * the k-th smallest element in an unsorted array:
 *
 *   1. BASELINE  – {@link Arrays#sort} the entire array, then index directly into it.
 *                  Time complexity: O(n log n) for the sort.
 *
 *   2. QUICKSELECT – A randomised, in-place selection algorithm based on the Lomuto
 *                    partition scheme with a randomly chosen pivot.
 *                    Expected time complexity: O(n) average case.
 *                    Worst case: O(n²) (extremely unlikely with random pivoting).
 *
 * HOW IT WORKS
 * ────────────
 * For every combination of (array size × data configuration) the runner:
 *   a) Generates a fresh array via {@link DataGenerator#generate}.
 *   b) Makes two independent copies of that array — one for each algorithm — so
 *      neither algorithm's in-place modifications affect the other's measurement.
 *   c) Times each algorithm using {@link System#nanoTime()} for nanosecond precision.
 *   d) Repeats steps a–c for {@code trials} iterations and averages the timing.
 *   e) Prints one CSV row per (size, config, algorithm) triple.
 *
 * OUTPUT FORMAT
 * ─────────────
 * The program writes CSV to standard output so it can be piped directly into a file:
 *
 *   java BenchmarkRunner > results.csv
 *
 * Columns: DatasetSize, Configuration, Algorithm, AvgTimeNanoseconds
 *
 * EXPERIMENTAL PARAMETERS
 * ────────────────────────
 * Array sizes  : 100, 1 000, 5 000, 10 000, 50 000, 100 000
 * Configurations: RANDOM, SORTED, REVERSE, DUPLICATES  (see DataGenerator)
 * Trials per cell: 100  (averaging reduces noise from JVM jitter, GC pauses, etc.)
 * k (rank)     : always n/2, i.e., the median element
 */
public class BenchmarkRunner {

    /**
     * Program entry point.
     *
     * Iterates over every (size, configuration) pair, runs both algorithms for
     * {@code trials} independent trials each, and prints the averaged timing as CSV.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {

        // ── Experimental Parameters ───────────────────────────────────────────────
        // The array sizes to test. Doubling (or quintupling) the size lets us observe
        // how run-time grows and verify whether behaviour matches theoretical complexity.
        int[] sizes = {100, 1000, 5000, 10000, 50000, 100000};

        // Each configuration stresses the algorithms differently.
        // See DataGenerator for a full description of each.
        String[] configs = {"RANDOM", "SORTED", "REVERSE", "DUPLICATES"};

        // Number of independent repetitions per (size, config) cell.
        // More trials → more stable average, but longer total runtime.
        // 100 trials is a practical balance for classroom benchmarking.
        int trials = 100;

        // ── CSV Header ────────────────────────────────────────────────────────────
        // Print the header row so the output file is immediately importable into
        // spreadsheet software or Python/R for analysis.
        System.out.println("DatasetSize,Configuration,Algorithm,AvgTimeNanoseconds");

        // ── Main Benchmark Loop ───────────────────────────────────────────────────
        for (int n : sizes) {
            for (String config : configs) {

                // Accumulators for total elapsed nanoseconds across all trials.
                // Dividing by `trials` at the end gives the average per-trial time.
                long totalBaselineTime   = 0;
                long totalQuickselectTime = 0;

                for (int t = 0; t < trials; t++) {

                    // ── Fresh Data ────────────────────────────────────────────────
                    // Generate a new array for every trial so the data is never
                    // reused. This prevents a lucky (or unlucky) first array from
                    // skewing the entire average.
                    int[] data = DataGenerator.generate(n, config);

                    // We always search for the median (rank = n/2).
                    // Integer division means k = 50 for n = 100, k = 500 for n = 1000, etc.
                    int k = n / 2;

                    // ── Baseline: Arrays.sort ─────────────────────────────────────
                    // Copy the original data so the sort does not modify the array
                    // used by the Quickselect trial (each algorithm gets identical input).
                    int[] baselineCopy = Arrays.copyOf(data, data.length);

                    long startB = System.nanoTime();
                    Arrays.sort(baselineCopy);          // O(n log n) dual-pivot quicksort
                    int resB = baselineCopy[k - 1];     // k is 1-based; convert to 0-based index
                    totalBaselineTime += (System.nanoTime() - startB);

                    // resB is used here only to prevent the JVM from optimising away
                    // the sort as dead code. (A sufficiently aggressive JIT could
                    // eliminate the entire block if it detects the result is unused.)
                    // In practice, assigning to a local variable is enough.

                    // ── Improved: Quickselect ─────────────────────────────────────
                    // Another independent copy so Quickselect operates on the same
                    // original data the baseline saw, making the comparison fair.
                    int[] quickCopy = Arrays.copyOf(data, data.length);

                    long startQ = System.nanoTime();
                    // quickSelect returns the k-th smallest value (1-based k).
                    int resQ = quickSelect(quickCopy, 0, quickCopy.length - 1, k);
                    totalQuickselectTime += (System.nanoTime() - startQ);
                }

                // ── Output One Row per Algorithm ──────────────────────────────────
                // Divide accumulated nanoseconds by trial count to get the average.
                // %d produces a plain integer (no decimal point), which is clean for CSV.
                System.out.printf("%d,%s,Baseline,%d\n",
                        n, config, totalBaselineTime / trials);

                System.out.printf("%d,%s,Quickselect,%d\n",
                        n, config, totalQuickselectTime / trials);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Quickselect Implementation
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Finds the k-th smallest element (1-based) in the subarray {@code a[left..right]}
     * using the randomised Lomuto partition scheme.
     *
     * ALGORITHM OVERVIEW
     * ──────────────────
     * Quickselect is a selection-by-partitioning algorithm derived from Quicksort.
     * Unlike Quicksort, which recurses on BOTH halves, Quickselect only recurses on
     * the half that must contain the answer — discarding roughly half the work each
     * time. This is why the expected complexity drops from O(n log n) to O(n).
     *
     * RECURSION STRUCTURE
     * ───────────────────
     *  1. Choose a pivot and partition a[left..right] around it.
     *     After partitioning, pivotIndex is the pivot's final, sorted position.
     *
     *  2. Base case: if left == right, only one element remains — return it.
     *
     *  3. If k-1 == pivotIndex, the pivot IS the k-th smallest — return it.
     *
     *  4. If k-1 < pivotIndex, the answer lies in the LEFT subarray  → recurse left.
     *
     *  5. If k-1 > pivotIndex, the answer lies in the RIGHT subarray → recurse right.
     *
     * Note: k is 1-based (rank 1 = smallest), but array indices are 0-based, hence
     * the comparisons use {@code k - 1} against {@code pivotIndex}.
     *
     * @param a     the array to search (modified in place during partitioning)
     * @param left  the inclusive left boundary of the current subarray
     * @param right the inclusive right boundary of the current subarray
     * @param k     the desired rank (1 = smallest, n = largest)
     * @return      the value of the k-th smallest element
     */
    public static int quickSelect(int[] a, int left, int right, int k) {

        // Base case: the subarray has exactly one element, which must be the answer.
        if (left == right) return a[left];

        // Partition around a randomly chosen pivot and get its final sorted index.
        int pivotIndex = randomLomutoPartition(a, left, right);

        if (k - 1 == pivotIndex) {
            // The pivot landed exactly at rank k — we're done.
            return a[pivotIndex];
        } else if (k - 1 < pivotIndex) {
            // The k-th smallest must be to the LEFT of the pivot.
            // Discard the pivot and everything to its right.
            return quickSelect(a, left, pivotIndex - 1, k);
        } else {
            // The k-th smallest must be to the RIGHT of the pivot.
            // Discard the pivot and everything to its left.
            return quickSelect(a, pivotIndex + 1, right, k);
        }
    }

    /**
     * Partitions the subarray {@code a[left..right]} around a randomly selected pivot
     * using the Lomuto partition scheme and returns the pivot's final index.
     *
     * RANDOMISATION
     * ─────────────
     * Choosing the pivot randomly (rather than always picking a[right]) makes it
     * extremely unlikely for an adversarial or pathological input (e.g., SORTED or
     * REVERSE data) to consistently trigger O(n²) worst-case behaviour. By swapping
     * the random element to the {@code right} position before partitioning, we reuse
     * the standard Lomuto logic without any further modification.
     *
     * LOMUTO PARTITION SCHEME
     * ───────────────────────
     * The pivot value {@code x = a[right]} is placed at the end of the subarray.
     * A "boundary" index {@code i} starts just before {@code left}.
     * Every element {@code a[j]} that is ≤ x is swapped into the "left region"
     * by incrementing {@code i} and swapping {@code a[i]} with {@code a[j]}.
     * At the end, the pivot is moved into position {@code i+1}, which is its
     * correct sorted position: everything to its left is ≤ x and everything to
     * its right is > x.
     *
     * @param a     the array containing the subarray to partition
     * @param left  the inclusive left boundary
     * @param right the inclusive right boundary (pivot is placed here before partition)
     * @return      the final 0-based index of the pivot after partitioning
     */
    private static int randomLomutoPartition(int[] a, int left, int right) {

        // Pick a random index within [left, right] and move that element to the end
        // so the standard Lomuto code can proceed without modification.
        int r = left + (int)(Math.random() * (right - left + 1));
        swap(a, r, right);

        // x is the pivot value; i tracks the boundary between elements ≤ x and > x.
        int x = a[right];
        int i = left - 1;

        // Scan every element except the pivot (which sits at a[right]).
        for (int j = left; j < right; j++) {
            if (a[j] <= x) {
                // a[j] belongs in the "≤ pivot" region — expand that region and place it there.
                swap(a, ++i, j);
            }
        }

        // Place the pivot immediately after the last element that is ≤ x.
        // This is the pivot's final sorted position.
        swap(a, i + 1, right);
        return i + 1;
    }

    /**
     * Swaps the elements at positions {@code i} and {@code j} in array {@code a}.
     *
     * This helper is used by both {@link #randomLomutoPartition} (many times per
     * partition call) and by the random-pivot setup step. Inlining the three lines
     * each time would clutter the logic; extracting it here improves readability.
     *
     * @param a the array containing the elements to swap
     * @param i index of the first element
     * @param j index of the second element
     */
    private static void swap(int[] a, int i, int j) {
        int temp = a[i];
        a[i]    = a[j];
        a[j]    = temp;
    }
}
