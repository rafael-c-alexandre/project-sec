#! /bin/bash

mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid1.txt 123456 1 1 0 src/main/assets/command_files/correct_behaviour.txt"