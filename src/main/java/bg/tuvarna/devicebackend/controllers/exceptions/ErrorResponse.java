package bg.tuvarna.devicebackend.controllers.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public final class ErrorResponse {
    private String error;
    private int errorCode;
    private LocalDateTime timestamp;
    private ErrorType type;
    private String[] validations;

    public ErrorResponse(CustomException ex) {
        this(ex.getMessage(), ex.getErrorCode().getCode(), LocalDateTime.now(), ErrorType.ERROR, ex.getArguments());
    }
}