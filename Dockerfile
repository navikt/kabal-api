FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:7e194fab08c2dd1353019b631fe982c8bfb9b5ddcf391e77a121a1fa944653c3
ENV TZ="Europe/Oslo"
COPY build/libs/app.jar app.jar
# Custom OTEL Java agent extension that rewrites spurious ERROR statuses on the
# SSE endpoint span (caused by client disconnects) to OK.
# Loaded by the OTEL auto-instrumentation agent via OTEL_JAVAAGENT_EXTENSIONS.
COPY otel-extension/build/libs/otel-extension.jar otel-extension.jar
CMD ["-jar","app.jar"]