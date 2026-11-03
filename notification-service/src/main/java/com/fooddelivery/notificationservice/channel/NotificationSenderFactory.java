package com.fooddelivery.notificationservice.channel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.notificationservice.entity.NotificationChannel;
import org.springframework.stereotype.Component;

/** Factory: picks the sender of a channel. Every sender bean registers itself. */
@Component
public class NotificationSenderFactory {

    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);

    public NotificationSenderFactory(List<NotificationSender> senders) {
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
    }

    public NotificationSender getSender(NotificationChannel channel) {
        NotificationSender sender = senders.get(channel);
        if (sender == null) {
            throw new IllegalStateException("No sender for channel " + channel);
        }
        return sender;
    }
}
