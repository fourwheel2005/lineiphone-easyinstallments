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

    public ExtractedData extractInfo(String userMessage) {
        try {
            log.info("🔍 กำลังให้ AI สกัดข้อมูลจากข้อความ: {}", userMessage);

            ExtractedData result = chatClient.prompt()
                    .system(sys -> sys.text(extractorPromptTemplate))
                    .user(u -> u.text(userMessage))
                    .call()
                    .entity(ExtractedData.class);

            // 🌟 อัปเดต Log ให้โชว์ข้อมูลครบถ้วน
            log.info("✅ AI สกัดข้อมูลสำเร็จ: Age={}, Model={}, Capacity={}, Condition={}",
                    result.age(), result.deviceModel(), result.capacity(), result.condition());

            // 🌟 ดักจับ Null และใส่ค่า Default เป็น "unknown" ถ้าลูกค้าไม่ได้พิมพ์บอกมา
            return new ExtractedData(
                    result.age() != null ? result.age() : 0,
                    result.deviceModel() != null && !result.deviceModel().isEmpty() ? result.deviceModel() : "unknown",
                    result.capacity() != null && !result.capacity().isEmpty() ? result.capacity() : "unknown",
                    result.condition() != null && !result.condition().isEmpty() ? result.condition() : "unknown"
            );

        } catch (Exception e) {
            log.error("❌ AI Extractor ทำงานล้มเหลว: ", e);
            return new ExtractedData(0, "unknown", "unknown", "unknown");
        }
    }
}