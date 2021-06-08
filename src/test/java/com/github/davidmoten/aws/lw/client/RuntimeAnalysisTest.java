package com.github.davidmoten.aws.lw.client;

import java.io.File;
import java.util.List;

import org.davidmoten.kool.Stream;
import org.junit.Test;

public class RuntimeAnalysisTest {

    @Test
    public void test() {
        report("src/test/resources/one-time-link-lambda-runtimes.txt");
        report("src/test/resources/one-time-link-lambda-runtimes-sdk-v1.txt");
    }

    private void report(String filename) {
        List<Record> list = Stream
                .lines(new File(filename)) //
                .map(line -> line.trim()) //
                .filter(line -> !line.isEmpty()) //
                .map(line -> line.replaceAll("\\s+", " ")) //
                .map(line -> line.split(" ")) //
                .map(x -> new Record(Double.parseDouble(x[0]), Double.parseDouble(x[1]),
                        Double.parseDouble(x[2]))) //
                .toList().get();

        Stream.from(list) //
                .statistics(x -> x.coldStartRuntime2GBLight)//
                .forEach(System.out::println);

        Stream.from(list) //
                .statistics(x -> x.actualWarmStartRuntime2GBLightAverage())
                .forEach(System.out::println);

        Stream.from(list) //
                .statistics(x -> x.apigLambdaRequestTimeMs).forEach(System.out::println);
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

}
