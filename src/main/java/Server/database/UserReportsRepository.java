package Server.database;

import Server.UserReport;
import util.Coords;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserReportsRepository {
    private final Connection connection;

    public UserReportsRepository(Connection connection) {
        this.connection = connection;
    }

    public UserReport getUserReportOnEpoch(String username, int epoch){

        try{
            String sql = "SELECT x,y FROM UserReports WHERE username = ? AND epoch = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setString(1,username);
            preparedStatement.setInt(2,epoch);

            ResultSet rs = preparedStatement.executeQuery();

            while (rs.next()){
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

    public List<String> getUsersAtPos(int epoch, int x, int y){
        try{
            String sql = "SELECT username FROM UserReports WHERE epoch = ? AND x = ? AND y = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setInt(1,epoch);
            preparedStatement.setInt(2,x);
            preparedStatement.setInt(3,y);

            ResultSet rs = preparedStatement.executeQuery();

            List<String> users = new ArrayList<>();
            while (rs.next()){
                users.add(rs.getString("username"));
            }
            return users;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void submitUserReport(UserReport userReport){
        try{
            String sql = "INSERT INTO UserReports VALUES (?,?,?,?)";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            preparedStatement.setString(1,userReport.getUsername());
            preparedStatement.setInt(2,userReport.getEpoch());
            preparedStatement.setInt(3,userReport.getCoords().getX());
            preparedStatement.setInt(4,userReport.getCoords().getY());

            preparedStatement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
