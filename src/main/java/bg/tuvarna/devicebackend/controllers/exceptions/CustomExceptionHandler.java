package bg.tuvarna.devicebackend.controllers.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
public class CustomExceptionHandler {
    @ExceptionHandler()
    public ResponseEntity<String> handleInternalExceptions(RuntimeException ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomExceptions(CustomException ex) {
        return new ResponseEntity<>(
                new ErrorResponse(ex),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        List<String> list = new ArrayList<>();

        for (ObjectError error :  ex.getBindingResult().getAllErrors()) {
            String errorMessage = error.getDefaultMessage();
            list.add(errorMessage);
        }

        return new ResponseEntity<>(
                new ErrorResponse(
                        new CustomException("Validation error", ErrorCode.Validation, list.toArray(new String[0]))
                ),
                HttpStatus.BAD_REQUEST
        );
    }
}