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

    // 🌟 สร้าง ChatClient แบบคลีนๆ ไม่ต้องใส่ Option ตรงนี้ให้เกิด Error
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
                    .entity(ExtractedData.class); // บังคับคาย JSON กลับมาเป็น Class

            log.info("✅ AI สกัดข้อมูลสำเร็จ: Age={}, Model={}", result.age(), result.deviceModel());

            return new ExtractedData(
                    result.age() != null ? result.age() : 0,
                    result.deviceModel() != null ? result.deviceModel() : "unknown"
            );

        } catch (Exception e) {
            log.error("❌ AI Extractor ทำงานล้มเหลว: ", e);
            return new ExtractedData(0, "unknown");
        }
    }
}