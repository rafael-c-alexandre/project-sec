package Exceptions;

public class InvalidProofOfWorkException extends Exception {
    public InvalidProofOfWorkException() {
        super("Invalid proof of work");
    }
}
