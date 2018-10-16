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
    <version>0.16.1</version>
    <scope>runtime</scope>
</dependency>
```

Also add dependencies required for LightStep tracer

## Usage
```java
// Instantiate LightStep tracer
JRETracer jreTracer = ... 

// Register the exporter
LightStepTraceExporter.createAndRegister(jreTracer);
```


## License

[Apache 2.0 License](./LICENSE).
