package Exceptions;

public class ProverNotCloseEnoughException extends Exception {

    public ProverNotCloseEnoughException() {
        super("Prover and witness are not close enough");
    }
}
