package Server.database;

import Server.Proof;
import Server.UserReport;
import util.Coords;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserReportsRepository {
    private final Connection connection;

    public UserReportsRepository(Connection connection) {
        this.connection = connection;
    }

    public UserReport getUserReportOnEpoch(String username, int epoch) {

        try {
            String sql = "SELECT x,y FROM UserReports WHERE username = ? AND epoch = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setString(1, username);
            preparedStatement.setInt(2, epoch);

            ResultSet rs = preparedStatement.executeQuery();

            while (rs.next()) {
                UserReport userReport = new UserReport();
                userReport.setUsername(username);
                userReport.setEpoch(epoch);
                userReport.setCoords(new Coords(
                        rs.getInt("x"),
                        rs.getInt("y")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CopyOnWriteArrayList<UserReport> getAllUserReports() {
        CopyOnWriteArrayList<UserReport> result = new CopyOnWriteArrayList<>();
        try {
            String sql = "SELECT username,epoch,x,y,isClosed,signature FROM UserReports";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            ResultSet rs = preparedStatement.executeQuery();

            while (rs.next()) {
                UserReport userReport = new UserReport();
                userReport.setUsername(rs.getString("username"));
                userReport.setEpoch(rs.getInt("epoch"));
                userReport.setCoords(new Coords(
                        rs.getInt("x"),
                        rs.getInt("y")
                ));
                userReport.setSignature(rs.getBytes("signature"));
                userReport.setClosed(rs.getBoolean("isClosed"));
                String sql1 = "SELECT prover_username,witness_username,epoch,x,y,signature FROM Proofs WHERE prover_username = ? AND epoch = ?";
                PreparedStatement preparedStatement1 = connection.prepareStatement(sql1);
                preparedStatement1.setString(1, rs.getString("username"));
                preparedStatement1.setInt(2, rs.getInt("epoch"));
                ResultSet rs1 = preparedStatement1.executeQuery();
                while (rs1.next()) {
                    Proof proof = new Proof();
                    proof.setProverUsername(rs1.getString("prover_username"));
                    proof.setWitnessUsername(rs1.getString("witness_username"));
                    proof.setEpoch(rs.getInt("epoch"));
                    proof.setCoords(new Coords(
                            rs.getInt("x"),
                            rs.getInt("y")
                    ));
                    proof.setSignature(rs.getBytes("signature"));
                    userReport.addProof(proof);
                }
                result.add(userReport);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<String> getUsersAtPos(int epoch, int x, int y) {
        try {
            String sql = "SELECT username FROM UserReports WHERE epoch = ? AND x = ? AND y = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setInt(1, epoch);
            preparedStatement.setInt(2, x);
            preparedStatement.setInt(3, y);

            ResultSet rs = preparedStatement.executeQuery();

            List<String> users = new ArrayList<>();
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
            return users;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void submitUserReport(UserReport userReport, byte[] signature) {
        try {
            String sql = "INSERT INTO UserReports(username, epoch, x, y, signature) VALUES (?,?,?,?,?)";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setString(1, userReport.getUsername());
            preparedStatement.setInt(2, userReport.getEpoch());
            preparedStatement.setInt(3, userReport.getCoords().getX());
            preparedStatement.setInt(4, userReport.getCoords().getY());
            preparedStatement.setBytes(5, signature);

            preparedStatement.executeUpdate();
            for (int i = 0; i < userReport.getProofsList().size(); i++)
                submitProof(userReport.getProofsList().get(i));
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void closeUserReport(UserReport userReport){
        try {
            String sql = "UPDATE UserReports SET isClosed = true WHERE username = ? AND epoch = ? AND x = ? AND y = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setString(1, userReport.getUsername());
            preparedStatement.setInt(2, userReport.getEpoch());
            preparedStatement.setInt(3, userReport.getCoords().getX());
            preparedStatement.setInt(4, userReport.getCoords().getY());

            preparedStatement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteReport(String username, int epoch){
        try {
            String sql = "DELETE FROM Proofs WHERE prover_username = ? AND epoch = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setString(1, username);
            preparedStatement.setInt(2, epoch);

            preparedStatement.executeUpdate();
            connection.commit();

            sql = "DELETE FROM UserReports WHERE username = ? AND epoch = ?";
            preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setString(1, username);
            preparedStatement.setInt(2, epoch);

            preparedStatement.executeUpdate();
            connection.commit();

            System.out.println("Replaced in DB!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void submitProof(Proof proof) {
        try {
            String sql = "INSERT INTO Proofs(server_id,prover_username, witness_username,epoch,x,y,signature) VALUES (?,?,?,?,?,?,?)";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setString(1, proof.getProverUsername());
            preparedStatement.setString(2, proof.getWitnessUsername());
            preparedStatement.setInt(3, proof.getEpoch());
            preparedStatement.setInt(4, proof.getCoords().getX());
            preparedStatement.setInt(5, proof.getCoords().getY());
            preparedStatement.setBytes(6,proof.getSignature());

            preparedStatement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
