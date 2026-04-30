import java.util.Random;

/**
 * QuickSelect.java
 * Implementation of the K-th Smallest Element algorithm using 
 * Randomized Lomuto Partitioning as specified in the project requirements.
 */
public class QuickSelect {
    private static final Random random = new Random();

    /**
     * The main selection function (Decrease-and-Conquer).
     * @param a The array to search
     * @param l Lower bound index
     * @param h Higher bound index
     * @param k The k-th position (1-indexed)
     * @return The value of the k-th smallest element
     */
    public static int select(int[] a, int l, int h, int k) {
        // If the array contains only one element, return it
        if (l == h) {
            return a[l];
        }

        // Partition the array and get the pivot index
        int pivotIndex = lomutoPartitioning(a, l, h);

        // Check if the pivot index is the k-th smallest element
        // Note: k-1 because the array is 0-indexed
        if (k - 1 == pivotIndex) {
            return a[pivotIndex];
        } else if (k - 1 < pivotIndex) {
            // Recurse on the left subarray
            return select(a, l, pivotIndex - 1, k);
        } else {
            // Recurse on the right subarray
            return select(a, pivotIndex + 1, h, k);
        }
    }

    /**
     * Implements the Lomuto Partitioning scheme with a random pivot.
     */
    private static int lomutoPartitioning(int[] a, int l, int h) {
        // Randomly select r from {l, ..., h}
        int r = l + random.nextInt(h - l + 1);
        
        // Swap a[h] with a[r] to use the random element as the pivot
        swap(a, h, r);
        
        int x = a[h]; // The pivot value
        int k = l - 1; // Index of the smaller element
        
        for (int j = l; j <= h - 1; j++) {
            if (a[j] <= x) {
                k++;
                swap(a, j, k);
            }
        }
        
        // Final swap to place the pivot in its correct sorted position
        swap(a, k + 1, h);
        
        return k + 1; // Return the pivot's final index
    }

    private static void swap(int[] a, int i, int j) {
        int temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }
}