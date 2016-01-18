package net.monofraps.influxmetrics.jvm;

import com.google.common.collect.ImmutableList;
import net.monofraps.influxmetrics.InfluxSeriesRegistry;
import net.monofraps.influxmetrics.MetricTag;
import net.monofraps.influxmetrics.fields.Gauge;
import net.monofraps.influxmetrics.fields.IMeasurementField;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Creates measurement series for JVM memory usage.
 *
 * Measurement:
 *  `measurementName` (defaults to 'jvm_memory')
 * Tags:
 *  memoryType
 *  poolName - for memoryType=pool
 * Fields:
 *  init, used, max, committed, usage
 */
public class MemoryUsageMetrics {
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");

    public static final String INIT_FIELD_NAME = "init";
    public static final String USED_FIELD_NAME = "used";
    public static final String MAX_FIELD_NAME = "max";
    public static final String COMMITTED_FIELD_NAME = "committed";
    public static final String USAGE_FIELD_NAME = "usage";

    private final MemoryMXBean mxBean;
    private final List<MemoryPoolMXBean> memoryPools;
    private final String measurementName;

    /**
     * Creates new a measurement series for each memory type and pool using the default MX beans and measurement name 'jvm_memory'.
     */
    public MemoryUsageMetrics() {
        this("jvm_memory");
    }

    /**
     * Creates new a measurement series for each memory type and pool using the default MX beans and the provided measurement name.
     * @param measurementName The measurement name to use.
     */
    public MemoryUsageMetrics(String measurementName) {
        this(ManagementFactory.getMemoryMXBean(), ManagementFactory.getMemoryPoolMXBeans(), measurementName);
    }

    /**
     * Creates new a measurement series for each memory type and pool using the provided MX beans and measurement name.
     * @param measurementName The measurement name to use.
     * @param memoryPools The memory pool MX beans to use.
     * @param mxBean The memory MX bean to use.
     */
    public MemoryUsageMetrics(MemoryMXBean mxBean, Collection<MemoryPoolMXBean> memoryPools, String measurementName) {
        this.mxBean = mxBean;
        this.measurementName = measurementName;
        this.memoryPools = new ArrayList<>(memoryPools);
    }

    public void registerSeries(InfluxSeriesRegistry registry) {
        createMeasurementSeries(registry, ImmutableList.of(new MetricTag("memoryType", "heap")), mxBean.getHeapMemoryUsage());
        createMeasurementSeries(registry, ImmutableList.of(new MetricTag("memoryType", "non-heap")), mxBean.getNonHeapMemoryUsage());

        for (final MemoryPoolMXBean pool : memoryPools) {
            final String poolName = WHITESPACE.matcher(pool.getName()).replaceAll("-");
            createMeasurementSeries(registry, ImmutableList.of(new MetricTag("memoryType", "pool"), new MetricTag("poolName", poolName)), pool.getUsage());
        }
    }

    private void createMeasurementSeries(InfluxSeriesRegistry registry, List<MetricTag> tags, MemoryUsage memoryUsage) {
        List<IMeasurementField> fields = new ArrayList<>(5);
        fields.add(new Gauge<>(INIT_FIELD_NAME, memoryUsage::getInit));
        fields.add(new Gauge<>(USED_FIELD_NAME, memoryUsage::getUsed));
        fields.add(new Gauge<>(MAX_FIELD_NAME, memoryUsage::getMax));
        fields.add(new Gauge<>(COMMITTED_FIELD_NAME, memoryUsage::getCommitted));
        fields.add(new Gauge<>(USAGE_FIELD_NAME, () -> memoryUsage.getUsed() / memoryUsage.getMax() == -1 ? memoryUsage.getCommitted() : memoryUsage.getMax()));
        registry.timeSeries(measurementName, tags, fields);
    }
}
