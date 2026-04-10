package com.example.lineiphone_easyinstallments.service.ai;

import com.example.lineiphone_easyinstallments.dto.ExtractedData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiDataExtractorService {

    private final ChatClient chatClient;

    @Value("classpath:prompt/extractor-prompt.st")
    private Resource extractorPromptTemplate;

    public AiDataExtractorService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * สกัดข้อมูลจากข้อความลูกค้า (อัปเกรดเพิ่ม Context Memory)
     *
     * @param currentMessage ข้อความล่าสุดที่ลูกค้าพิมพ์
     * @param lastMessage ข้อความก่อนหน้าที่ลูกค้าเคยพิมพ์ (ความจำ)
     * @return ข้อมูลที่ถูกสกัดออกมาเป็น ExtractedData
     */
    // 🟢 1. แก้ไข Method Signature ให้รับ lastMessage ด้วย
    public ExtractedData extractInfo(String currentMessage, String lastMessage) {
        try {
            // 🟢 2. รวมประโยคเก่าและใหม่เข้าด้วยกัน
            String context = (lastMessage != null && !lastMessage.trim().isEmpty()) ? lastMessage : "ไม่มี";
            String combinedMessage = "ข้อความก่อนหน้า: " + context + " | ข้อความล่าสุด: " + currentMessage;

            log.info("🔍 [Extractor] กำลังให้ AI สกัดข้อมูลจาก: {}", combinedMessage);

            // 🌟 ใช้ Spring AI ถาม AI ตีความ
            ExtractedData result = chatClient.prompt()
                    .system(sys -> sys.text(extractorPromptTemplate))
                    .user(u -> u.text(combinedMessage)) // 🟢 3. ส่งประโยคที่รวมแล้วไปให้ AI
                    .call()
                    .entity(ExtractedData.class);

            // 🌟 อัปเดต Log ให้โชว์ข้อมูลครบถ้วน
            log.info("✅ [Extractor] สกัดข้อมูลสำเร็จ: Age={}, Model={}, Capacity={}, Condition={}",
                    result.age(), result.deviceModel(), result.capacity(), result.condition());

            // 🌟 ดักจับ Null และใส่ค่า Default เป็น "unknown" ถ้าลูกค้าไม่ได้พิมพ์บอกมา
            return new ExtractedData(
                    result.age() != null ? result.age() : 0,
                    result.deviceModel() != null && !result.deviceModel().isEmpty() ? result.deviceModel() : "unknown",
                    result.capacity() != null && !result.capacity().isEmpty() ? result.capacity() : "unknown",
                    result.condition() != null && !result.condition().isEmpty() ? result.condition() : "unknown"
            );

        } catch (Exception e) {
            log.error("❌ [Extractor] ทำงานล้มเหลว: ", e);
            return new ExtractedData(0, "unknown", "unknown", "unknown");
        }
    }
}