package com.github.davidmoten.aws.lw.client;

import java.io.File;
import java.util.Arrays;
import java.util.List;

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
    }

}
