DROP DATABASE SecDB;
CREATE DATABASE SecDB;
USE SecDB;

DROP TABLE IF EXISTS Proofs;
DROP TABLE IF EXISTS UserReports;

CREATE TABLE UserReports (
     server_id VARCHAR(255),
     username VARCHAR(255),
     epoch INT,
     x INT NOT NULL,
     y INT NOT NULL,
     signature VARBINARY(4096),
     isClosed BOOLEAN DEFAULT false,
     PRIMARY KEY (username,epoch)
);

CREATE TABLE Proofs (
    server_id VARCHAR(255),
    witness_username VARCHAR(255),
    prover_username VARCHAR(255),
    x INT,
    y INT,
    epoch INT,
    signature VARBINARY(4096),
    FOREIGN KEY (prover_username,epoch) REFERENCES UserReports(username,epoch),
    PRIMARY KEY (witness_username, prover_username, epoch)
);