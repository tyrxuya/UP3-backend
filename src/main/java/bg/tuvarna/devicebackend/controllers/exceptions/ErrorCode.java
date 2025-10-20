package bg.tuvarna.devicebackend.controllers.exceptions;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    EntityNotFound(0),
    AlreadyExists(1),
    WrongCredentials(2),
    NotRegistered(3),
    Failed(4),
    Validation(5);

    private final int code;

    @JsonValue
    public int getCode() {
        return code;
    }
}