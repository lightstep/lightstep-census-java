package com.lightstep.opencensus.exporter;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;

import com.lightstep.tracer.grpc.Span;
import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.Options;
import io.opencensus.common.Scope;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.SpanBuilder;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class LightStepExporterHandlerTest {

  @Test
  @SuppressWarnings("unchecked")
  public void export() throws Exception {
    Tracer tracer = Tracing.getTracer();

    JRETracer jreTracer = new JRETracer(new Options.OptionsBuilder()
        .withAccessToken("token")
        .withDisableReportingLoop(true).build());

    LightStepTraceExporter.createAndRegister(jreTracer);

    SpanBuilder spanBuilder = tracer.spanBuilder("span-name").setRecordEvents(true)
        .setSampler(Samplers.alwaysSample());

    Scope scopedSpan = spanBuilder.startScopedSpan();
    tracer.getCurrentSpan().addAnnotation("test-annotation");
    tracer.getCurrentSpan()
        .putAttribute("key-attr", AttributeValue.stringAttributeValue("value-attr"));
    scopedSpan.close();

    Field f = jreTracer.getClass().getSuperclass().getDeclaredField("spans");
    f.setAccessible(true);
    ArrayList<Span> spans = (ArrayList<Span>) f.get(jreTracer);

    await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(spans), equalTo(1));
    Span span = spans.get(0);

    assertEquals("span-name", span.getOperationName());
    assertEquals("key-attr", span.getTags(0).getKey());
    assertEquals("value-attr", span.getTags(0).getStringValue());
    assertEquals("test-annotation", span.getLogs(0).getFields(0).getStringValue());
  }

  private Callable<Integer> reportedSpansSize(final ArrayList<Span> spans) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return spans.size();
      }
    };
  }

}