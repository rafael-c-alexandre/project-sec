# SEC project - Highly Dependable Location Tracker
SEC course project: final stage  - MEIC-A, Group 8:
- Afonso Paredes, ist189401
- Rafael Poças, ist189527
- Rafael Alexandre, ist189528

## Setup

#### Requirements

The requirements needed to run the program are:
- Java 15
- Maven
- MySQL 


## Usage

In order to run the program, at least 3 terminal windows must be open:
- 1+ server(s)
- 1+ client(s)
- 1 Healthcare Authority (HA)

#### 0. Installing and compiling project 

1. Have all the previous required tools/software installed and ready for usage.

2. Have MySQL server started. 

3. Open `<root-project-directory>/src/main/assets/scripts` and run script **init.sh** to install and compile the whole project.

#### 1. Server initialization

On `<root-project-directory>`, run 

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> <nByzantineUsers> <keyStorePassword> <byzantineMode>" 
```

- **dbUser**: created user for using the MySQL database
- **dbPass**: password for created user in MySQL database
- **nByzantineUsers**: number of byzantine users in the whole system
- **keyStorePassword**: the private key store password
- **byzantineMode**: The mode that specifies which attack should be performed by the byzatine server
    - Mode `1`: Byzantine server sends wrong report when user request a location report 
    - Mode `2`: Byzantine server sends 0 reports when user request a location report 
    - Mode `3`: Byzantine server responds with fake users at location to the HA
    - Mode `4`: Byzantine server responds with no users at location to the HA
    - Mode `5`: Byzantine server always responds that the report is proven when a proof is submited
    - Mode `6`: Byzantine server sends 0 proofs to request my proofs by client
    - Mode `7`: Byzantine server sends fake proofs to request my proofs by client
    - Default: Normal server behaviour

For this program, it is required to enter the passwords of the key stores that store all the keys/certificates used during communication.
For every one of those stores, the password is set by default to `123456`. However, we **strongly recommend** that the user changes that password to a stronger one, which can be done by issuing the command `keytool -storepasswd -keystore <root-project-directory>/src/assets/keyStores/<key-store-name>`.

#### 2. Client initialization

On `<root-project-directory>`, run 

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="<username> <gridFilePath> <keyStorePassword> <nByzantineUsers> <nByzantineServers> <byzantineMode> [<scriptFilePath>]" 
```

- **username**: username for the client
- **gridFilePath**: path to the file containing the locations' grid
- **keyStorePassword**: the private key store password
- **scriptFilePath**: path to the file containing a script of user's commands 
- **nByzantineUsers**: number of byzantine users in the whole systemcommands 
- **nByzantineServers**: number of byzantine servers in the whole system
- **byzantineMode**: The mode that specifies which attack should be performed by the byzatine user
    - Mode `1`: generates wrong location in report when requesting to server
    - Mode `2`: wrong epoch when creating proofs to other prover
    - Mode `3`: sends proof request to every witness available, at any range
    - Mode `4`: gives proof to every prover who requests, use prover location as own location in the proof to make sure the server accepts it
    - Mode `5`: witness captures other user's proof and replays it
    - Mode `0`: Regular user


For this program, it is required to enter the passwords of the key stores that store all the keys/certificates used during communication.
For every one of those stores, the password is set by default to `123456`. However, we **strongly recommend** that the user changes that password to a stronger one, which can be done by issuing the command `keytool -storepasswd -keystore <root-project-directory>/src/assets/keyStores/<key-store-name>`.

#### 3. Healthcare Authority initialization

On `<root-project-directory>`, run 

```bash
mvn exec:java -Dexec.mainClass="HA.HAClient" -Dexec.args="<keyStorePassword> <numberOfByzantineUsers> <numberOfByzantineServers> [<scriptFilePath>]"
```
- **keyStorePassword**: the private key store password
- **nByzantineUsers**: number of byzantine users in the whole systemcommands 
- **nByzantineServers**: number of byzantine servers in the whole system
- **scriptFilePath**: path to the file containing a script of user's commands 

For this program, it is required to enter the passwords of the key stores that store all the keys/certificates used during communication.
For every one of those stores, the password is set by default to `123456`. However, we **strongly recommend** that the user changes that password to a stronger one, which can be done by issuing the command `keytool -storepasswd -keystore <root-project-directory>/src/assets/keyStores/<key-store-name>`.


## Demos and tests

