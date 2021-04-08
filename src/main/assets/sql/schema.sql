DROP DATABASE SecDB;
CREATE DATABASE SecDB;
USE SecDB;

DROP TABLE IF EXISTS UserReports;

CREATE TABLE UserReports (
    username VARCHAR(50),
    epoch INT,
    x INT NOT NULL,
    y INT NOT NULL,
    PRIMARY KEY (username,epoch)
);