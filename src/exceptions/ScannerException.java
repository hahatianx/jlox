package exceptions;

public class ScannerException extends Exception {

    private final int line;
    private final String message;

    public ScannerException(int line, String message) {
        super();
        this.line = line;
        this.message = message;
    }

}
