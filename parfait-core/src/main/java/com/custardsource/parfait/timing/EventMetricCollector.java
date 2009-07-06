package com.custardsource.parfait.timing;

import java.util.Map;

import org.apache.log4j.Logger;

/**
 * <p>
 * Coordinates multiple {@link MetricMeasurement MetricMeasurements} for all the controllers invoked
 * in the process of handling the user request.
 * </p>
 * <p>
 * Not thread-safe, should be used only by one thread at a time.
 * </p>
 */
public class EventMetricCollector {
    private StepMeasurements current = null;
    /**
     * The number of nested controllers invoked so far. When we hit depth of 0 we know we've reached
     * the top-level controller requested by the user.
     */
    private int depth = 0;
    private Object topLevelController;

    private final Map<MetricCollectorController, EventCounters> perControllerCounters;

    private static final Logger LOG = Logger.getLogger(EventMetricCollector.class);

    public EventMetricCollector(
            Map<MetricCollectorController, EventCounters> perControllerCounters) {
        this.perControllerCounters = perControllerCounters;
    }

    public void startTiming(Object controller, String action) {
        StepMeasurements newTiming = new StepMeasurements(current, controller.getClass(),
                action);
        for (ThreadMetric metric : perControllerCounters.get(controller).getMetricSources()) {
            newTiming.addMetricInstance(new MetricMeasurement(metric));
        }
        current = newTiming;
        topLevelController = controller;
        depth++;
        current.startAll();
    }

    public void stopTiming() {
        current.stopAll();
        depth--;
        String depthText = (depth > 0) ? "Nested (" + depth + ")" : "Top";

        String metricData = "";
        for (MetricMeasurement metric : current.getMetricInstances()) {
            metricData += "\t" + metric.getMetricName() + ": own " +
                    metric.ownTimeValueFormatted() + ", total " + metric.totalValueFormatted();
        }
        LOG.info(String.format("%s\t%s\t%s\t%s", depthText, current.getForwardTrace(), current
                .getBackTrace(), metricData));
        if (depth == 0 && perControllerCounters.containsKey(topLevelController)) {
            // We're at the top level, do our PCP perControllerCounters too
            EventCounters counters = perControllerCounters.get(topLevelController);
            for (MetricMeasurement metric : current.getMetricInstances()) {
                EventMetricCounters counter = counters.getCounterForMetric(metric
                        .getMetricSource());
                if (counter != null) {
                    counter.incrementCounters(metric.totalValue());
                }
            }
            counters.getInvocationCounter().incrementCounters(1);
        }
        current = current.getParent();
    }

    public void pauseForForward() {
        current.pauseAll();
    }

    public void resumeAfterForward() {
        current.resumeAll();
    }
}