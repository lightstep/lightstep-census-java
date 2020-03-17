[![Circle CI](https://circleci.com/gh/lightstep/lightstep-census-java.svg?style=shield)](https://circleci.com/gh/lightstep/lightstep-census-java) [![Released Version][maven-img]][maven] [![Apache-2.0 license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# LightStep OpenCensus Trace Exporter

The LightStep OpenCensus Trace Exporter is a trace exporter that exports data to LightStep.

## Installation

pom.xml
```xml
<dependency>
    <groupId>com.lightstep.opencensus</groupId>
    <artifactId>lightstep-opencensus-exporter</artifactId>
    <version>VERSION</version>
</dependency>

<dependency>
    <groupId>io.opencensus</groupId>
    <artifactId>opencensus-impl</artifactId>
    <version>0.19.0</version>
    <scope>runtime</scope>
</dependency>
```

Also add dependencies required for LightStep tracer

## Usage

### Initialization
```java
// Instantiate LightStep tracer
JRETracer jreTracer = ... 

// Register the exporter
LightStepTraceExporter.createAndRegister(jreTracer);

// Optionally configure 100% sample rate, otherwise, few traces will be sampled
TraceConfig traceConfig = Tracing.getTraceConfig();
    traceConfig.updateActiveTraceParams(
        traceConfig.getActiveTraceParams()
            .toBuilder()
            .setSampler(Samplers.probabilitySampler(1))
            .build());
```

### Shutdown
```java
// To shutdown the exporter
Tracing.getExportComponent().shutdown();

// Close the tracer, so that it'll flush queued traces
jreTracer.close();
```

### Example

There is an [example](./example) which demonstrate basic usage of the exporter. 

## License

[Apache 2.0 License](./LICENSE).

[maven-img]: https://img.shields.io/maven-central/v/com.lightstep.opencensus/lightstep-opencensus-exporter.svg
[maven]: http://search.maven.org/#search%7Cga%7C1%7Clightstep-opencensus-exporter  
