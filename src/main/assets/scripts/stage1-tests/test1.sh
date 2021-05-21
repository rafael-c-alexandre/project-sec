#! /bin/bash

cd ../../../..
mysql -u root -p123 < src/main/assets/sql/schema.sql
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Server.Server\" -Dexec.args=\"afonso password 2 123456\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user1 src/main/assets/grid_examples/grid1.txt 123456 1 1 0 src/main/assets/command_files/correct_behaviour.txt\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user2 src/main/assets/grid_examples/grid1.txt 123456 1 1 0 src/main/assets/command_files/correct_behaviour.txt\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user3 src/main/assets/grid_examples/grid1.txt 123456 1 1 0 src/main/assets/command_files/correct_behaviour.txt\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"HA.HAClient\" -Dexec.args=\"123456 1 1 src/main/assets/command_files/correct_behaviour_ha.txt\"")

