package com.example.lineiphone_easyinstallments.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * 🌟 สร้าง ChatClient ให้พร้อมใช้งานทั่วทั้งโปรเจกต์
     * ค่า Model และ Temperature จะถูกดึงมาจาก application.yml อัตโนมัติ
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                // ถ้าในอนาคตอยากใส่ Default System Prompt แบบฝังราก ก็ใส่ตรงนี้ได้เลยครับ
                // .defaultSystem("คุณคือผู้ช่วยร้านมือถือ...")
                .build();
    }
}