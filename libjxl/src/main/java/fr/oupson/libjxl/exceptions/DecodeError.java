package fr.oupson.libjxl.exceptions;

public class DecodeError extends Exception {
    public enum DecodeErrorType {
        DecoderFailedError,
        ICCProfileError,
        MethodCallFailedError,
        NeedMoreInputError,
        UnknownError
    }

    private static DecodeErrorType errorTypeFromNative(int errorType) {
        switch (errorType) {
            case 0:
                return DecodeErrorType.DecoderFailedError;
            case 1:
                return DecodeErrorType.ICCProfileError;
            case 2:
                return DecodeErrorType.MethodCallFailedError;
            case 3:
                return DecodeErrorType.NeedMoreInputError;
            default:
                return DecodeErrorType.UnknownError;
        }
    }

    private final DecodeErrorType errorType;
    private final String errorMessage;

    public DecodeError(DecodeErrorType errorType) {
        super(String.format("Failed to decode : %s", errorType));
        this.errorType = errorType;
        this.errorMessage = null;
    }

    public DecodeError(DecodeErrorType errorType, String errorMessage) {
        super(String.format("Failed to decode : %s with %s", errorType, errorMessage));
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public DecodeError(int errorType) {
        this(errorTypeFromNative(errorType));
    }

    public DecodeError(int errorType, String errorMessage) {
        this(errorTypeFromNative(errorType), errorMessage);
    }

    public DecodeErrorType getErrorType() {
        return errorType;
    }


    public String getErrorMessage() {
        return errorMessage;
    }
}
