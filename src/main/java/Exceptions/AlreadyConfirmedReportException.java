package Exceptions;

public class AlreadyConfirmedReportException extends Exception {

    public AlreadyConfirmedReportException(String username, int epoch) {
        super("Report for user " + username + " for epoch " + epoch +" already confirmed");
    }
}
