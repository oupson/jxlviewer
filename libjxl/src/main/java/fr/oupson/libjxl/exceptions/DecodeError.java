package fr.oupson.libjxl.exceptions;

/**
 * This class represent an error from the native side.
 */
public class DecodeError extends Exception {
    public enum DecodeErrorType {
        DECODER_FAILED_ERROR,
        ICC_PROFILE_ERROR,
        METHOD_CALL_FAILED_ERROR,
        NEED_MORE_INPUT_ERROR,
        OTHER_ERROR,
        UNKNOWN_ERROR
    }

    private static DecodeErrorType errorTypeFromNative(int errorType) {
        switch (errorType) {
            case 0:
                return DecodeErrorType.DECODER_FAILED_ERROR;
            case 1:
                return DecodeErrorType.ICC_PROFILE_ERROR;
            case 2:
                return DecodeErrorType.METHOD_CALL_FAILED_ERROR;
            case 3:
                return DecodeErrorType.NEED_MORE_INPUT_ERROR;
            case 4:
                return DecodeErrorType.OTHER_ERROR;
            default:
                return DecodeErrorType.UNKNOWN_ERROR;
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

    /**
     * Create a DecoderError from an error type.
     * <p>
     * Used by the native side of the library.
     *
     * @param errorType The type of the error.
     */
    public DecodeError(int errorType) {
        this(errorTypeFromNative(errorType));
    }

    /**
     * Create a DecoderError from an error type and an error message.
     * <p>
     * Used by the native side of the library.
     *
     * @param errorType    The type of the error.
     * @param errorMessage The message of this error.
     */
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
