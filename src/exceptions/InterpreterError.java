package exceptions;

public class InterpreterError extends RuntimeException {
    public InterpreterError(String error) {
        super(error);
    }
}
