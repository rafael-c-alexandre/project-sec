package Exceptions;

public class ReportAlreadyExistsException extends Exception {

    public ReportAlreadyExistsException(String username, int epoch) {
        super("Report for " + username + " in epoch " +  epoch + " already exists");
    }
}
