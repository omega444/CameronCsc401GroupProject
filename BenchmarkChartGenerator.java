import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * BenchmarkChartGenerator
 *
 * Reads QuickSelect (Lomuto), Baseline (Arrays.sort+RunDetection), and
 * YAROSLAVSKIY (DualPivot) benchmark CSV data from multiple run files,
 * averages across runs, and produces:
 *   1. Bar charts  — avg time per configuration at each dataset size
 *                    Each bar is labelled "QS", "BL", or "YR" directly above it
 *   2. Line charts — avg time vs. dataset size per configuration (all algos)
 *                    Each line is labelled at its right endpoint
 *
 * Usage:
 *   Place all cam_run_quickselect*.txt, cam_run_baseline*.txt, and
 *   cam_run_yaroslavskiy*.txt files in
 *   the same directory as this class (or update DATA_DIR below).
 *   Compile:  javac BenchmarkChartGenerator.java
 *   Run:      java BenchmarkChartGenerator
 *
 * Output PNGs are written to ./charts/
 */
public class BenchmarkChartGenerator {

    // ── Config ────────────────────────────────────────────────────────────────
    private static final String DATA_DIR   = ".";
    private static final String OUTPUT_DIR = "charts";
    private static final int    IMG_W      = 1100;
    private static final int    IMG_H      = 640;

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final Color BG        = new Color(18, 20, 28);
    private static final Color GRID      = new Color(45, 50, 68);
    private static final Color TEXT_MAIN = new Color(230, 235, 255);
    private static final Color TEXT_DIM  = new Color(140, 150, 180);

    // ── Algorithm colours — vivid, high-saturation ────────────────────────────
    // QuickSelect bars: bold, saturated
    private static final Color[] QS_COLORS = {
        new Color(0,   180, 255),   // RANDOM     – electric cyan
        new Color(0,   230,  90),   // SORTED     – neon green
        new Color(255, 160,   0),   // REVERSE    – vivid amber
        new Color(255,  60,  90),   // DUPLICATES – hot red
    };
    // Baseline bars: clearly lighter/pastel version of same hue
    private static final Color[] BL_COLORS = {
        new Color(160, 220, 255),   // RANDOM
        new Color(170, 255, 200),   // SORTED
        new Color(255, 220, 140),   // REVERSE
        new Color(255, 170, 185),   // DUPLICATES
    };
    // Yaroslavskiy bars: vivid violet per config
    private static final Color[] YR_COLORS = {
        new Color(180, 100, 255),   // RANDOM     – vivid violet
        new Color(220, 150, 255),   // SORTED     – light violet
        new Color(130,  50, 220),   // REVERSE    – deep violet
        new Color(200,  80, 240),   // DUPLICATES – vivid purple
    };

    // Line chart: QS = electric cyan, BL = gold, YR = vivid violet
    private static final Color LINE_QS = new Color(0,   200, 255);
    private static final Color LINE_BL = new Color(255, 200,  40);
    private static final Color LINE_YR = new Color(200, 100, 255);

    // ── Data structures ───────────────────────────────────────────────────────
    record DataPoint(int size, String config, String algo, long timeNs) {}

    // ── Main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {

        List<DataPoint> allPoints = new ArrayList<>();
        File dir = new File(DATA_DIR);
        File[] files = dir.listFiles((d, name) ->
            name.matches("cam.run.quickselect.*\\.txt")   ||
            name.matches("cam.run.baseline.*\\.txt")       ||
            name.matches("cam.run.yaroslavskiy.*\\.txt")   ||
            name.matches("cam_run_quickselect.*\\.txt")    ||
            name.matches("cam_run_baseline.*\\.txt")       ||
            name.matches("cam_run_yaroslavskiy.*\\.txt"));

        if (files == null || files.length == 0) {
            System.err.println("No matching files found in: " + dir.getAbsolutePath());
            System.exit(1);
        }
        for (File f : files) {
            System.out.println("Reading: " + f.getName());
            parseFile(f, allPoints);
        }
        System.out.println("Total data points loaded: " + allPoints.size());

