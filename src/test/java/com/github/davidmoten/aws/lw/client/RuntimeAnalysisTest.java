package com.github.davidmoten.aws.lw.client;

import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;

import org.davidmoten.kool.Statistics;
import org.davidmoten.kool.Stream;
import org.junit.Test;

public class RuntimeAnalysisTest {

    @Test
    public void test() {
        report("src/test/resources/one-time-link-lambda-runtimes.txt");
        report("src/test/resources/one-time-link-lambda-runtimes-sdk-v1.txt");
        report("src/test/resources/one-time-link-lambda-runtimes-sdk-v2.txt");
    }

    private void report(String filename) {
        List<Record> list = Stream.lines(new File(filename)) //
                .map(line -> line.trim()) //
                .filter(line -> !line.isEmpty()) //
                .map(line -> line.replaceAll("\\s+", " ")) //
                .map(line -> line.split(" ")) //
                .map(x -> new Record(Double.parseDouble(x[0]), Double.parseDouble(x[1]),
                        Double.parseDouble(x[2]) * 1000)) //
                .toList().get();

        Stream.from(list) //
                .statistics(x -> x.coldStartRuntime2GBLight)//
                .println().go();

        Stream.from(list) //
                .statistics(x -> x.actualWarmStartRuntime2GBLightAverage()).println().go();
        ;

        Stream.from(list) //
                .statistics(x -> x.apigLambdaRequestTimeMs).println().go();

        Stream.from(list) //
                .statistics(x -> x.apigLambdaRequestTimeMs - x.coldStartRuntime2GBLight).println()
                .go();
    }

    static final class Record {
        final double coldStartRuntime2GBLight;
        final double warmStartRuntime2GBLight10SampleAverage;
        final double apigLambdaRequestTimeMs;

        public Record(double coldStartRuntime2GBLight,
                double warmStartRuntime2GBLight9SampleAverage, double apigLambdaRequestTimeMs) {
            this.coldStartRuntime2GBLight = coldStartRuntime2GBLight;
            this.warmStartRuntime2GBLight10SampleAverage = warmStartRuntime2GBLight9SampleAverage;
            this.apigLambdaRequestTimeMs = apigLambdaRequestTimeMs;
        }

        public double actualWarmStartRuntime2GBLightAverage() {
            return (warmStartRuntime2GBLight10SampleAverage * 10 - coldStartRuntime2GBLight) / 9;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Record [coldStartRuntime2GBLight=");
            builder.append(coldStartRuntime2GBLight);
            builder.append(", warmStartRuntime2GBLight9SampleAverage=");
            builder.append(warmStartRuntime2GBLight10SampleAverage);
            builder.append("]");
            return builder.toString();
        }

    }

    private static Stream<String> lines(String filename) {
        return Stream.lines(new File(filename)) //
                .map(line -> line.trim()) //
                .filter(line -> line.length() > 0) //
                .filter(line -> !line.startsWith("#"));
    }

    @Test
    public void testStaticFields() {
        System.out.println("// results with static fields");
        lines("src/test/resources/one-time-link-lambda-runtimes-2.txt") //
                .map(line -> line.split("\\s+")) //
                .skip(1) //
                .map(items -> Double.parseDouble(items[0])) //
                .statistics(x -> x) //
                .println().go();
        lines("src/test/resources/one-time-link-lambda-runtimes-sdk-v2-2.txt") //
                .filter(line -> line.startsWith("C")) //
                .map(line -> line.split(",")) //
                .skip(1) //
                .map(items -> Double.parseDouble(items[2])) //
                .statistics(x -> x).println().go();
        lines("src/test/resources/one-time-link-lambda-runtimes-sdk-v2-2.txt") //
                .filter(line -> line.startsWith("W")) //
                .map(line -> line.split(",")) //
                .map(items -> Double.parseDouble(items[2])) //
                .statistics(x -> x).println().go();

        System.out.println("request time analysis with static fields");
        System.out.println("| | Average | Stdev | Min | Max | n |");
        System.out.println("|-------|-------|-------|------|-------|------|");
        reportRequestTimeStats("AWS SDK v1", 0);
        reportRequestTimeStats("AWS SDK v2", 1);
        reportRequestTimeStats("lightweight", 2);
    }

    @Test
    public void testStaticFields2() {
//        lines(
//                "src/test/resources/one-time-link-hourly-store-request-times-raw.txt").skip(1) //
//                        .bufferUntil((list, x) -> x.contains("AEST"), true) //
//                        .map(x -> x.subList(1, x.size())) //
//                        .println().go();
        Stream<HashMap<Integer, List<String>>> o = lines(
                "src/test/resources/one-time-link-hourly-store-request-times-raw.txt").skip(1) //
                        .bufferUntil((list, x) -> x.contains("AEST"), true) //
                        .map(list -> list.subList(1, list.size())) //
                        .map(list -> Stream.from(list).map(y -> y.substring(10, y.length() - 1))
                                .toList().get()) //
                        .filter(list -> !list.stream().anyMatch(x -> Double.parseDouble(x) > 10))
                        .map(x -> Stream.from(x) //
                                .mapWithIndex() //
                                .groupByList( //
                                        HashMap::new, //
                                        y -> y.index() % 3, //
                                        y -> y.value())
                                .get());

        System.out.println("cold start");
        for (int i = 0; i < 3; i++) {
            int j = i;
            o.map(x -> x.get(j)).map(x -> x.get(0)).statistics(Double::parseDouble).println().go();
        }

        System.out.println("warm start");
        Statistics light = o.map(x -> x.get(0)).flatMap(x -> Stream.from(x.subList(1, x.size())))
                .statistics(Double::parseDouble).get();
        for (int i = 0; i < 3; i++) {
            int j = i;
            Statistics stats = o.map(x -> x.get(j))
                    .flatMap(x -> Stream.from(x.subList(1, x.size())))
                    .statistics(Double::parseDouble).println().get();
            System.out.println("z score=" + Math.abs(light.mean() - stats.mean())
                    / stats.standardDeviation() * Math.sqrt(light.count()));
        }
        for (int i = 0; i < 3; i++) {
            int j = i;
            o.map(x -> x.get(j)).flatMap(x -> Stream //
                    .from(x.subList(1, x.size()))) //
                    .statistics(Double::parseDouble) //
                    .map(x -> markdownRow(j + "", x)) //
                    .println().go();
        }
    }

    private static void reportRequestTimeStats(String name, int index) {
        lines("src/test/resources/one-time-link-hourly-store-request-times.txt") //
                .map(line -> line.split("\\s+")) //
                .map(items -> Double.parseDouble(items[index])) //
                .statistics(x -> x) //
                .map(x -> markdownRow(name, x)) //
                .println() //
                .go();
    }

    public static String markdownRow(String name, Statistics x) {
        DecimalFormat df = new DecimalFormat("0.000");
        return "| **" + name + "** | " + df.format(x.mean()) + " | "
                + df.format(x.standardDeviation()) + " | " + df.format(x.min()) + " | "
                + df.format(x.max()) + " | " + x.count() + " |";
    }

}
