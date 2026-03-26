package com.example.lineiphone_easyinstallments.repository;

import com.example.lineiphone_easyinstallments.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {


    List<ChatHistory> findTop10ByLineUserIdOrderByCreatedAtDesc(String lineUserId);

    /**
     * 🧹 แถมให้ครับ: ฟังก์ชันสำหรับ "ล้างประวัติแชท" (Clear Chat)
     * เผื่ออนาคตคุณอยากทำปุ่มใน Rich Menu ให้ลูกค้ากด "เริ่มคุยเรื่องใหม่"
     * เพื่อล้างสมองบอทไม่ให้จำเรื่องเก่ามาปนกับเรื่องใหม่ครับ
     */
    @Transactional
    void deleteByLineUserId(String lineUserId);
}