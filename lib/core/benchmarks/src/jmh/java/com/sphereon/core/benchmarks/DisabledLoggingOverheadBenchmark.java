package com.sphereon.core.benchmarks;

import com.sphereon.core.api.log.LogService;
import kotlin.jvm.functions.Function0;
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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DisabledLoggingOverheadBenchmark {

    @State(Scope.Thread)
    public static class LoggingState {
        LogService logger;
        Function0<String> constantMessage;
        int sequence;

        @Setup(Level.Trial)
        public void setUp() {
            logger = BenchmarkFixtures.disabledDebugLogService();
            constantMessage = () -> "constant disabled log message";
            sequence = 0;
        }
    }

    @Benchmark
    public void noOpBaseline(LoggingState state) {
        state.sequence++;
    }

    @Benchmark
    public void disabledConstantMessageLambda(LoggingState state) {
        state.logger.debug(null, state.constantMessage);
    }

    @Benchmark
    public void disabledInterpolatedMessageLambda(LoggingState state) {
        int local = state.sequence++;
        state.logger.debug(null, () -> "tenant-" + (local & 31) + "-principal-" + local);
    }
}