Before every test case, run 
```bash 
mysql -p < schema.sql
``` 
on `<root-project-directory>/src/main/assets/sql_scripts>` to create a clean database.

### Case 1. Correct Behaviour

For this test case, start 1 server with 0 byzantine users. 3 regular users with usernames, user1 user2 and user3 using the grid file **grid1.txt** and using the command file **correct_behaviour.txt**. After the users stabilize by proving each others' locations, start an HA client using the command file **correct_behaviour_ha.txt**.


On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 0 123456 0" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid1.txt 123456 0 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid1.txt 123456 0 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```


On terminal 4:


```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid1.txt 123456 0 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 5:

```bash
mvn exec:java -Dexec.mainClass="HA.HAClient" -Dexec.args="123456 src/main/assets/command_files/correct_behaviour_ha.txt"
```



This test reflects the correct behaviour of the program. Users prove their location correctly by issuing broadcast proof request to other witnesses and submitting them to the server. Plus, clients request their locations for a specific epoch and the HA request all the clients in some position at a given epoch.


### Case 2. Byzantine user tries to generate a valid report using a fake location

For this test case, start 1 server, 1 byzantine user with username byzantine_user1 in mode 1, 3 regular users with usernames user1 and user2 , all of them using the grid file **grid2.txt**. 


On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 1 123456 0" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid2.txt 123456 1 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid2.txt 123456 1 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="byzantine_user1 rc/main/assets/grid_examples/grid2.txt 123456 1 0 1 src/main/assets/command_files/correct_behaviour.txt" 
```


A byzantine prover sends a legit proof request to the witnesses but sends an incorrect location report to the server, hoping the server accepts those proofs as valid for the aforementioned report. This will make the server reject the proofs based on location disparity.



### Case 3. Byzantine user generating and sending a wrong proof to a correct user (altered epoch)

For this test case, start 1 server, 1 byzantine user with username byzantine_user1 in mode 2, 2 regular users with usernames user1 and user2,  all of them using the grid file **grid3.txt**. 


On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 1 123456 0" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid3.txt 123456 1 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid3.txt 123456 1 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```


On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="byzantine_user1 rc/main/assets/grid_examples/grid3.txt 123456 1 0 2 src/main/assets/command_files/correct_behaviour.txt" 
```


In this test case, a byzantine user sends wrong location proofs to correct users that made a request, changing the epoch of the proof. It is expected that the server rejects the invalid proof.




### Case 4. Byzantine users collaborate to fake one byzantine user's location


For this test case, start 1 server, 2 byzantine users with usernames byzantine_user1 in mode 3 and byzantine_user2 in mode 4, 3 regular users with usernames user1 user2 and user3, all of them using the grid file **grid4.txt**. 


On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 2 123456 0" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid4.txt 123456 2 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid4.txt 123456 2 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 4:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid4.txt 123456 2 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 5:


```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid4.txt 123456 2 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="byzantine_user1 rc/main/assets/grid_examples/grid4.txt 123456 2 0 3 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 7:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="byzantine_user2 rc/main/assets/grid_examples/grid4.txt 123456 2 0 4 src/main/assets/command_files/correct_behaviour.txt" 
```


In this test, there are 2 byzantine users collaborating. One is trying to prove himself to be in a fake location, the other is colluding with the byzantine prover, sending fake proofs that match the fake proof request of the first one. However, because they will not be able to send f (in this case 2) valid proofs to the server, the report will not be accepted.



### Case 5. Byzantine user tries to replay a correct user's proof


For this test case, start 1 server, 1 byzantine user with username byzantine_user1 in mode 5, 2 regular users with usernames user1 and user2, all of them using the grid file **grid5.txt**. 



On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 2 123456 0" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid5.txt 123456 2 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid5.txt 123456 2 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```


On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="byzantine_user1 rc/main/assets/grid_examples/grid4.txt 123456 2 0 5 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 5:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="byzantine_user2 rc/main/assets/grid_examples/grid4.txt 123456 2 0 5 src/main/assets/command_files/correct_behaviour.txt" 
```



In this test, the byzantine user captures a valid proof from a correct witness to a correct prover and replays it, also sending it to the prover. Because the proof comes, supposedly, from a user that has already submitted a proof for that report, the server will reject it.


#### Case 6. Server crashes and recovers its states after rebooting

For this test case, start 1 server with 0 byzantine users. 3 regular users with usernames, user1 user2 and user3 using the grid file **grid6.txt** and using the command file **correct_behaviour.txt**. After the users stabilize by proving each others' locations, start an HA client using the command file **correct_behaviour_ha.txt**.

On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 2 123456 0" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid6.txt 123456 0 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid6.txt 123456 0 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 4:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid6.txt 123456 0 0 0 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 5:

```bash
mvn exec:java -Dexec.mainClass="HA.HAClient" -Dexec.args="123456 correct_behaviour_ha.txt"
```


After submitting all the reports, **crash the server**.
Then reboot it again:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 0 123456 0" 
```


