FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:8f73a60b4ca9f98e226d8750e9dbc633a1ad3833c056a5b3816804d6ec4ebae4
ENV TZ="Europe/Oslo"
COPY build/libs/app.jar app.jar
CMD ["-jar","app.jar"]