#! /bin/bash

cd ../../../../..
mysql -u root -p123 < src/main/assets/sql/schema.sql
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Server.Server\" -Dexec.args=\"afonso password 2 123456 0\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user1 src/main/assets/grid_examples/grid5.txt 123456 1 1 0\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user2 src/main/assets/grid_examples/grid5.txt 123456 1 1 0\"") &
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user3 src/main/assets/grid_examples/grid5.txt 123456 1 1 5\"")&
konsole -e /bin/bash  --rcfile <(echo "mvn exec:java -Dexec.mainClass=\"Client.Client\" -Dexec.args=\"user4 src/main/assets/grid_examples/grid5.txt 123456 1 1 0\"")&