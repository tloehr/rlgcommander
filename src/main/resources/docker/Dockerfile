FROM amazoncorretto:17
EXPOSE 8090
# ARGS
ARG MOSQUITTO=mosquitto.conf
ARG CONFIG=application.yml
ARG COMMANDER=rlgcommander.jar
ARG BASEDIR=/opt/rlgcommander
ARG CONFIGDIR=${BASEDIR}/config
ARG MQTTDIR=${BASEDIR}/mosquitto
# SETUP
RUN mkdir -p ${BASEDIR}
RUN mkdir -p ${CONFIGDIR}
RUN mkdir -p ${MQTTDIR}
ADD ${COMMANDER} ${BASEDIR}
ADD entrypoint.sh ${BASEDIR}
ADD ${MOSQUITTO} ${MQTTDIR}
ADD ${CONFIG} ${CONFIGDIR}
RUN chmod +x ${BASEDIR}/entrypoint.sh
VOLUME ["${CONFIGDIR}","${MQTTDIR}"]
WORKDIR ${BASEDIR}
ENTRYPOINT ["./entrypoint.sh"]
CMD ["java","-jar","rlgcommander.jar"]
