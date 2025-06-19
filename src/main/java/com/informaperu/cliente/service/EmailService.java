package com.informaperu.cliente.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendNotification(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("lesliemarlo09@gmail.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            logger.info("✅ Correo enviado a {} con asunto: {}. Motivo: Envío exitoso.", to, subject);
        } catch (Exception e) {
            logger.error("❌ Error al enviar correo a {}. Motivo: {}. Acción: Verifique las credenciales de correo en application.properties o la configuración SMTP.", 
                    to, e.getMessage());
            throw new RuntimeException("Error al enviar correo: " + e.getMessage(), e);
        }
    }
}