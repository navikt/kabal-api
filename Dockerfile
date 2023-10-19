FROM gcr.io/distroless/java17-debian11:latest
ENV TZ="Europe/Oslo"

COPY build/libs/*.jar ./
