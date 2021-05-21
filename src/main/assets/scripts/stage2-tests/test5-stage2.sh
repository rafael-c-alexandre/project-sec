#! /bin/bash

cd ../../../..
mysql -u root -p123 < src/main/assets/sql/schema.sql
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Server.Server\" -Dexec.args=\"root 123 1 123456 server 5\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Server.Server\" -Dexec.args=\"root 123 1 123456 server2 0\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Server.Server\" -Dexec.args=\"root 123 1 123456 server3 0\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Server.Server\" -Dexec.args=\"root 123 1 123456 server4 0\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user1 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user2 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0\"") &

