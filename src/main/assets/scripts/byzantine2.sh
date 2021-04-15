#! /bin/bash

mvn exec:java -Dexec.mainClass="ByzantineClient.ByzantineClient" -Dexec.args="byzantine_user2 src/main/assets/grid_examples/grid1.txt 123456"