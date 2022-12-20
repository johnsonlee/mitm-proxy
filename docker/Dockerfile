FROM node:19 AS ui
WORKDIR /app
ADD ui .
RUN npm install cnpm -g --registry=https://registry.npmmirror.com && cnpm install && npm run build

FROM johnsonlee/gradle-7.4:springboot-2.7.5 AS builder
WORKDIR /app
ADD . .
COPY --from=ui /app/dist /app/server/src/main/resources/public
RUN ./gradlew bootJar --no-daemon

FROM johnsonlee/java15:latest
WORKDIR /app
COPY --from=builder /app/server/build/libs/mitmproxy.jar mitmproxy.jar
COPY --from=builder /app/envsetup.sh envsetup.sh
CMD ["/bin/bash", "-c", "source envsetup.sh && exec java $JAVA_OPTS -Dspring.profiles.active=prod -jar mitmproxy.jar"]
