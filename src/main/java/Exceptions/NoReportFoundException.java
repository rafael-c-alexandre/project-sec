package Exceptions;

public class NoReportFoundException extends Exception {

    public NoReportFoundException(String username, int epoch) {
        super("No report found for " + username + " in epoch " + epoch);
    }
}
