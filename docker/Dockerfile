FROM hseeberger/scala-sbt:eclipse-temurin-11.0.14.1_1.6.2_2.13.8 as build

WORKDIR /app

# Constrain sbt memory in the builder to reduce OOM risk in Docker Desktop defaults.
ENV SBT_OPTS="-Xmx1G -Xms512m -Xss2M -Dsbt.ci=true -Dsbt.task.forcegc=true"

COPY . .

RUN sbt -batch \
	"set ThisBuild / Compile / doc / sources := Seq.empty" \
	"set ThisBuild / Test / doc / sources := Seq.empty" \
	update compile stage
RUN chmod a+rx /app/target/universal/stage/bin/*

FROM openjdk:11-jre-slim

WORKDIR /app

COPY --from=build /app/target/universal/stage /app

COPY --chmod=755 docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
