package com.sphereon.core.benchmarks;

import com.sphereon.core.api.conf.PropertySourcesPropertyResolver;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ConfigLookupPathBenchmark {

    @State(Scope.Benchmark)
    public static class ResolverState {
        PropertySourcesPropertyResolver resolver;
        String directHitKey;
        String fallbackHitKey;
        String missKey;

        @Setup(Level.Trial)
        public void setUp() {
            ResolverFixture fixture = BenchmarkFixtures.propertyResolverFixture(256);
            resolver = fixture.getResolver();
            directHitKey = fixture.getDirectHitKey();
            fallbackHitKey = fixture.getFallbackHitKey();
            missKey = fixture.getMissKey();
        }
    }

    @Benchmark
    public void directHit(ResolverState state, Blackhole blackhole) {
        blackhole.consume(state.resolver.getPropertyAsString(state.directHitKey, null));
    }

    @Benchmark
    public void fallbackHit(ResolverState state, Blackhole blackhole) {
        blackhole.consume(state.resolver.getPropertyAsString(state.fallbackHitKey, null));
    }

    @Benchmark
    public void missPath(ResolverState state, Blackhole blackhole) {
        blackhole.consume(state.resolver.getPropertyAsString(state.missKey, null));
    }
}
