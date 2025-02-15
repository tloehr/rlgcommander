#!/bin/bash

clear

if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

#
## check if already installed
INSTALLED_ALREADY=/var/lib/docker/_rlgs/docker-compose.yml
if test -f "$INSTALLED_ALREADY";
    then
        echo "The commander is installed already."
        echo "Delete ${INSTALLED_ALREADY} if you want to do it again."
        exit
fi


UNAME=`uname -s`
OS=`cat /etc/os-release`

if [[ $UNAME != "Linux" ]]
    then
        echo "The RLGCommander runs under Linux only. Sorry"
        exit 1
fi

DOCKER=`systemctl show --property ActiveState docker`

if [[ $DOCKER != "ActiveState=active" ]]
    then
        echo "We need a working docker container environment. Sorry"
        exit 1
fi

if ! command -v docker-compose 2>&1 >/dev/null
    then
        echo "We need docker-compose - please install it."
        exit 1
fi

curl --create-dirs --output /var/lib/docker/_rlgs/docker-compose.yml https://raw.githubusercontent.com/tloehr/rlgcommander/refs/heads/main/src/main/resources/docker-comopose.yml
curl --create-dirs --output /var/lib/docker/_rlgs/config/application-reset-admin-pw.yml https://raw.githubusercontent.com/tloehr/rlgcommander/refs/heads/main/src/main/resources/application-reset-admin-pw.yml
curl --create-dirs --output /var/lib/docker/_rlgs/mosquitto/mosquitto.conf https://raw.githubusercontent.com/tloehr/rlgcommander/refs/heads/main/src/main/resources/mosquitto.conf

docker-compose -f /var/lib/docker/_rlgs/docker-compose.yml up -d
