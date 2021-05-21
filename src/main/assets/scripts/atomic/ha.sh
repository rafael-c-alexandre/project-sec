#! /bin/bash

mvn exec:java -Dexec.mainClass="HA.HAClient" -Dexec.args="123456 1 1 src/main/assets/command_files/correct_behaviour_ha.txt"