package Server;

import util.Coords;

import java.util.ArrayList;
import java.util.List;

public class ServerLogic {

    private List<UserReport> reportList = new ArrayList<>();


    public ServerLogic() {
    }

    public void submitReport(String username, int epoch, int x, int y){
        reportList.add(new UserReport(
                epoch,
                username,
                new Coords(x, y)
        ));
    }

    public void submitReport(String json){
        reportList.add(new UserReport(json));
    }


    public boolean verifyProofs(){

        return true;
    }
}
