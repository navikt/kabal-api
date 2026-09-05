FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:bec6cea1c5412108a06228ef05e212f91f1f98425ee745cddcdc4aeb067e3723
ENV TZ="Europe/Oslo"
COPY build/libs/app.jar app.jar
# Custom OTEL Java agent extension that rewrites spurious ERROR statuses on the
# SSE endpoint span (caused by client disconnects) to OK.
# Loaded by the OTEL auto-instrumentation agent via OTEL_JAVAAGENT_EXTENSIONS.
COPY otel-extension/build/libs/otel-extension.jar otel-extension.jar
CMD ["-jar","app.jar"]