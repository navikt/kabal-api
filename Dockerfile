FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:ce6adb53a310a46b83087deeb64dd713f210d2b1d26ee6952c3fc4c7eb4fe801
ENV TZ="Europe/Oslo"
COPY build/libs/app.jar app.jar
CMD ["-jar","app.jar"]