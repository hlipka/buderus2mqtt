# Buderus2MQTT
## Overview
This project is intended to push service data (e.g. current water temperature) from a Buderus gateway 8KM50 / KM100 / KM200) to a MQTT server.
Its heavily based on the KM200 gateway binding from OpenHAB 1.x, but I removed all the OpenHAB-specific code.
I'm using it to push regular temperature and energy consumption data into an InfluxDB system, to visualize it via Grafana.
## Features
- can retrieve list of available service from the Buderus gateway
- handles string-based and number-based values
- can push to multiple MQTT servers
  - unauthorized or username ( / password)
  - SSL or plaintext connection
- format of MQTT message can be defined freely
- topic for each service can be defined
## Usage
Call via ''bin/buderus2mqtt.sh CONFIGFILE''. ''-h'' shows the usage help.
## Configuration
See the provided config.yml.example. Most notable:
Both the MQTT topic and the MQTT message written can be defined as needed. For both, value replacement can be done. Available replacements:
- {service} : the service name as defined
- {value_i} : the retrieved as INT
- {value_d} : the retrieved as double
- {value_s} : the retrieved value, as-is
- {value_b} : the retrieved value as boolean ('true' as true, or ''0'' as false)
To configure the gateway connection, you need some parameters, Look at the OpenHAB binding configuration page for more information how to retrieve them.
