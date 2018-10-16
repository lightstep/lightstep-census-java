package com.lightstep.opencensus.exporter;


import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.verify;

import io.opencensus.trace.export.SpanExporter;
import io.opencensus.trace.export.SpanExporter.Handler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LightStepTraceExporterTest {

  @Mock
  private SpanExporter spanExporter;
  @Mock
  private Handler handler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void registerUnregisterLightStepExporter() {
    LightStepTraceExporter.register(spanExporter, handler);
    verify(spanExporter)
        .registerHandler(
            eq("com.lightstep.opencensus.exporter.LightStepTraceExporter"), same(handler));
    LightStepTraceExporter.unregister(spanExporter);
    verify(spanExporter)
        .unregisterHandler(eq("com.lightstep.opencensus.exporter.LightStepTraceExporter"));
  }

}