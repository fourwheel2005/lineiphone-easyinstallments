package com.example.lineiphone_easyinstallments.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "chat_history", indexes = {
        @Index(name = "idx_line_user_id", columnList = "lineUserId") // 🌟 สำคัญมาก! ทำ Index เพื่อให้ดึงประวัติแชทของคนนั้นๆ ได้เร็วปรู๊ดปร๊าด
})
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String lineUserId;

    @Column(nullable = false)
    private String role; // เก็บค่าเป็น "USER" หรือ "ASSISTANT"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // ข้อความที่คุยกัน

    @CreationTimestamp
    private LocalDateTime createdAt;
}