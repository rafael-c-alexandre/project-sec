DROP DATABASE SecDB;
CREATE DATABASE SecDB;
USE SecDB;

DROP TABLE IF EXISTS Proofs;
DROP TABLE IF EXISTS UserReports;

CREATE TABLE UserReports (
     username VARCHAR(255),
     epoch INT,
     x INT NOT NULL,
     y INT NOT NULL,
     isClosed BOOLEAN,
     PRIMARY KEY (username,epoch)
);

CREATE TABLE Proofs (
    witness_username VARCHAR(255),
    prover_username VARCHAR(255),
    x INT,
    y INT,
    epoch INT,
    signature BINARY DEFAULT false,
    FOREIGN KEY (prover_username,epoch) REFERENCES UserReports(username,epoch),
    PRIMARY KEY (witness_username, prover_username, epoch)
);