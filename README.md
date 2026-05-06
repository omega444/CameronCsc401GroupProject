# CSC 401/501 Algorithm Benchmarking Project

A Java benchmarking suite that empirically compares sorting and selection algorithms across a range of dataset sizes and input configurations.

---

## What It Does

The project benchmarks three algorithms:

| Algorithm | Strategy | Complexity |
|---|---|---|
| **Baseline** — `Arrays.sort()` | Sort the entire array, then index into it | O(n log n) |
| **QuickSelect (Lomuto)** | Randomized Lomuto partitioning; stop as soon as the pivot lands on position k | Expected O(n) |
| **YaroslavskiySort** | Pure dual-pivot quicksort (Yaroslavskiy 2009) — partitions into three zones using two pivots, recurses on all three | Expected O(n log n) |

YaroslavskiySort was added to isolate the dual-pivot partitioning strategy from Java's production `Arrays.sort()` heuristics (which layer on insertion sort fallbacks, Timsort intercepts, and pivot sampling). Running the pure algorithm on the same datasets reveals how much of `Arrays.sort()`'s performance comes from the dual-pivot scheme itself versus those engineering additions.

All algorithms are run across **6 dataset sizes** (`100`, `1,000`, `5,000`, `10,000`, `50,000`, `100,000`) and **4 input configurations**, with **100 timed trials per combination**. Average nanosecond times are recorded to CSV-formatted `.txt` files, which are then fed into a chart generator that produces bar charts and line charts as PNG images.

### Input Configurations

| Configuration | Description | Why It Matters |
|---|---|---|
| `RANDOM` | Uniformly random values in `[0, n×10)` | Typical, unstructured input |
| `SORTED` | Same values sorted ascending | Stress-tests pivot quality (naïve pivoting → O(n²)) |
| `REVERSE` | Sorted then reversed (descending) | Another classic adversarial case |
| `DUPLICATES` | Values restricted to `[0, 4]` | High collision rate; stresses equal-key handling |

---

## Project Structure

```
.
├── DataGenerator.java          # Generates test arrays in the 4 configurations
├── QuickSelect.java            # Randomized Lomuto k-th smallest selection
├── YaroslavskiySort.java       # Pure Yaroslavskiy 2009 dual-pivot quicksort (no JDK heuristics)
├── BenchmarkRunner.java        # Runs benchmarks and prints CSV results to stdout
├── BenchmarkChartGenerator.java# Reads run files, averages across runs, renders PNGs
│
├── cam_run_baseline_*.txt      # Recorded baseline benchmark runs (CSV)
├── cam run quickselect*.txt    # Recorded QuickSelect benchmark runs (CSV)
│
└── charts/                     # Generated chart output
    ├── bar_n100.png            # Bar chart at n = 100
    ├── bar_n1000.png
    ├── ...
    ├── line_random.png         # Line chart for RANDOM config (both algos)
    ├── line_sorted.png
    ├── line_reverse.png
    ├── line_duplicates.png
    └── line_all_configs_*.png
```

---

## Prerequisites

- **Java 16+** (the chart generator uses `record` types introduced in Java 16)
- No external libraries or build tools required — everything is plain Java SE

Verify your version:

```bash
java -version
```

---

## How to Run

All commands should be run from the project root directory.

### Step 1 — Compile

Compile all four source files at once:

```bash
javac DataGenerator.java QuickSelect.java BenchmarkRunner.java BenchmarkChartGenerator.java
```

### Step 2 — Run the Benchmark

Execute `BenchmarkRunner` and redirect its CSV output to a new run file:

```bash
java BenchmarkRunner > my_run_baseline_1.txt
```

> **Toggle flags** — Before running, open `BenchmarkRunner.java` and set the two boolean flags near the top to control which algorithm(s) are active:
>
> ```java
> public static boolean runBaseline    = true;   // set false to skip baseline
> public static boolean runQuickselect = true;   // set false to skip quickselect
> ```
>
> Name your output file accordingly:
> - Baseline-only run → `my_run_baseline_1.txt`
> - QuickSelect-only run → `my_run_quickselect_1.txt`

Repeat for as many runs as you want (different numbered suffixes). The chart generator averages across all matching files automatically.

### Step 3 — Generate Charts

Once you have at least one run file, generate the charts:

```bash
java BenchmarkChartGenerator
```

