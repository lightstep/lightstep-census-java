package com.lightstep.opencensus.exporter;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.Span;
import com.lightstep.tracer.shared.SpanBuilder;
import io.opencensus.common.Function;
import io.opencensus.common.Scope;
import io.opencensus.common.Timestamp;
import io.opencensus.trace.Annotation;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Sampler;
import io.opencensus.trace.Span.Kind;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.Status;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.export.SpanData;
import io.opencensus.trace.export.SpanData.TimedEvent;
import io.opencensus.trace.export.SpanExporter;
import io.opencensus.trace.samplers.Samplers;
import io.opentracing.tag.Tags;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class LightStepExporterHandler extends SpanExporter.Handler {
  private static final String STATUS_CODE = "census.status_code";
  private static final String STATUS_DESCRIPTION = "census.status_description";
  private static final Tracer tracer = Tracing.getTracer();
  private static final Sampler probabilitySampler = Samplers.probabilitySampler(0.0001);
  private final byte[] spanIdBuffer = new byte[SpanId.SIZE];
  private final byte[] traceIdBuffer = new byte[TraceId.SIZE];
  private final JRETracer jreTracer;

  public LightStepExporterHandler(JRETracer jreTracer) {
    this.jreTracer = jreTracer;
  }

  @Override
  public void export(Collection<SpanData> spanDataList) {
    // Start a new span with explicit 1/10000 sampling probability to avoid the case when user
    // sets the default sampler to always sample and we get the gRPC span of the lightstep
    // export call always sampled and go to an infinite loop.
    Scope scope =
        tracer.spanBuilder("SendLightStepSpans").setSampler(probabilitySampler).startScopedSpan();

    try {
      for (SpanData spanData : spanDataList) {
        if ("Sent.lightstep.collector.CollectorService.Report".equals(spanData.getName())) {
          // Skip LS internal span
          continue;
        }
        long startTimestamp = toEpochMicros(spanData.getStartTimestamp());
        long endTimestamp = toEpochMicros(spanData.getEndTimestamp());
        SpanContext context = spanData.getContext();
        long traceId = traceIdToLong(context.getTraceId());

        SpanBuilder spanBuilder = (SpanBuilder) jreTracer.buildSpan(spanData.getName())
            .withStartTimestamp(startTimestamp);

        if (spanData.getParentSpanId() != null && spanData.getParentSpanId().isValid()) {
          spanBuilder.asChildOf(new com.lightstep.tracer.shared.SpanContext(traceId,
              spanIdToLong(spanData.getParentSpanId())));
        }

        Span span = (Span) spanBuilder.withTraceIdAndSpanId(traceId,
            spanIdToLong(context.getSpanId())).start();

        span.setTag(Tags.SPAN_KIND.getKey(), toSpanKind(spanData));

        for (Map.Entry<String, AttributeValue> label :
            spanData.getAttributes().getAttributeMap().entrySet()) {
          addAttributeAsTag(label.getValue(), span, label.getKey());
        }

        Status status = spanData.getStatus();
        if (status != null) {
          span.setTag(STATUS_CODE, status.getCanonicalCode().toString());
          if (status.getDescription() != null) {
            span.setTag(STATUS_DESCRIPTION, status.getDescription());
          }
        }

        for (TimedEvent<Annotation> annotation : spanData.getAnnotations().getEvents()) {
          Map<String, Object> fields = new HashMap<String, Object>();
          fields.put("annotationDescription", annotation.getEvent().getDescription());
          if (annotation.getEvent().getAttributes() != null) {
            for (Entry<String, AttributeValue> entry : annotation.getEvent()
                .getAttributes().entrySet()) {
              fields.put(entry.getKey(), getAttributeValue(entry.getValue()));
            }
          }
          span.log(toEpochMicros(annotation.getTimestamp()), fields);
        }

        for (TimedEvent<io.opencensus.trace.MessageEvent> messageEvent :
            spanData.getMessageEvents().getEvents()) {
          Map<String, Object> fields = new HashMap<String, Object>();
          fields.put("messageEventType", messageEvent.getEvent().getType().name());
          fields.put("compressedMessageSize", messageEvent.getEvent().getCompressedMessageSize());
          fields
              .put("uncompressedMessageSize", messageEvent.getEvent().getUncompressedMessageSize());
          span.log(toEpochMicros(messageEvent.getTimestamp()), fields);
        }

        span.finish(endTimestamp);
      }
    } catch (Exception e) {
      tracer.getCurrentSpan().setStatus(Status.UNKNOWN.withDescription(
          e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
      throw new RuntimeException(e);
    } finally {
      scope.close();
    }
  }

  private static Object addAttributeAsTag(AttributeValue attributeValue, Span span,
      String tagName) {
    return attributeValue.match(
        stringAttributeConverter(span, tagName),
        booleanAttributeConverter(span, tagName),
        longAttributeConverter(span, tagName),
        //doubleAttributeConverter(span, tagName),
        defaultAttributeConverter(span, tagName));
  }

  private static Object getAttributeValue(AttributeValue attributeValue) {
    return attributeValue.match(
        stringAttributeConverter(),
        booleanAttributeConverter(),
        longAttributeConverter(),
        //doubleAttributeConverter(span, tagName),
        defaultAttributeConverter());
  }

  private static String toSpanKind(SpanData spanData) {
    // This is a hack because the Span API did not have Span Kind.
    if (spanData.getKind() == Kind.SERVER
        || (spanData.getKind() == null && Boolean.TRUE.equals(spanData.getHasRemoteParent()))) {
      return Tags.SPAN_KIND_SERVER;
    }

    // This is a hack because the Span API did not have Span Kind.
    if (spanData.getKind() == Kind.CLIENT || spanData.getName().startsWith("Sent.")) {
      return Tags.SPAN_KIND_CLIENT;
    }

    return null;
  }

  private long spanIdToLong(final SpanId spanId) {
    if (spanId == null) {
      return 0L;
    }
    // Attempt to minimise allocations, since SpanId#getBytes currently creates a defensive copy:
    spanId.copyBytesTo(spanIdBuffer, 0);
    return fromByteArray(spanIdBuffer);
  }

  private long fromByteArray(byte[] bytes) {
    return ByteBuffer.wrap(bytes).getLong();
  }

  private long traceIdToLong(final TraceId traceId) {
    if (traceId == null) {
      return 0L;
    }
    // Attempt to minimise allocations, since SpanId#getBytes currently creates a defensive copy:
    traceId.copyBytesTo(traceIdBuffer, 0);
    return fromByteArray(traceIdBuffer);
  }

  private static long toEpochMicros(Timestamp timestamp) {
    return SECONDS.toMicros(timestamp.getSeconds()) + NANOSECONDS.toMicros(timestamp.getNanos());
  }

  private static Function<? super String, Object> stringAttributeConverter(final Span span,
      final String tagName) {
    return new Function<String, Object>() {
      @Override
      public String apply(final String value) {
        span.setTag(tagName, value);
        return value;
      }
    };
  }

  private static Function<? super String, Object> stringAttributeConverter() {
    return new Function<String, Object>() {
      @Override
      public String apply(final String value) {
        return value;
      }
    };
  }

  private static Function<? super Boolean, Object> booleanAttributeConverter(final Span span,
      final String tagName) {
    return new Function<Boolean, Object>() {
      @Override
      public Boolean apply(final Boolean value) {
        span.setTag(tagName, value);
        return value;
      }
    };
  }

  private static Function<? super Boolean, Object> booleanAttributeConverter() {
    return new Function<Boolean, Object>() {
      @Override
      public Boolean apply(final Boolean value) {
        return value;
      }
    };
  }

  private static Function<? super Double, Object> doubleAttributeConverter(final Span span,
      final String tagName) {
    return new Function<Double, Object>() {
      @Override
      public String apply(final Double value) {
        span.setTag(tagName, value);
        return Double.toString(value);
      }
    };
  }

  private static Function<? super Double, Object> doubleAttributeConverter() {
    return new Function<Double, Object>() {
      @Override
      public String apply(final Double value) {
        return Double.toString(value);
      }
    };
  }

  private static Function<? super Long, Object> longAttributeConverter(final Span span,
      final String tagName) {
    return new Function<Long, Object>() {
      @Override
      public Long apply(final Long value) {
        span.setTag(tagName, value);
        return value;
      }
    };
  }

  private static Function<? super Long, Object> longAttributeConverter() {
    return new Function<Long, Object>() {
      @Override
      public Long apply(final Long value) {
        return value;
      }
    };
  }

  private static Function<Object, Object> defaultAttributeConverter(final Span span,
      final String tagName) {
    return new Function<Object, Object>() {
      @Override
      public Object apply(final Object value) {
        span.setTag(tagName, value.toString());
        return value;
      }
    };
  }

  private static Function<Object, Object> defaultAttributeConverter() {
    return new Function<Object, Object>() {
      @Override
      public Object apply(final Object value) {
        return value;
      }
    };
  }
}
