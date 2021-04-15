package HA;

import io.grpc.StatusRuntimeException;
import util.Coords;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Scanner;

public class HAClient {

    final String GRID_FILE_PATH = "src/main/assets/grid_examples/grid1.txt";
    final String CLIENT_ADDR_MAPPINGS_FILE = "src/main/assets/mappings/mappings.txt";

    private HAFrontend haFrontend;
    private final HALogic haLogic;
    private int port;

    public HAClient() {
        /* Initialize client logic */
        haLogic = new HALogic();
        /* Import users and server from mappings */
        importAddrMappings();
    }

    public static void main(String[] args) throws FileNotFoundException {
        HAClient client = new HAClient();

        String commandsFilePath = args.length == 1 ? args[0] : null;

        System.out.println("Healthcare Authority started");
        try {
            Scanner in;
            if (commandsFilePath == null) {
                in = new Scanner(System.in);
            } else {
                in = new Scanner(new File(commandsFilePath));
            }

            boolean running = true;
            while (running) {
                String cmd = in.nextLine();
                switch (cmd) {
                    case "obtain_report" -> client.obtainReport();
                    case "users_at_location" -> client.obtainUsersAtLocation();
                    case "exit" -> running = false;
                    case "help" -> client.displayHelp();
                    default -> System.err.println("Error: Command not recognized");
                }
            }
        } finally {
            client.haFrontend.shutdown();
        }

    }

    private void obtainUsersAtLocation() {
        try {
            Scanner in = new Scanner(System.in);
            System.out.print("State the x coordinate: ");
            int x = Integer.parseInt(in.nextLine());
            System.out.print("State the y coordinate: ");
            int y = Integer.parseInt(in.nextLine());
            System.out.print("State the epoch: ");
            int epoch = Integer.parseInt(in.nextLine());
            List<String> result = haFrontend.obtainUsersAtLocation(x, y, epoch);

            if (result.size() == 0)
                System.out.println("No users at this location in the given epoch.");
            else
                System.out.println("These are the users at location (" + x + "," + y + ") at epoch "  + epoch + " :");

            for(String user : result){
                System.out.println(user);
            }
        } catch (StatusRuntimeException e) {
            System.err.println(e.getMessage());
        }
    }

    public void obtainReport() {
        try {
            Scanner in = new Scanner(System.in);
            System.out.print("Which user do you wish to get your location report? ");
            String username = in.nextLine();
            System.out.print("From which epoch do you wish to get your location report? ");
            int epoch = Integer.parseInt(in.nextLine());
            Coords result = haFrontend.obtainLocationReport(username, epoch);
            System.out.println("User " + username + " was at position (" + result.getX() + "," + result.getY() + ") at epoch " + epoch);
        } catch (StatusRuntimeException e) {
            System.err.println(e.getMessage());
        }
    }

    public void displayHelp() {

        System.out.println("obtain_report - obtain location report from a specific epoch for a specific user");
        System.out.println("users_at_location - obtain users at a specific location at a specific epoch");
        System.out.println("help - displays help message");
        System.out.println("exit - exits client");

    }

    private void importAddrMappings() {
        Scanner scanner;
        //Build frontends
        try {
            scanner = new Scanner(new File(CLIENT_ADDR_MAPPINGS_FILE));
        } catch (FileNotFoundException e) {
            System.out.println("No such client mapping file!");
            return;
        }

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // process the line
            String[] parts = line.split(",");
            String mappingsUser = parts[0].trim();
            String mappingsHost = parts[1].trim();
            int mappingsPort = Integer.parseInt(parts[2].trim());
            //SERVER
            if (mappingsUser.equals("server")) {
                haFrontend = new HAFrontend(mappingsHost, mappingsPort, haLogic);
                continue;
            }
        }
    }


}
