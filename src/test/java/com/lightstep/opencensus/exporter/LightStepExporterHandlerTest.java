package com.lightstep.opencensus.exporter;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.lightstep.tracer.grpc.KeyValue;
import com.lightstep.tracer.grpc.Log;
import com.lightstep.tracer.grpc.Span;
import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.Options;
import io.opencensus.common.Scope;
import io.opencensus.trace.Annotation;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.MessageEvent;
import io.opencensus.trace.MessageEvent.Type;
import io.opencensus.trace.SpanBuilder;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    String annotation = "test-annotation";
    Map<String, AttributeValue> attributes = new HashMap<String, AttributeValue>();
    attributes
        .put("annotation-attr-key", AttributeValue.stringAttributeValue("annotation-attr-value"));
    tracer.getCurrentSpan()
        .addAnnotation(Annotation.fromDescriptionAndAttributes(annotation, attributes));

    MessageEvent messageEvent = MessageEvent.builder(Type.SENT, 1).setCompressedMessageSize(1)
        .setUncompressedMessageSize(2).build();
    tracer.getCurrentSpan().addMessageEvent(messageEvent);

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

    assertTrue(find(span.getLogsList(), "annotationDescription", "test-annotation"));
    assertTrue(find(span.getLogsList(), "annotation-attr-key", "annotation-attr-value"));

    assertTrue(find(span.getLogsList(), "messageEventType", "SENT"));
    assertTrue(find(span.getLogsList(), "compressedMessageSize", 1));
    assertTrue(find(span.getLogsList(), "uncompressedMessageSize", 2));

    LightStepTraceExporter.unregister();
  }

  @Test
  public void traceIdToLong() {
    LightStepExporterHandler handler = new LightStepExporterHandler(null);

    // right most part of 463ac35c9f6413ad48485a3953bb6124 actually is 5208512171318403364L
    TraceId traceId = TraceId.fromLowerBase16("463ac35c9f6413ad48485a3953bb6124", 0);
    long traceIdLong = handler.traceIdToLong(traceId);
    assertEquals(5208512171318403364L, traceIdLong);
  }

  private Callable<Integer> reportedSpansSize(final ArrayList<Span> spans) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return spans.size();
      }
    };
  }

  private boolean find(List<Log> logs, String key, String value) {
    for (Log log : logs) {
      for (KeyValue keyValue : log.getFieldsList()) {
        if (keyValue.getKey().equals(key) && keyValue.getStringValue().equals(value)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean find(List<Log> logs, String key, int value) {
    for (Log log : logs) {
      for (KeyValue keyValue : log.getFieldsList()) {
        if (keyValue.getKey().equals(key) && keyValue.getIntValue() == value) {
          return true;
        }
      }
    }
    return false;
  }

}