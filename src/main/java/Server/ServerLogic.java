package Server;

import Server.database.Connector;
import Server.database.UserReportsRepository;
import util.Coords;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ServerLogic {

    private List<UserReport> reportList;
    private UserReportsRepository reportsRepository;


    public ServerLogic(Connection Connection) {
        reportsRepository = new UserReportsRepository(Connection);
        this.reportList = reportsRepository.getAllUserReports();
    }

    public void submitReport(String username, int epoch, int x, int y){

        UserReport userReport = new UserReport(
                epoch,
                username,
                new Coords(x, y));

        this.reportList.add(userReport);

        //Add to database
        reportsRepository.submitUserReport(userReport);

    }

    public Coords obtainLocationReport(String username, int epoch){
        for(UserReport report : this.reportList){
            if(report.getUsername().equals(username) && report.getEpoch() == epoch)
                return report.getCoords();
        }
        return null;
    }

    public List<String> obtainUsersAtLocation(int x, int y, int epoch){
        return this.reportList.stream()
                .filter(report -> report.getCoords().getY() == y && report.getCoords().getX() == x && report.getEpoch() == epoch)
                .map(report -> report.getUsername())
                .collect(Collectors.toList());
    }

    public void submitReport(String json){
        this.reportList.add(new UserReport(json));
    }


    public boolean verifyProofs(){

        return true;
    }
}
