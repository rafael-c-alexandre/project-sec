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
- **scriptFilePath**: path to the file containing a script of user's commands 


For this program, it is required to enter the passwords of the key stores that store all the keys/certificates used during communication.
For every one of those stores, the password is set by default to `123456`. However, we **strongly recommend** that the user changes that password to a stronger one, which can be done by issuing the command `keytool -storepasswd -keystore <root-project-directory>/src/assets/keyStores/<key-store-name>`.


## Demos and tests

#### Case 1. Correct Behaviour

This test reflects the correct behaviour of the program. Users prove their location correctly by issuing broadcast proof request to other witnesses and submitting them to the server. Plus, clients request their locations for a specific epoch and the HA request all the clients in some position at a given epoch.

For this test case start 1 server, 3 clients and the HA client. Below is how to run this test using the **correct_behaviour.txt** and **correct_behaviour_ha.txt** command files as input.

On `<root-project-directory>`, run 

On terminal 1:
```bash
mvn exec:java -Dexec.mainClass="Server.Server" -Dexec.args="<dbUser> <dbPass> 2 <keyStorePassword>" 
````
On terminal 2:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user1 grid1.txt <keyStorePassword> correct_behaviour.txt" 
````
On terminal 3:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 grid1.txt <keyStorePassword> correct_behaviour.txt" 
````
On terminal 4:
```bash
mvn exec:java -Dexec.mainClass="Client.Client" -Dexec.args="user2 grid1.txt <keyStorePassword> correct_behaviour.txt"
````
On terminal 5:
```bash
mvn exec:java -Dexec.mainClass="HA.HAClient" -Dexec.args="<keyStorePassword> correct_behaviour_ha.txt"
```


#### Case 2. Byzantine user generating and sending a wrong proof to a correct user



#### Case 3. Byzantine user tries to generate a valid report using a fake location

A byzantine prover sends a legit proof request to the witnesses but sends an incorrect location report to the server, hoping the server accepts those proofs as valid for the aforementioned report. This will make the server reject the proofs based on location disparity.

For this test case, start 1 server, 3 clients and the HA client.

#### Case 4. Bad guy 3



#### Case 5. Server maintains correctness after crashing


