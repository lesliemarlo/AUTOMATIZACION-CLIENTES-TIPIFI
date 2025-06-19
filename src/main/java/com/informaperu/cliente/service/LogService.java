package com.informaperu.cliente.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LogService {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public LogService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendLog(String message) {
        messagingTemplate.convertAndSend("/topic/logs", message);
    }
}