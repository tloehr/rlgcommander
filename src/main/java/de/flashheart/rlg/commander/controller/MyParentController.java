package de.flashheart.rlg.commander.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.flashheart.rlg.commander.games.GameSetupException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.InvocationTargetException;
import java.util.NoSuchElementException;

@Log4j2
public class MyParentController {
//    @ExceptionHandler({
//            JSONException.class,
//            NoSuchElementException.class,
//            ArrayIndexOutOfBoundsException.class,
//            ClassNotFoundException.class,
//            NoSuchMethodException.class,
//            InvocationTargetException.class,
//            InstantiationException.class,
//            IllegalAccessException.class,
//            IllegalStateException.class,
//            JsonParseException.class,
//            JsonProcessingException.class
//    })
//    public ResponseEntity<?> handleException(Exception exc) {
//        log.warn(exc.getMessage());
//        return new ResponseEntity(exc, HttpStatus.NOT_FOUND);
//    }


    @ExceptionHandler({Exception.class})
    public ResponseEntity<?> handleException(Exception exc) {
        log.warn(exc.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (exc instanceof EntityNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (exc instanceof IllegalArgumentException
                || exc instanceof NoSuchElementException
                || exc instanceof ArrayIndexOutOfBoundsException) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        } else if (exc instanceof InvocationTargetException && exc.getCause() instanceof Exception) {
            // throw cause instead
            exc = (Exception) exc.getCause();
        } else if (exc instanceof GameSetupException) {
            status = HttpStatus.NOT_ACCEPTABLE;
        } else {
            exc.printStackTrace();
            log.error(exc.getStackTrace());
        }
        return new ResponseEntity<>(new JSONObject()
                .put("exception", exc.getClass().getName())
                .put("message", exc.getMessage()).toString(4), status);
    }
}
