#!/bin/bash
cp --no-clobber /opt/rlgcommander/application.yml /opt/rlgcommander/config
cp --no-clobber /opt/rlgcommander/mosquitto.conf /opt/rlgcommander/mosquitto
exec "$@"