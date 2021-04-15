# SEC project - Highly Dependable Location Tracker
SEC course project: stage 1 - MEIC-A, Group 8:
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
- 1 server
- 1+ client(s)
- 1 Healthcare Authority (HA)

#### 0. Installing and compiling project 

1. Have all the previous required tools/software installed and ready for usage.

2. Have MySQL server started. Afterwards, Open `<root-project-directory>/src/main/assets/sql_scripts>` and run `mysql -p < schema.sql`, which will create and initialize the `SecDB` databse.

3. Open `<root-project-directory>/src/main/assets/scripts` and run script **init.sh** to install and compile the whole project.

#### 1. Server initialization

On `<root-project-directory>`, run 

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> <nByzantineUsers> <keyStorePassword>" 
```

- **dbUser**: created user for using the MySQL database
- **dbPass**: password for created user in MySQL database
- **nByzantineUsers**: number of byzantine users in the whole system
- **keyStorePassword**: the private key store password

For this program, it is required to enter the passwords of the key stores that store all the keys/certificates used during communication.
For every one of those stores, the password is set by default to `123456`. However, we **strongly recommend** that the user changes that password to a stronger one, which can be done by issuing the command `keytool -storepasswd -keystore <root-project-directory>/src/assets/keyStores/<key-store-name>`.

#### 2. Client initialization

On `<root-project-directory>`, run 

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="<username> <gridFilePath> <keyStorePassword> [<scriptFilePath>]" 
```

- **username**: username for the client
- **gridFilePath**: path to the file containing the locations' grid
- **keyStorePassword**: the private key store password
- **scriptFilePath**: path to the file containing a script of user's commands 


For this program, it is required to enter the passwords of the key stores that store all the keys/certificates used during communication.
For every one of those stores, the password is set by default to `123456`. However, we **strongly recommend** that the user changes that password to a stronger one, which can be done by issuing the command `keytool -storepasswd -keystore <root-project-directory>/src/assets/keyStores/<key-store-name>`.

#### 3. Healthcare Authority initialization

On `<root-project-directory>`, run 

```bash
mvn exec:java -Dexec.mainClass="HA.HAClient" -Dexec.args="<keyStorePassword> [<scriptFilePath>]"
```
- **keyStorePassword**: the private key store password
- **scriptFilePath**: path to the file containing a script of user's commands 

For this program, it is required to enter the passwords of the key stores that store all the keys/certificates used during communication.
For every one of those stores, the password is set by default to `123456`. However, we **strongly recommend** that the user changes that password to a stronger one, which can be done by issuing the command `keytool -storepasswd -keystore <root-project-directory>/src/assets/keyStores/<key-store-name>`.

#### 4. Byzantine Client initialization

On `<root-project-directory>`, run 

```bash
mvn exec:java -Dexec.mainClass="ByzantineClient.ByzantineClient" -Dexec.args="<username> <gridFilePath> <keyStorePassword> <byzantineMode> [<scriptFilePath>] " 
```

- **username**: username for the client
- **gridFilePath**: path to the file containing the locations' grid
- **keyStorePassword**: the private key store password
- **byzantineMode**: The mode that specifies which attack should be performed by the byzatine user
    - Mode `1`: generates wrong location in report when requesting to server
    - Mode `2`: wrong epoch when creating proofs to other prover
    - Mode `3`: sends proof request to every witness available, at any range
    - Mode `4`: gives proof to every prover who requests, use prover location as own location in the proof to make sure the server accepts it
    - Mode `5`: witness captures other user's proof and replays it
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
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 0 123456" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid1.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid1.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```


On terminal 4:


```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid1.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```
After submitting all the reports, **launch an HA client**.

On terminal 5:

```bash
mvn exec:java -Dexec.mainClass="HA.HAClient" -Dexec.args="123456 correct_behaviour_ha.txt"
```



This test reflects the correct behaviour of the program. Users prove their location correctly by issuing broadcast proof request to other witnesses and submitting them to the server. Plus, clients request their locations for a specific epoch and the HA request all the clients in some position at a given epoch.


### Case 2. Byzantine user tries to generate a valid report using a fake location

