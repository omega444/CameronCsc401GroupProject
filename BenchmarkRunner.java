import java.util.Arrays;

/**
 * BenchmarkRunner.java
 * * PURPOSE:
 * The central execution hub for the CSC 401/501 Algorithm Project. 
 * Coordinates data generation, algorithm execution, and timing analysis.
 * * FEATURES:
 * 1. Comprehensive Proof-of-Generation: Visual validation of small (n=10) and large (n=1000) arrays.
 * 2. Randomized QuickSelect Proof: Verifies Lomuto Partitioning correctness across configurations.
 * 3. Transparent Timing: Displays real-time nanosecond results for initial runs.
 * 4. Unambiguous Labeling: Explicitly labels Baseline(Arrays.sort) and Quickselect(Lomuto).
 * 5. Toggle Flags: Independent execution for Johnny (Baseline) and Jacob (Improved).
 */
public class BenchmarkRunner {

    // --- TEAM TOGGLE FLAGS ---
    // Johnny (Baseline Lead): Set runQuickselect = false
    // Jacob (Improved Lead): Set runBaseline = false
    public static boolean runBaseline = false; 
    public static boolean runQuickselect = true;

    public static void main(String[] args) {

        // --- 1. DATA GENERATION & ALGORITHM PROOF ---
        // Provides visual proof of array creation and algorithm correctness.
        System.out.println("=== DATA GENERATION & ALGORITHM PROOF (n=10) ===");
        String[] proofConfigs = {"RANDOM", "SORTED", "REVERSE", "DUPLICATES"};
        
        for (String config : proofConfigs) {
            int[] proofData = DataGenerator.generate(10, config);
            System.out.println(config + " Array (n=10): " + Arrays.toString(proofData));
            int kValue = 5;

            // Baseline Proof with Timing
            if (runBaseline) {
                int[] sampleB = Arrays.copyOf(proofData, 10);
                long sB = System.nanoTime();
                Arrays.sort(sampleB);
                long eB = System.nanoTime();
                System.out.printf("   -> Baseline(Arrays.sort) Time: %d ns\n", (eB - sB));
            }
            
            // QuickSelect Proof with Timing
            if (runQuickselect) {
                int[] sampleQ = Arrays.copyOf(proofData, 10);
                long sQ = System.nanoTime();
                int result = QuickSelect.select(sampleQ, 0, 9, kValue);
                long eQ = System.nanoTime();
                System.out.printf("   -> QuickSelect(Lomuto) Proof: Found %dth smallest -> %d (Time: %d ns)\n", 
                                    kValue, result, (eQ - sQ));
            }
            System.out.println();
        }

        // --- 2. LARGE SCALE PROOF-OF-CONCEPT ---
        // Demonstrates that the system successfully handles large datasets in memory.
        System.out.println("=== LARGE SCALE DATA PROOF (n=1000) ===");
        int largeN = 1000;
        int[] largeData = DataGenerator.generate(largeN, "RANDOM");
        
        // Print the first 20 and last 20 elements to show the array scale
        System.out.print("Large RANDOM Array (Truncated View): [");
        for (int i = 0; i < 20; i++) System.out.print(largeData[i] + ", ");
        System.out.println("... , ");
        for (int i = largeN - 20; i < largeN; i++) {
            System.out.print(largeData[i] + (i == largeN - 1 ? "" : ", "));
        }
        System.out.println("]");
        
        // Large Scale Baseline Proof
        if (runBaseline) {
            long startB = System.nanoTime();
            int[] largeB = Arrays.copyOf(largeData, largeN);
            Arrays.sort(largeB);
            long endB = System.nanoTime();
            System.out.println("   -> Large Scale Baseline(Arrays.sort) Proof: Completed in " + (endB - startB) + " ns");
        }

        // Large Scale QuickSelect Proof
        if (runQuickselect) {
            long startQ = System.nanoTime();
            int res = QuickSelect.select(Arrays.copyOf(largeData, largeN), 0, largeN - 1, largeN/2);
            long endQ = System.nanoTime();
            System.out.println("   -> Large Scale QuickSelect(Lomuto) Proof: Found median in " + (endQ - startQ) + " ns");
        }
        System.out.println("================================================\n");


        // --- 3. ASYMPTOTIC BENCHMARKING (CSV Output for Bryan) ---
        int[] sizes = {100, 1000, 5000, 10000, 50000, 100000};
        String[] configs = {"RANDOM", "SORTED", "REVERSE", "DUPLICATES"};
        int trials = 100;

        System.out.println("DatasetSize,Configuration,Algorithm,AvgTimeNanoseconds");

        for (int n : sizes) {
            for (String config : configs) {
                long totalBaselineTime = 0;
                long totalQuickselectTime = 0;

                for (int t = 0; t < trials; t++) {
                    int[] data = DataGenerator.generate(n, config);
                    int k = n / 2;

                    if (runBaseline) {
                        int[] baselineCopy = Arrays.copyOf(data, data.length);
                        long start = System.nanoTime();
                        Arrays.sort(baselineCopy);
                        totalBaselineTime += (System.nanoTime() - start);
                    }

                    if (runQuickselect) {
                        int[] quickCopy = Arrays.copyOf(data, data.length);
                        long start = System.nanoTime();
                        QuickSelect.select(quickCopy, 0, n - 1, k);
                        totalQuickselectTime += (System.nanoTime() - start);
                    }
                }

                if (runBaseline) {
                    System.out.printf("%d,%s,Baseline(Arrays.sort),%d\n", n, config, totalBaselineTime / trials);
                }
                if (runQuickselect) {
                    System.out.printf("%d,%s,Quickselect(Lomuto),%d\n", n, config, totalQuickselectTime / trials);
                }
            }
        }
    }
}