When the central server goes down, its in-memmory data is lost forever. However, all data is also stored in an external database, allowing the server to reboot and recover its previous up-to-date state.


#### Case 1 - stage 2. Byzantine server sends wrong report when user request a location report

For this test case, start 3 correct servers and 1 byzantine server in mode 1 with 0 byzantine users. 3 regular users with usernames, user1 user2 and user3 using the grid file **grid1.txt** and using the command file **correct_behaviour.txt**.

On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server 1"
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server2 0"
```

On terminal 3:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server3 0"
```

On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server4 0"
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

On terminal 7:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

Note that when the users request a location proof there will be displayed an error message stating the received report was invalid, that was the report from the byzantine correclty identified as faked. The user should also receive good reports from the correct servers and display the result.

#### Case 2 - stage 2. Byzantine server sends 0 reports when user request a location report 

For this test case, start 3 correct servers and 1 byzantine server in mode 2 with 0 byzantine users. 3 regular users with usernames, user1 user2 and user3 using the grid file **grid1.txt** and using the command file **correct_behaviour.txt**.

On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server 2"
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server2 0"
```

On terminal 3:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server3 0"
```

On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server4 0"
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

On terminal 7:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```


The user should receive good reports from the correct servers and display the result even though one byzantine user returned an error.

#### Case 3 - stage 2. Byzantine server responds with fake users at location to the HA

For this test case, start 3 correct servers and 1 byzantine server in mode 3 with 0 byzantine users. 3 regular users with usernames, user1 user2 and user3 using the grid file **grid1.txt** and using the command file **correct_behaviour.txt**.

On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server 3"
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server2 0"
```

On terminal 3:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server3 0"
```

On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server4 0"
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

On terminal 7:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

The HA should return the correct answer from the quorum of responses from the correct servers even though one response is fake.

#### Case 4 - stage 2. Byzantine server responds with no users at location to the HA

For this test case, start 3 correct servers and 1 byzantine server in mode 4 with 0 byzantine users. 3 regular users with usernames, user1 user2 and user3 using the grid file **grid1.txt** and using the command file **correct_behaviour.txt**.

On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server 4"
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server2 0"
```

On terminal 3:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server3 0"
```

On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server4 0"
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

On terminal 7:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

The HA should return the correct answer from the quorum of responses from the correct servers even though an error will be returned from the byzantine server

#### Case 5 - stage 2. Byzantine server always responds that the report is proven when a proof is submited

For this test case, start 3 correct servers and 1 byzantine server in mode 5 with 0 byzantine users. 3 regular users with usernames, user1 user2 and user3 using the grid file **grid1.txt** and using the command file **correct_behaviour.txt**. After the users stabilize by proving each others' locations, start an HA client using the command file **correct_behaviour_ha.txt**.

On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server 5"
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server2 0"
```

On terminal 3:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server3 0"
```

On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server4 0"
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

On terminal 7:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

This should return the correct proofs because the client merges all the correct proofs received and the client waits for a majority of servers, which means at least one response was from a correct server.

#### Case 6 - stage 2. Byzantine server sends 0 proofs to request my proofs by client

For this test case, start 3 correct servers and 1 byzantine server in mode 6 with 0 byzantine users. 3 regular users with usernames, user1 user2 and user3 using the grid file **grid1.txt** and using the command file **correct_behaviour.txt**. After the users stabilize by proving each others' locations, start an HA client using the command file **correct_behaviour_ha.txt**.


On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server 6"
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server2 0"
```

On terminal 3:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server3 0"
```

On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="root 123 1 123456 server4 0"
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

On terminal 7:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid7-stage2.txt 123456 1 1 0"
```

This should return the correct proofs because the client merges all the correct proofs received and the client waits for a majority of servers, which means at least one response was from a correct server.
 


#### Note
If command `konsole` is installed, it is also possible to run the test scripts `test<case_number>-stage<x>.sh` to perform the test. 