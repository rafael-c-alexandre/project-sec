package Exceptions;

public class NoSuchCoordsException extends Exception {

    public NoSuchCoordsException() {
        super("No report found for the given location");
    }
}