        // Average across runs: key = (size, config, algo)
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        for (DataPoint dp : allPoints) {
            String key = dp.size() + "|" + dp.config() + "|" + dp.algo();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(dp.timeNs());
        }
        Map<String, Double> avg = new LinkedHashMap<>();
        grouped.forEach((k, list) ->
            avg.put(k, list.stream().mapToLong(Long::longValue).average().orElse(0)));

        new File(OUTPUT_DIR).mkdirs();

        int[]    sizes   = {100, 1000, 5000, 10000, 50000, 100000};
        String[] configs = {"RANDOM", "SORTED", "REVERSE", "DUPLICATES"};
        String[] algos   = {"Quickselect(Lomuto)", "Baseline(Arrays.sort+RunDetection)", "YAROSLAVSKIY(DualPivot)"};  // keep QS first so combo chart still works

        // Bar charts — one per dataset size
        for (int size : sizes) {
            BufferedImage img = renderBarChart(avg, size, configs, algos);
            File out = new File(OUTPUT_DIR, "bar_n" + size + ".png");
            ImageIO.write(img, "PNG", out);
            System.out.println("Saved: " + out.getPath());
        }

        // Line charts — one per configuration, both algos
        for (String cfg : configs) {
            BufferedImage img = renderLineChart(avg, cfg, sizes, algos);
            File out = new File(OUTPUT_DIR, "line_" + cfg.toLowerCase() + ".png");
            ImageIO.write(img, "PNG", out);
            System.out.println("Saved: " + out.getPath());
        }

        // Combo line: QuickSelect — all configs on one chart
        BufferedImage combo = renderComboLineChart(avg, sizes, configs, algos);
        File comboOut = new File(OUTPUT_DIR, "line_all_configs_quickselect.png");
        ImageIO.write(combo, "PNG", comboOut);
        System.out.println("Saved: " + comboOut.getPath());

        // Combo line: Yaroslavskiy — all configs on one chart
        BufferedImage yaroCombo = renderYaroComboLineChart(avg, sizes, configs);
        File yaroComboOut = new File(OUTPUT_DIR, "line_all_configs_yaroslavskiy.png");
        ImageIO.write(yaroCombo, "PNG", yaroComboOut);
        System.out.println("Saved: " + yaroComboOut.getPath());

