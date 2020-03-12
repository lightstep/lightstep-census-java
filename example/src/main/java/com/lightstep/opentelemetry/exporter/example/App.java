package com.lightstep.opentelemetry.exporter.example;

import com.lightstep.opencensus.exporter.LightStepTraceExporter;
import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.Options;
import io.opencensus.common.Scope;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class App {
  public static void main(String[] args) throws Exception {
    final Properties properties = loadConfig(args);

    // 1. Create and register LS tracer
    JRETracer jreTracer = new JRETracer(new Options.OptionsBuilder()
        .withAccessToken(properties.getProperty("access_token"))
        .withComponentName(properties.getProperty("component_name"))
        .withCollectorHost(properties.getProperty("collector_host"))
        .withCollectorPort(Integer.parseInt(properties.getProperty("collector_port")))
        .withCollectorProtocol(properties.getProperty("collector_protocol"))
        .build());

    LightStepTraceExporter.createAndRegister(jreTracer);

    // 2. Configure 100% sample rate, otherwise, few traces will be sampled.
    TraceConfig traceConfig = Tracing.getTraceConfig();
    traceConfig.updateActiveTraceParams(
        traceConfig.getActiveTraceParams()
            .toBuilder()
            .setSampler(Samplers.probabilitySampler(1))
            .build());

    // 3. Get the global singleton Tracer object.
    Tracer tracer = Tracing.getTracer();

    // 4. Create a scoped span, a scoped span will automatically end when closed.
    // It implements AutoClosable, so it'll be closed when the try block ends.
    try (Scope scope = tracer.spanBuilder("main").startScopedSpan()) {
      System.out.println("About to do some busy work...");
      for (int i = 0; i < 10; i++) {
        doWork(i);
      }
    }

    // 5. Gracefully shutdown the exporter
    Tracing.getExportComponent().shutdown();

    // 6. Close the tracer, so that it'll flush queued traces
    jreTracer.close();
  }

  private static void doWork(int i) {
    // 6. Get the global singleton Tracer object.
    Tracer tracer = Tracing.getTracer();

    // 7. Start another span. If another span was already started, it'll use that span as the parent span.
    // In this example, the main method already started a span, so that'll be the parent span, and this will be
    // a child span.
    try (Scope scope = tracer.spanBuilder("doWork").startScopedSpan()) {
      // Simulate some work.
      Span span = tracer.getCurrentSpan();

      try {
        //System.out.println("doing busy work");
        Thread.sleep(10L);
      } catch (InterruptedException e) {
        // 6. Set status upon error
        span.setStatus(Status.INTERNAL.withDescription(e.toString()));
      }

      // 7. Annotate our span to capture metadata about our operation
      Map<String, AttributeValue> attributes = new HashMap<>();
      attributes.put("use", AttributeValue.stringAttributeValue("test-attr-value"));
      span.addAnnotation("Invoking doWorkInvoking", attributes);
    }
  }

  private static Properties loadConfig(String[] args)
      throws IOException {
    String file = "config.properties";
    if (args.length > 0) {
      file = args[0];
    }

    FileInputStream fs = new FileInputStream(file);
    Properties config = new Properties();
    config.load(fs);
    return config;
  }
}