PNG charts are written to the `charts/` subdirectory (created automatically if it does not exist).

---

## Output Files

### Benchmark run files (CSV-in-.txt)

Each run file has a header row followed by one measurement per size/config/algorithm combination:

```
DatasetSize,Configuration,Algorithm,AvgTimeNanoseconds
100,RANDOM,Baseline(Arrays.sort),9224
100,RANDOM,Quickselect(Lomuto),2766
...
100000,DUPLICATES,Quickselect(Lomuto),187432
```

### Charts

| File pattern | Contents |
|---|---|
| `bar_n<SIZE>.png` | Side-by-side bars for QS vs. Baseline across all 4 configs at a fixed n |
| `line_<config>.png` | Time vs. dataset size for one config (both algorithms on the same chart) |
| `line_all_configs_*.png` | All configurations overlaid for a single algorithm |

---

## Algorithm Details

### QuickSelect — Randomized Lomuto Partitioning

`QuickSelect.select(int[] a, int l, int h, int k)` returns the value of the k-th smallest element (1-indexed) using decrease-and-conquer:

1. Randomly pick a pivot index `r` in `[l, h]` and swap it to position `h`.
2. Run the Lomuto partition, placing the pivot at its sorted position `p`.
3. If `k-1 == p` → return `a[p]`.  
   If `k-1 < p` → recurse left.  
   Else → recurse right.

Random pivot selection gives **expected O(n)** time and eliminates worst-case O(n²) behaviour on sorted/reverse-sorted input.

### YaroslavskiySort — Dual-Pivot Quicksort (Yaroslavskiy 2009)

`YaroslavskiySort.sort(int[] a)` sorts in-place using the original dual-pivot scheme from Vladimir Yaroslavskiy's 2009 proposal:

1. Choose `p1 = a[left]`, `p2 = a[right]`; swap if necessary so `p1 ≤ p2`.
2. Three-pointer sweep (`less`, `k`, `great`) divides the array into four zones: `< p1`, `[p1..p2]`, `> p2`, and unexamined.
3. Place both pivots in their final positions, then recurse on the left zone, centre zone (skipped when `p1 == p2`), and right zone.

The implementation uses an **explicit heap-allocated stack** instead of the JVM call stack, preventing `StackOverflowError` on degenerate inputs (sorted, reverse-sorted, all-equal). The partitioning logic itself is unchanged from the 2009 paper.

**Why add it?** Java's `Arrays.sort()` is derived from this algorithm but wraps it in production heuristics (insertion sort below a threshold, Timsort for nearly-sorted runs, pivot sampling). YaroslavskiySort strips all of that away so benchmarks reflect the core dual-pivot strategy alone.

### Baseline — `Arrays.sort()`

The entire copy of the array is sorted with Java's dual-pivot Timsort (`Arrays.sort`). The k-th smallest is then `a[k-1]`. This always costs **O(n log n)** regardless of input shape.

---

## Limitations & Runtime Expectations

| Dataset size (n) | Approximate benchmark time |
|---|---|
| 100 – 1,000 | < 5 seconds |
| 5,000 – 10,000 | 10 – 30 seconds |
| 50,000 | ~1–2 minutes |
| 100,000 | ~3–5 minutes |

Times are wall-clock estimates for a full 100-trial run on a modern laptop. Actual times vary by hardware.

**Memory:** Each run loads arrays entirely into heap. At n = 100,000 with `int[]`, a single array uses ~400 KB; 100 trials allocate and discard arrays sequentially, so peak heap usage stays well under the JVM default (typically 256 MB). No special `-Xmx` flag is needed.

**Known limitations:**
- The benchmark is single-threaded; results reflect one CPU core only.
- JVM warm-up effects are not fully eliminated. The first few trials of each combination may be slower due to JIT compilation. This is expected and averaged out across 100 trials.
- Chart generation (`BenchmarkChartGenerator`) requires at least one matching run file per algorithm prefix (`*baseline*` or `*quickselect*`); running with no output files will throw a file-not-found error.

---

## Team

| Member | Role |
|---|---|
| Cameron | QuickSelect implementation, benchmarking infrastructure |
| Johnny | Baseline (`Arrays.sort`) benchmarking lead |
| Jacob | Improved algorithm lead |
| Bryan | Data analysis and chart generation |
Kaseem   C0-Lead / architecture, complexity analysis, 