        System.out.println("\nDone. Charts written to ./" + OUTPUT_DIR + "/");
        showPreview(OUTPUT_DIR);
    }

    // ── File parser ───────────────────────────────────────────────────────────
    /**
     * Reads all lines from a file, auto-detecting UTF-16 (BOM) vs UTF-8.
     * PowerShell's > redirect writes UTF-16 LE with BOM; older files are UTF-8.
     */
    static List<String> readLines(File f) throws IOException {
        byte[] head = new byte[2];
        try (InputStream is = new FileInputStream(f)) {
            if (is.read(head) == 2
                    && ((head[0] == (byte) 0xFF && head[1] == (byte) 0xFE)
                     || (head[0] == (byte) 0xFE && head[1] == (byte) 0xFF))) {
                // UTF-16 with BOM — StandardCharsets.UTF_16 consumes the BOM
                return Files.readAllLines(f.toPath(), StandardCharsets.UTF_16);
            }
        }
        return Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
    }

    static void parseFile(File f, List<DataPoint> out) throws IOException {
        boolean inCsv = false;
        for (String raw : readLines(f)) {
            String line = raw.trim();
            if (line.startsWith("DatasetSize,")) { inCsv = true; continue; }
            if (!inCsv || line.isEmpty()) continue;
            String[] parts = line.split(",");
            if (parts.length < 4) continue;
            try {
                int    size   = Integer.parseInt(parts[0].trim());
                String config = parts[1].trim();
                String algo   = parts[2].trim();
                long   time   = Long.parseLong(parts[3].trim());
                out.add(new DataPoint(size, config, algo, time));
            } catch (NumberFormatException ignore) {}
        }
    }

    static double getAvg(Map<String, Double> avg, int size, String config, String algo) {
        return avg.getOrDefault(size + "|" + config + "|" + algo, 0.0);
    }

    // ── Bar Chart ─────────────────────────────────────────────────────────────
    static BufferedImage renderBarChart(Map<String, Double> avg,
                                        int size,
                                        String[] configs,
                                        String[] algos) {
        BufferedImage img = new BufferedImage(IMG_W, IMG_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = setupGraphics(img);

        int padL = 95, padR = 30, padT = 75, padB = 100;
        int plotW = IMG_W - padL - padR;
        int plotH = IMG_H - padT - padB;

        double maxVal = 1;
        for (String cfg : configs)
            for (String alg : algos)
                maxVal = Math.max(maxVal, getAvg(avg, size, cfg, alg));
        maxVal *= 1.22; // extra headroom for above-bar labels

        // Title
        g.setColor(TEXT_MAIN);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        drawCenteredString(g, "Avg Time (ns) by Configuration  —  n = " + size, IMG_W / 2, padT - 32);

        drawAxes(g, padL, padT, plotW, plotH);
        drawYGrid(g, padL, padT, plotW, plotH, maxVal, 5);

        // Bars
        int groupCount  = configs.length;
        int groupGap    = 30;
        int barGap      = 6;
        int totalGroupW = plotW - groupGap * (groupCount + 1);
        int groupW      = totalGroupW / groupCount;
        int barW        = (groupW - barGap) / algos.length;

        for (int gi = 0; gi < groupCount; gi++) {
            int gx = padL + groupGap * (gi + 1) + groupW * gi;

            for (int bi = 0; bi < algos.length; bi++) {
                double val   = getAvg(avg, size, configs[gi], algos[bi]);
                int bx  = gx + bi * (barW + barGap);
                int bh  = (int)(plotH * val / maxVal);
                int by  = padT + plotH - bh;

                Color  base;
                String algoTag;
                if      (bi == 0) { base = QS_COLORS[gi]; algoTag = "QS"; }
                else if (bi == 1) { base = BL_COLORS[gi]; algoTag = "BL"; }
                else              { base = YR_COLORS[gi]; algoTag = "YR"; }
                Color top = brighten(base, 1.25f);

                if (bh > 0) {
                    GradientPaint gp = new GradientPaint(bx, by, top, bx, by + bh, base);
                    g.setPaint(gp);
                    g.fillRoundRect(bx, by, barW, bh, 6, 6);

                    // Bright top edge
                    g.setColor(brighten(top, 1.15f));
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawLine(bx + 3, by + 1, bx + barW - 3, by + 1);
                    g.setStroke(new BasicStroke(1f));
                }

                // ── "QS" / "BL" / "YR" tag directly above bar ─────────────────
                g.setFont(new Font("Monospaced", Font.BOLD, 12));
                int tagW = g.getFontMetrics().stringWidth(algoTag);
                int tagX = bx + (barW - tagW) / 2;
                // Shadow
                g.setColor(BG);
                g.drawString(algoTag, tagX + 1, by - 14 + 1);
                // Tag in vivid colour
                g.setColor(bi == 0 ? brighten(base, 1.5f) : brighten(base, 1.2f));
                g.drawString(algoTag, tagX, by - 14);

                // Value label above the tag
                g.setFont(new Font("Monospaced", Font.PLAIN, 9));
                String vl = formatNs(val);
                int vlW   = g.getFontMetrics().stringWidth(vl);
                int vlX   = bx + (barW - vlW) / 2;
                g.setColor(TEXT_MAIN);
                g.drawString(vl, vlX, by - 26);
            }

            // Config group label below X axis
            g.setColor(TEXT_DIM);
            g.setFont(new Font("Monospaced", Font.PLAIN, 11));
            drawCenteredString(g, configs[gi], gx + groupW / 2, padT + plotH + 18);
        }

        // Legend
        drawBarLegend(g, padL, IMG_H - padB + 46, configs);

        // Axis labels
        g.setColor(TEXT_DIM);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        drawVerticalString(g, "Time (nanoseconds)", padL - 72, padT + plotH / 2);
        drawCenteredString(g, "Input Configuration", padL + plotW / 2, IMG_H - 8);

        g.dispose();
        return img;
    }

    static void drawBarLegend(Graphics2D g, int x, int y, String[] configs) {
        g.setFont(new Font("Monospaced", Font.BOLD, 11));

        // "QS =" label
        g.setColor(new Color(0, 200, 255));
        g.drawString("QS = QuickSelect(Lomuto)", x, y);

        // "BL =" label
        g.setColor(new Color(210, 225, 255));
        g.drawString("BL = Baseline(Arrays.sort+RunDetection)", x + 290, y);

        // "YR =" label
        g.setColor(new Color(200, 100, 255));
        g.drawString("YR = YAROSLAVSKIY(DualPivot)", x + 660, y);

        // Config swatches
        int swY = y + 18;
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        for (int i = 0; i < configs.length; i++) {
            int lx = x + i * 240;
            g.setColor(QS_COLORS[i]);
            g.fillRoundRect(lx,      swY - 9, 11, 11, 3, 3);
            g.setColor(BL_COLORS[i]);
            g.fillRoundRect(lx + 15, swY - 9, 11, 11, 3, 3);
            g.setColor(YR_COLORS[i]);
            g.fillRoundRect(lx + 30, swY - 9, 11, 11, 3, 3);
            g.setColor(TEXT_DIM);
            g.drawString(configs[i], lx + 46, swY);
        }
    }

    // ── Line Chart (per config, both algos) ───────────────────────────────────
    static BufferedImage renderLineChart(Map<String, Double> avg,
                                         String config,
                                         int[] sizes,
                                         String[] algos) {
        BufferedImage img = new BufferedImage(IMG_W, IMG_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = setupGraphics(img);

        // Extra right padding for end-of-line labels
        int padL = 100, padR = 160, padT = 75, padB = 90;
        int plotW = IMG_W - padL - padR;
        int plotH = IMG_H - padT - padB;

        double maxVal = 1;
        for (String alg : algos)
            for (int s : sizes)
                maxVal = Math.max(maxVal, getAvg(avg, s, config, alg));
        maxVal *= 1.15;

        g.setColor(TEXT_MAIN);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        drawCenteredString(g, "Time vs. Dataset Size  —  " + config + " Input",
            padL + plotW / 2, padT - 32);

        drawAxes(g, padL, padT, plotW, plotH);
        drawYGrid(g, padL, padT, plotW, plotH, maxVal, 6);
        drawXGrid(g, padL, padT, plotW, plotH, sizes);

        Color[]  lineColors = {LINE_QS, LINE_BL, LINE_YR};
        String[] shortNames = {"QuickSelect", "Baseline", "Yaroslavskiy"};

        for (int ai = 0; ai < algos.length; ai++) {
            drawLineWithEndLabel(g, avg, config, algos[ai], sizes,
                padL, padT, plotW, plotH, maxVal, lineColors[ai], shortNames[ai]);
        }

        drawSimpleLegend(g, padL, IMG_H - padB + 38, lineColors,
            new String[]{"QuickSelect(Lomuto)", "Baseline(Arrays.sort+RunDetection)", "YAROSLAVSKIY(DualPivot)"});

        g.setColor(TEXT_DIM);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        drawVerticalString(g, "Time (nanoseconds)", padL - 78, padT + plotH / 2);
        drawCenteredString(g, "Dataset Size (n)", padL + plotW / 2, IMG_H - 8);

        g.dispose();
        return img;
    }

    // ── Combo Line Chart (QuickSelect, all configs) ───────────────────────────
    static BufferedImage renderComboLineChart(Map<String, Double> avg,
                                               int[] sizes,
                                               String[] configs,
                                               String[] algos) {
        BufferedImage img = new BufferedImage(IMG_W, IMG_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = setupGraphics(img);

        int padL = 100, padR = 160, padT = 75, padB = 90;
        int plotW = IMG_W - padL - padR;
        int plotH = IMG_H - padT - padB;

        String alg = algos[0]; // QuickSelect
        double maxVal = 1;
        for (String cfg : configs)
            for (int s : sizes)
                maxVal = Math.max(maxVal, getAvg(avg, s, cfg, alg));
        maxVal *= 1.15;

        g.setColor(TEXT_MAIN);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        drawCenteredString(g, "QuickSelect(Lomuto)  —  All Configurations vs. Dataset Size",
            padL + plotW / 2, padT - 32);

        drawAxes(g, padL, padT, plotW, plotH);
        drawYGrid(g, padL, padT, plotW, plotH, maxVal, 6);
        drawXGrid(g, padL, padT, plotW, plotH, sizes);

        for (int ci = 0; ci < configs.length; ci++) {
            drawLineWithEndLabel(g, avg, configs[ci], alg, sizes,
                padL, padT, plotW, plotH, maxVal, QS_COLORS[ci], configs[ci]);
        }

        drawSimpleLegend(g, padL, IMG_H - padB + 38, QS_COLORS, configs);

        g.setColor(TEXT_DIM);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        drawVerticalString(g, "Time (nanoseconds)", padL - 78, padT + plotH / 2);
        drawCenteredString(g, "Dataset Size (n)", padL + plotW / 2, IMG_H - 8);

        g.dispose();
        return img;
    }

    // ── Combo Line Chart (Yaroslavskiy, all configs) ──────────────────────────
    static BufferedImage renderYaroComboLineChart(Map<String, Double> avg,
                                                   int[] sizes,
                                                   String[] configs) {
        BufferedImage img = new BufferedImage(IMG_W, IMG_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = setupGraphics(img);

        int padL = 100, padR = 160, padT = 75, padB = 90;
        int plotW = IMG_W - padL - padR;
        int plotH = IMG_H - padT - padB;

        String alg = "YAROSLAVSKIY(DualPivot)";
        double maxVal = 1;
        for (String cfg : configs)
            for (int s : sizes)
                maxVal = Math.max(maxVal, getAvg(avg, s, cfg, alg));
        maxVal *= 1.15;

        g.setColor(TEXT_MAIN);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        drawCenteredString(g, "YAROSLAVSKIY(DualPivot)  —  All Configurations vs. Dataset Size",
            padL + plotW / 2, padT - 32);

        drawAxes(g, padL, padT, plotW, plotH);
        drawYGrid(g, padL, padT, plotW, plotH, maxVal, 6);
        drawXGrid(g, padL, padT, plotW, plotH, sizes);

        for (int ci = 0; ci < configs.length; ci++) {
            drawLineWithEndLabel(g, avg, configs[ci], alg, sizes,
                padL, padT, plotW, plotH, maxVal, YR_COLORS[ci], configs[ci]);
        }

        drawSimpleLegend(g, padL, IMG_H - padB + 38, YR_COLORS, configs);

        g.setColor(TEXT_DIM);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        drawVerticalString(g, "Time (nanoseconds)", padL - 78, padT + plotH / 2);
        drawCenteredString(g, "Dataset Size (n)", padL + plotW / 2, IMG_H - 8);

        g.dispose();
        return img;
    }

    // ── Drawing primitives ────────────────────────────────────────────────────
    static Graphics2D setupGraphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(BG);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        return g;
    }

    static void drawAxes(Graphics2D g, int padL, int padT, int plotW, int plotH) {
        g.setColor(new Color(90, 100, 140));
        g.setStroke(new BasicStroke(1.8f));
        g.drawLine(padL, padT, padL, padT + plotH);
        g.drawLine(padL, padT + plotH, padL + plotW, padT + plotH);
        g.setStroke(new BasicStroke(1f));
    }

    static void drawYGrid(Graphics2D g, int padL, int padT,
                          int plotW, int plotH, double maxVal, int yTicks) {
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        for (int i = 0; i <= yTicks; i++) {
            double val = maxVal * i / yTicks;
            int    yPx = padT + plotH - (int)(plotH * i / yTicks);
            g.setColor(GRID);
            g.drawLine(padL, yPx, padL + plotW, yPx);
            g.setColor(TEXT_DIM);
            String label = formatNs(val);
            g.drawString(label, padL - 8 - g.getFontMetrics().stringWidth(label), yPx + 4);
        }
    }

    static void drawXGrid(Graphics2D g, int padL, int padT,
                          int plotW, int plotH, int[] sizes) {
        for (int xi = 0; xi < sizes.length; xi++) {
            int xPx = padL + xi * plotW / (sizes.length - 1);
            g.setColor(GRID);
            g.drawLine(xPx, padT, xPx, padT + plotH);
            g.setColor(TEXT_DIM);
            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            drawCenteredString(g, formatSize(sizes[xi]), xPx, padT + plotH + 16);
        }
    }

    /**
     * Draws a line with dots AND a label at the right endpoint.
     */
    static void drawLineWithEndLabel(Graphics2D g, Map<String, Double> avg,
                                      String config, String algo, int[] sizes,
                                      int padL, int padT, int plotW, int plotH,
                                      double maxVal, Color color, String endLabel) {
        int[] xs = new int[sizes.length];
        int[] ys = new int[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            double val = getAvg(avg, sizes[i], config, algo);
            xs[i] = padL + i * plotW / (sizes.length - 1);
            ys[i] = padT + plotH - (int)(plotH * Math.min(val, maxVal) / maxVal);
        }

        // Line
        g.setColor(color);
        g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i < sizes.length; i++)
            g.drawLine(xs[i-1], ys[i-1], xs[i], ys[i]);

        // Dots
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < sizes.length; i++) {
            g.setColor(BG);
            g.fillOval(xs[i] - 5, ys[i] - 5, 10, 10);
            g.setColor(color);
            g.fillOval(xs[i] - 4, ys[i] - 4, 8, 8);
        }

        // ── End-of-line label ──────────────────────────────────────────────────
        int lastX = xs[sizes.length - 1];
        int lastY = ys[sizes.length - 1];

        // Short connector tick
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(lastX + 5, lastY, lastX + 14, lastY);
        g.setStroke(new BasicStroke(1f));

        // Dark shadow pass for legibility over grid lines
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        int labelX = lastX + 17;
        int labelY = lastY + 5;
        g.setColor(BG);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                g.drawString(endLabel, labelX + dx, labelY + dy);

        // Vivid label
        g.setColor(brighten(color, 1.3f));
        g.drawString(endLabel, labelX, labelY);
    }

    static void drawSimpleLegend(Graphics2D g, int x, int y, Color[] colors, String[] labels) {
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        int itemW = 240;
        for (int i = 0; i < labels.length; i++) {
            int lx = x + i * itemW;
            g.setColor(colors[i]);
            g.fillRoundRect(lx, y - 10, 14, 14, 4, 4);
            g.setColor(TEXT_MAIN);
            g.drawString(labels[i], lx + 20, y + 2);
        }
    }

    static void drawCenteredString(Graphics2D g, String s, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }

    static void drawVerticalString(Graphics2D g, String s, int cx, int cy) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.rotate(-Math.PI / 2, cx, cy);
        drawCenteredString(g2, s, cx, cy);
        g2.dispose();
    }

    // ── Colour utilities ──────────────────────────────────────────────────────
    static Color brighten(Color c, float factor) {
        return new Color(
            Math.min(255, (int)(c.getRed()   * factor)),
            Math.min(255, (int)(c.getGreen() * factor)),
            Math.min(255, (int)(c.getBlue()  * factor))
        );
    }

    // ── Formatting ────────────────────────────────────────────────────────────
    static String formatNs(double ns) {
        if (ns >= 1_000_000_000) return String.format("%.2fs",  ns / 1e9);
        if (ns >= 1_000_000)     return String.format("%.2fms", ns / 1e6);
        if (ns >= 1_000)         return String.format("%.1fµs", ns / 1e3);
        return String.format("%.0fns", ns);
    }

    static String formatSize(int n) {
        if (n >= 1_000_000) return (n / 1_000_000) + "M";
        if (n >= 1_000)     return (n / 1_000) + "K";
        return String.valueOf(n);
    }

    // ── Preview window ────────────────────────────────────────────────────────
    static void showPreview(String dir) {
        File[] pngs = new File(dir).listFiles((d, n) -> n.endsWith(".png"));
        if (pngs == null || pngs.length == 0) return;
        Arrays.sort(pngs);

        JFrame frame = new JFrame("Benchmark Charts Preview");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG);
        tabs.setForeground(TEXT_MAIN);

        for (File f : pngs) {
            try {
                BufferedImage bi = ImageIO.read(f);
                JLabel lbl = new JLabel(new ImageIcon(bi.getScaledInstance(
                    (int)(IMG_W * 0.85), (int)(IMG_H * 0.85), Image.SCALE_SMOOTH)));
                lbl.setBackground(BG);
                lbl.setOpaque(true);
                tabs.addTab(f.getName().replace(".png", ""), new JScrollPane(lbl));
            } catch (IOException ignored) {}
        }

        frame.add(tabs);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}