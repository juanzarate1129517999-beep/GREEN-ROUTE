# Green Route en un contenedor: compila el motor Java y sirve el sitio web + API REST en el puerto $PORT.
# Lo usan Render, Railway, Koyeb, Fly.io o cualquier servicio que acepte Docker.

FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /app
COPY greenroute-java/src greenroute-java/src
RUN mkdir -p greenroute-java/out \
 && javac -encoding UTF-8 -d greenroute-java/out $(find greenroute-java/src -name '*.java')

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilacion /app/greenroute-java/out greenroute-java/out
COPY greenroute-java/datos greenroute-java/datos
COPY web web
WORKDIR /app/greenroute-java
ENV PORT=8080
EXPOSE 8080
# El motor usa unos 200 MB; con 75 % de la RAM del contenedor cabe en los planes gratuitos de 512 MB.
CMD java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -XX:MaxRAMPercentage=75 -XX:+UseSerialGC \
    -cp out co.edu.greenroute.App --puerto=${PORT}
