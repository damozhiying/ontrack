package net.nemerosa.ontrack.extension.metrics.influxdb.client;

import com.codahale.metrics.*;
import com.codahale.metrics.Timer;
import net.nemerosa.ontrack.model.metrics.OntrackTaggedMetrics;
import net.nemerosa.ontrack.model.metrics.TaggedMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class InfluxDbReporter extends ScheduledReporter {

    public static final class Builder {
        private final MetricRegistry registry;
        private Map<String, String> tags;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private boolean skipIdleMetrics;
        private Collection<OntrackTaggedMetrics> taggedMetrics = Collections.emptyList();

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.tags = null;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
        }

        /**
         * Use the list of tagged metrics sources
         */
        public Builder withTaggedMetrics(Collection<OntrackTaggedMetrics> taggedMetrics) {
            this.taggedMetrics = Collections.unmodifiableCollection(taggedMetrics);
            return this;
        }

        /**
         * Add these tags to all metrics.
         *
         * @param tags a map containing tags common to all metrics
         * @return {@code this}
         */
        public Builder withTags(Map<String, String> tags) {
            this.tags = Collections.unmodifiableMap(tags);
            return this;
        }

        /**
         * Convert rates to the given time unit.
         *
         * @param rateUnit a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Only report metrics that have changed.
         *
         * @param skipIdleMetrics true/false for skipping metrics not reported
         * @return {@code this}
         */
        public Builder skipIdleMetrics(@SuppressWarnings("SameParameterValue") boolean skipIdleMetrics) {
            this.skipIdleMetrics = skipIdleMetrics;
            return this;
        }

        public InfluxDbReporter build(final InfluxDbSender influxDb) {
            return new InfluxDbReporter(registry, influxDb, tags, rateUnit, durationUnit, filter, skipIdleMetrics, taggedMetrics);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(InfluxDbReporter.class);
    private final InfluxDbSender influxDb;
    private final boolean skipIdleMetrics;
    private final Map<String, Long> previousValues;
    private final Collection<OntrackTaggedMetrics> taggedMetrics;

    private InfluxDbReporter(final MetricRegistry registry, final InfluxDbSender influxDb, final Map<String, String> tags,
                             final TimeUnit rateUnit, final TimeUnit durationUnit, final MetricFilter filter, final boolean skipIdleMetrics, Collection<OntrackTaggedMetrics> taggedMetrics) {
        super(registry, "influxDb-reporter", filter, rateUnit, durationUnit);
        this.influxDb = influxDb;
        this.taggedMetrics = taggedMetrics;
        influxDb.setTags(tags);
        this.skipIdleMetrics = skipIdleMetrics;
        this.previousValues = new TreeMap<>();
    }

    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    @Override
    public void report(final SortedMap<String, Gauge> gauges, final SortedMap<String, Counter> counters,
                       final SortedMap<String, Histogram> histograms, final SortedMap<String, Meter> meters, final SortedMap<String, Timer> timers) {
        final long now = System.currentTimeMillis();

        try {
            influxDb.flush();

            for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                reportGauge(entry.getKey(), entry.getValue(), now);
            }

            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                reportCounter(entry.getKey(), entry.getValue(), now);
            }

            for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
                reportHistogram(entry.getKey(), entry.getValue(), now);
            }

            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                reportMeter(entry.getKey(), entry.getValue(), now);
            }

            for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                reportTimer(entry.getKey(), entry.getValue(), now);
            }

            // Tagged metrics
            taggedMetrics.stream()
                    .flatMap(source -> source.getTaggedMetrics().stream())
                    .forEach(this::reportTaggedMetric);

            if (influxDb.hasSeriesData()) {
                influxDb.writeData();
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to report to InfluxDB. Discarding data.", e);
        }
    }

    private <T extends Number> void reportTaggedMetric(TaggedMetric<T> metric) {
        influxDb.appendPoints(
                new InfluxDbPoint(
                        metric.getName(),
                        metric.getTags(),
                        metric.getTimestamp().getTime(),
                        Collections.singletonMap("value", metric.getValue())
                )
        );
    }

    private void reportTimer(String name, Timer timer, long now) {
        if (canSkipMetric(name, timer)) {
            return;
        }
        final Snapshot snapshot = timer.getSnapshot();
        Map<String, Object> fields = new HashMap<>();
        fields.put("count", timer.getCount());
        fields.put("min", convertDuration(snapshot.getMin()));
        fields.put("max", convertDuration(snapshot.getMax()));
        fields.put("mean", convertDuration(snapshot.getMean()));
        fields.put("std-dev", convertDuration(snapshot.getStdDev()));
        fields.put("median", convertDuration(snapshot.getMedian()));
        fields.put("50-percentile", convertDuration(snapshot.getMedian()));
        fields.put("75-percentile", convertDuration(snapshot.get75thPercentile()));
        fields.put("95-percentile", convertDuration(snapshot.get95thPercentile()));
        fields.put("98-percentile", convertDuration(snapshot.get98thPercentile()));
        fields.put("99-percentile", convertDuration(snapshot.get99thPercentile()));
        fields.put("999-percentile", convertDuration(snapshot.get999thPercentile()));
        fields.put("one-minute", convertRate(timer.getOneMinuteRate()));
        fields.put("five-minute", convertRate(timer.getFiveMinuteRate()));
        fields.put("fifteen-minute", convertRate(timer.getFifteenMinuteRate()));
        fields.put("mean-rate", convertRate(timer.getMeanRate()));
        fields.put("run-count", timer.getCount());
        influxDb.appendPoints(new InfluxDbPoint(
                name,
                Collections.emptyMap(),
                now,
                fields));
    }

    private void reportHistogram(String name, Histogram histogram, long now) {
        if (canSkipMetric(name, histogram)) {
            return;
        }
        final Snapshot snapshot = histogram.getSnapshot();
        Map<String, Object> fields = new HashMap<>();
        fields.put("count", histogram.getCount());
        fields.put("min", snapshot.getMin());
        fields.put("max", snapshot.getMax());
        fields.put("mean", snapshot.getMean());
        fields.put("median", snapshot.getMedian());
        fields.put("std-dev", snapshot.getStdDev());
        fields.put("50-percentile", snapshot.getMedian());
        fields.put("75-percentile", snapshot.get75thPercentile());
        fields.put("95-percentile", snapshot.get95thPercentile());
        fields.put("98-percentile", snapshot.get98thPercentile());
        fields.put("99-percentile", snapshot.get99thPercentile());
        fields.put("999-percentile", snapshot.get999thPercentile());
        fields.put("run-count", histogram.getCount());
        influxDb.appendPoints(new InfluxDbPoint(
                name,
                Collections.emptyMap(),
                now,
                fields));
    }

    private void reportCounter(String name, Counter counter, long now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("count", counter.getCount());
        influxDb.appendPoints(new InfluxDbPoint(
                name,
                Collections.emptyMap(),
                now,
                fields));
    }

    private void reportGauge(String name, Gauge<?> gauge, long now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("value", gauge.getValue());
        influxDb.appendPoints(new InfluxDbPoint(
                name,
                Collections.emptyMap(),
                now,
                fields));
    }

    private void reportMeter(String name, Metered meter, long now) {
        if (canSkipMetric(name, meter)) {
            return;
        }
        Map<String, Object> fields = new HashMap<>();
        fields.put("count", meter.getCount());
        fields.put("one-minute", convertRate(meter.getOneMinuteRate()));
        fields.put("five-minute", convertRate(meter.getFiveMinuteRate()));
        fields.put("fifteen-minute", convertRate(meter.getFifteenMinuteRate()));
        fields.put("mean-rate", convertRate(meter.getMeanRate()));
        influxDb.appendPoints(new InfluxDbPoint(
                name,
                Collections.emptyMap(),
                now,
                fields));
    }

    private boolean canSkipMetric(String name, Counting counting) {
        boolean isIdle = (calculateDelta(name, counting.getCount()) == 0);
        if (skipIdleMetrics && !isIdle) {
            previousValues.put(name, counting.getCount());
        }
        return skipIdleMetrics && isIdle;
    }

    private long calculateDelta(String name, long count) {
        Long previous = previousValues.get(name);
        if (previous == null) {
            return -1;
        }
        if (count < previous) {
            LOGGER.warn("Saw a non-monotonically increasing value for metric '{}'", name);
            return 0;
        }
        return count - previous;
    }

}
