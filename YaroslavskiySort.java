/**
 * YaroslavskiySort.java
 *
 * Pure implementation of Yaroslavskiy's 2009 Dual-Pivot Quicksort algorithm.
 *
 * This is a scientifically clean baseline that isolates dual-pivot partitioning
 * behaviour from Java's production DualPivotQuicksort heuristics.
 *
 * Intentionally omitted (by design):
 *   - No insertion sort fallback / INSERTION_SORT_THRESHOLD
 *   - No run-counting / MAX_RUN_COUNT loop
 *   - No Timsort or merge sort intercept
 *   - No pivot-count heuristic or sampling strategy
 *
 * Partitioning scheme (Yaroslavskiy 2009):
 *   p1 = A[left],  p2 = A[right]   (swap if p1 > p2 so p1 <= p2)
 *   Three-pointer sweep with less, k, great:
 *     - Elements < p1  placed left  of less
 *     - Elements > p2  placed right of great
 *     - Elements in [p1..p2] remain in the centre zone
 *   Pivots are placed at positions (less-1) and (great+1) after the sweep.
 *   Recurse on [left .. less-2], [less .. great], [great+2 .. right].
 *
 * The only base case is: if left >= right, return immediately.
 */
public class YaroslavskiySort {

    /**
     * Public entry point. Sorts the entire array in-place.
     *
     * @param a the int array to sort
     */
    public static void sort(int[] a) {
        sort(a, 0, a.length - 1);
    }

    /**
     * Recursive dual-pivot quicksort over a[left..right] (inclusive).
     *
     * Uses an explicit heap-allocated stack instead of the JVM call stack so
     * that degenerate inputs (SORTED, REVERSE, all-equal) do not cause a
     * StackOverflowError.  The partitioning logic is identical to Yaroslavskiy
     * 2009 — nothing else has changed.
     */
    private static void sort(int[] a, int left, int right) {
        // Each pending sub-problem is a (lo, hi) pair — two ints per entry.
        // At most O(n) disjoint ranges can be live at once; 4*(n+1) slots is safe.
        int capacity = 4 * (right - left + 2);
        int[] stk = new int[capacity];
        int top = 0;
        stk[top++] = left;
        stk[top++] = right;

        while (top > 0) {
            int hi = stk[--top];
            int lo = stk[--top];

            if (lo >= hi) continue;

            // ── Pivot selection ───────────────────────────────────────────────
            int p1 = a[lo];
            int p2 = a[hi];

            // Enforce p1 <= p2
            if (p1 > p2) {
                a[lo] = p2;
                a[hi] = p1;
                p1 = a[lo];
                p2 = a[hi];
            }

            // ── Three-pointer sweep ───────────────────────────────────────────
            int less  = lo + 1;
            int great = hi - 1;
            int k     = less;

            while (k <= great) {
                int ak = a[k];

                if (ak < p1) {
                    swap(a, k, less);
                    less++;
                    k++;
                } else if (ak > p2) {
                    while (a[great] > p2 && k < great) {
                        great--;
                    }
                    swap(a, k, great);
                    great--;
                    ak = a[k];
                    if (ak < p1) {
                        swap(a, k, less);
                        less++;
                    }
                    k++;
                } else {
                    k++;
                }
            }

            // ── Place pivots in their final positions ─────────────────────────
            less--;   // index for p1
            great++;  // index for p2

            swap(a, lo, less);
            swap(a, hi, great);

            // ── Push the three sub-problems ───────────────────────────────────
            stk[top++] = lo;
            stk[top++] = less - 1;

            // When p1 == p2 every element in [less+1..great-1] equals both
            // pivots and is already in its final sorted position — no recursion
            // needed for that zone (this is noted in Yaroslavskiy 2009 §3).
            if (p1 < p2) {
                stk[top++] = less  + 1;
                stk[top++] = great - 1;
            }

            stk[top++] = great + 1;
            stk[top++] = hi;
        }
    }

    private static void swap(int[] a, int i, int j) {
        int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }
}
