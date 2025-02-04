package de.flashheart.rlg.commander.misc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * This is the baseclass used to create small packages to communicate with the webclient.
 */
@Getter
public class ClientNotificationMessage {
    private final LocalDateTime timestamp;
    private final String message_class;
    public ClientNotificationMessage(String messageClass) {
        this.message_class = messageClass;
        this.timestamp = LocalDateTime.now();
    }
}
