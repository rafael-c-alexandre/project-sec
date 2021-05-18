package Exceptions;

public class InvalidFreshnessToken extends Exception {

    public InvalidFreshnessToken() {
        super("Invalid freshness token");
    }
}