For this test case, start 1 server, 1 byzantine user with username byzantine_user1 in mode 1, 3 regular users with usernames user1 user2 and user3, all of them using the grid file **grid2.txt**. 


On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 0 123456" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid2.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid2.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 4:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid2.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="ByzantineClient.ByzantineClient" -Dexec.args="byzantine_user1 rc/main/assets/grid_examples/grid2.txt 123456 1" 
```


A byzantine prover sends a legit proof request to the witnesses but sends an incorrect location report to the server, hoping the server accepts those proofs as valid for the aforementioned report. This will make the server reject the proofs based on location disparity.



### Case 3. Byzantine user generating and sending a wrong proof to a correct user (altered epoch)

For this test case, start 1 server, 1 byzantine user with username byzantine_user1 in mode 2, 3 regular users with usernames user1 user2 and user3, all of them using the grid file **grid3.txt**. 


On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 0 123456" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid3.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid3.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 4:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid3.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 5:


```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid3.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="ByzantineClient.ByzantineClient" -Dexec.args="byzantine_user1 rc/main/assets/grid_examples/grid3.txt 123456 2" 
```




In this test case, a byzantine user sends wrong location proofs to correct users that made a request, changing the epoch of the proof. It is expected that the server rejects the invalid proof.




### Case 4. Byzantine users collaborate to fake one byzantine user's location


For this test case, start 1 server, 2 byzantine users with usernames byzantine_user1 in mode 3 and byzantine_user2 in mode 4, 3 regular users with usernames user1 user2 and user3, all of them using the grid file **grid4.txt**. 


On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 0 123456" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid4.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid4.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 4:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid4.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 5:


```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid4.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="ByzantineClient.ByzantineClient" -Dexec.args="byzantine_user1 rc/main/assets/grid_examples/grid4.txt 123456 3" 
```

On terminal 7:
```bash
mvn exec:java -Dexec.mainClass="ByzantineClient.ByzantineClient" -Dexec.args="byzantine_user2 rc/main/assets/grid_examples/grid4.txt 123456 4" 
```



In this test, there are 3 byzantine users collaborating. One is trying to proove himself to be in a fake location, the other two are sending fake proofs that match the fake proof request of the first one. 



### Case 5. Byzantine user tries to replay a correct user's proof


For this test case, start 1 server, 1 byzantine user with username byzantine_user1 in mode 5, 3 regular users with usernames user1 user2 and user3, all of them using the grid file **grid5.txt**. 



On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 0 123456" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid5.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid5.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 4:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid5.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 5:


```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid5.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 6:
```bash
mvn exec:java -Dexec.mainClass="ByzantineClient.ByzantineClient" -Dexec.args="byzantine_user1 rc/main/assets/grid_examples/grid4.txt 123456 5" 
```


In this test, the byzantine users captures a valid proof from a correct witness to a correct prover and replays it, also sending it to the prover. Because the proof comes, supposedly, from a user that has already submitted a proof for that report, the server will reject it.


#### Case 6. Server crashes and recovers its states after rebooting

For this test case, start 1 server with 0 byzantine users. 3 regular users with usernames, user1 user2 and user3 using the grid file **grid6.txt** and using the command file **correct_behaviour.txt**. After the users stabilize by proving each others' locations, start an HA client using the command file **correct_behaviour_ha.txt**.

On terminal 1:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 0 123456" 
```

On terminal 2:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 src/main/assets/grid_examples/grid6.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 3:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 src/main/assets/grid_examples/grid6.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 4:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid6.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

On terminal 5:

```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user3 src/main/assets/grid_examples/grid6.txt 123456 src/main/assets/command_files/correct_behaviour.txt" 
```

After submitting all the reports, **crash the server**.
Then reboot it again:

```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 0 123456" 
```

and launch an HA client, on terminal 6:

After submitting all the reports, **launch an HA client**.

On terminal 5:

```bash
mvn exec:java -Dexec.mainClass="HA.HAClient" -Dexec.args="123456 correct_behaviour_ha.txt"
```



When the central server goes down, its in-memmory data is lost forever. However, all data is also stored in an external database, allowing the server to reboot and recover its previous up-to-date state.


