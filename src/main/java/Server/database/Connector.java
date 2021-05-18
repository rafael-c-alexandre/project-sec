package Server.database;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Connector {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/?useLegacyDatetimeCode=false&serverTimezone=UTC";


    public Connection connection;

    public Connector(String user, String password, String serverId) throws SQLException {
        String NEW_DB_URL = "jdbc:mysql://localhost:3306/SecDB_" +serverId +"?useLegacyDatetimeCode=false&serverTimezone=UTC";

        //CREATE NEW DB ASSOCIATED WITH THIS SERVER
        this.connection = DriverManager.getConnection(DB_URL, user, password);
        this.connection.setAutoCommit(false);
        Statement s=this.connection.createStatement();
        s.executeUpdate("DROP DATABASE IF EXISTS SecDB_" + serverId);
        int result = s.executeUpdate("CREATE DATABASE SecDB_" + serverId);

        this.connection.close();

        //CONNECT TO NEW DB
        this.connection = DriverManager.getConnection(NEW_DB_URL, user, password);
        this.connection.setAutoCommit(false);

        s = this.connection.createStatement();
        result = s.executeUpdate("CREATE TABLE IF NOT EXISTS UserReports (\n" +
                "     server_id VARCHAR(255),\n" +
                "     username VARCHAR(255),\n" +
                "     epoch INT,\n" +
                "     x INT NOT NULL,\n" +
                "     y INT NOT NULL,\n" +
                "     signature VARBINARY(4096),\n" +
                "     isClosed BOOLEAN DEFAULT false,\n" +
                "     PRIMARY KEY (username,epoch)\n" +
                ");");


        s = this.connection.createStatement();
        result = s.executeUpdate("CREATE TABLE IF NOT EXISTS Proofs (\n" +
                "    witness_username VARCHAR(255),\n" +
                "    prover_username VARCHAR(255),\n" +
                "    x INT,\n" +
                "    y INT,\n" +
                "    epoch INT,\n" +
                "    signature VARBINARY(4096),\n" +
                "    proof_bytes VARBINARY(4096),\n" +
                "    witness_session_key_bytes VARBINARY(4096),\n" +
                "    witness_iv VARBINARY(4096),\n" +
                "    FOREIGN KEY (prover_username,epoch) REFERENCES UserReports(username,epoch),\n" +
                "    PRIMARY KEY (witness_username, prover_username, epoch)\n" +
                ");");

        s.executeUpdate("DELETE FROM Proofs");
        s.executeUpdate("DELETE FROM UserReports");
    }

    public Connection getConnection() {
        return connection;
    }
}
