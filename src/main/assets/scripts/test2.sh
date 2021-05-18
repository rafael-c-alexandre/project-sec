#! /bin/bash

cd ../../../..
mysql -u root -p123 < src/main/assets/sql/schema.sql
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Server.Server\" -Dexec.args=\"afonso password 1 123456\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user1 src/main/assets/grid_examples/grid2.txt 123456 1 1 \"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user2 src/main/assets/grid_examples/grid2.txt 123456 1 1 \"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"ByzantineClient.ByzantineClient\" -Dexec.args=\"byzantine_user1 src/main/assets/grid_examples/grid2.txt 123456 1 1 1\"")

