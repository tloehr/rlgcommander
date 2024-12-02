package de.flashheart.rlg.commander.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.InvocationTargetException;
import java.util.NoSuchElementException;

@Log4j2
public class MyParentController {
    @ExceptionHandler({
            JSONException.class,
            NoSuchElementException.class,
            ArrayIndexOutOfBoundsException.class,
            ClassNotFoundException.class,
            NoSuchMethodException.class,
            InvocationTargetException.class,
            InstantiationException.class,
            IllegalAccessException.class,
            IllegalStateException.class,
            JsonParseException.class,
            JsonProcessingException.class
    })
    public ResponseEntity<ErrorMessage> handleException(Exception exc) {
        log.warn(exc.getMessage());
        return new ResponseEntity(exc, HttpStatus.NOT_FOUND);
    }
}
