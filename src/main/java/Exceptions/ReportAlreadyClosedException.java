package Exceptions;

public class ReportAlreadyClosedException extends Throwable {
    public ReportAlreadyClosedException(String username, int epoch) {
        super("Report for " + username + " in epoch " +  epoch + " already closed");
    }
}
