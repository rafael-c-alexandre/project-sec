package Exceptions;

public class ReportAlreadyExistsException extends Exception {

    public ReportAlreadyExistsException() {
        super("Report for this username in this epoch already exists");
    }
}
