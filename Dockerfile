FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:3bf34fd949124081777c356147320f13a02b1d935dc7ccd700cd83c0f0f43488
ENV TZ="Europe/Oslo"
COPY build/libs/app.jar app.jar
# Custom OTEL Java agent extension that rewrites spurious ERROR statuses on the
# SSE endpoint span (caused by client disconnects) to OK.
# Loaded by the OTEL auto-instrumentation agent via OTEL_JAVAAGENT_EXTENSIONS.
COPY otel-extension/build/libs/otel-extension.jar otel-extension.jar
CMD ["-jar","app.jar"]