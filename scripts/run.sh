#!/bin/bash 

NAME=${NAME:-case-creator}

JAR=$(find . -name ${NAME}*.jar|head -1)

exec java ${JAVA_OPTS} -Dcom.sun.management.jmxremote.local.only=false -Djava.security.egd=file:/dev/./urandom -jar "${JAR}" 
