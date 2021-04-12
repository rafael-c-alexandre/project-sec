package HA;

import io.grpc.StatusRuntimeException;

import java.io.File;
import java.io.FileNotFoundException;
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

    public static void main(String[] args) {
        HAClient client = new HAClient();
        try {
            Scanner in = new Scanner(System.in);
            boolean running = true;
            while (running) {
                System.out.print("Enter command ( Type 'help' for help menu ): ");
                String cmd = in.nextLine();
                switch (cmd) {
                    case "obtain_report" -> client.obtainReport();
                    case "exit" -> running = false;
                    case "help" -> client.displayHelp();
                    default -> System.err.println("Error: Command not recognized");
                }
            }
        } finally {
            client.haFrontend.shutdown();
        }

    }

    public void obtainReport() {
        try {
            Scanner in = new Scanner(System.in);
            System.out.print("Which user do you wish to get your location report? ");
            String username = in.nextLine();
            System.out.print("From which epoch do you wish to get your location report? ");
            int epoch = Integer.parseInt(in.nextLine());
            haFrontend.obtainLocationReport(username, epoch);
        } catch (StatusRuntimeException e) {
            System.out.println(e.getMessage());
        }
    }

    public void displayHelp() {


        System.out.println("obtain_report - obtain my location report from a specific epoch");
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
