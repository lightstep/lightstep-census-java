package com.lightstep.opencensus.exporter;


import com.lightstep.tracer.jre.JRETracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.export.SpanExporter;

public class LightStepTraceExporter {
  private static final String REGISTER_NAME = LightStepTraceExporter.class.getName();
  private static final Object monitor = new Object();


  private static SpanExporter.Handler handler = null;

  // Make constructor private to hide it from the API and therefore avoid users calling it.
  private LightStepTraceExporter() {
  }

  /**
   * Creates and registers the LightStep Trace exporter to the OpenCensus library.
   * Only one LightStep exporter can be registered at any point.
   *
   * @param jreTracer LightStep tracer
   * @throws IllegalStateException if a LightStep exporter is already registered.
   */
  public static void createAndRegister(JRETracer jreTracer) {
    synchronized (monitor) {
      if (handler != null) {
        throw new IllegalStateException("LightStep exporter is already registered.");
      }
      final SpanExporter.Handler newHandler = new LightStepExporterHandler(jreTracer);
      LightStepTraceExporter.handler = newHandler;
      register(Tracing.getExportComponent().getSpanExporter(), newHandler);
    }
  }

  /**
   * Registers the {@link LightStepTraceExporter}.
   *
   * @param spanExporter the instance of the {@code SpanExporter} where this service is registered.
   */
  static void register(final SpanExporter spanExporter, final SpanExporter.Handler handler) {
    spanExporter.registerHandler(REGISTER_NAME, handler);
  }

  /**
   * Unregisters the {@link LightStepTraceExporter} from the OpenCensus library.
   *
   * @throws IllegalStateException if a LightStep exporter is not registered.
   */
  public static void unregister() {
    synchronized (monitor) {
      if (handler == null) {
        throw new IllegalStateException("LightStep exporter is not registered.");
      }
      unregister(Tracing.getExportComponent().getSpanExporter());
      handler = null;
    }
  }

  /**
   * Unregisters the {@link LightStepTraceExporter}.
   *
   * @param spanExporter the instance of the {@link SpanExporter} from where this service is
   * unregistered.
   */
  static void unregister(final SpanExporter spanExporter) {
    spanExporter.unregisterHandler(REGISTER_NAME);
  }
}
