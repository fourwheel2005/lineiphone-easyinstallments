package com.example.lineiphone_easyinstallments.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiScreeningService {

    public enum ScreeningAnswer { YES, NO, UNCLEAR }

    private final ChatClient chatClient;

    @Value("classpath:prompt/screening-prompt.st")
    private Resource screeningPromptResource;

    public AiScreeningService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * ตีความคำตอบของลูกค้าว่าเป็น YES / NO / UNCLEAR (อัปเกรดเพิ่ม Context Memory)
     *
     * @param currentMessage ข้อความล่าสุดที่ลูกค้าพิมพ์
     * @param lastMessage ข้อความก่อนหน้าที่ลูกค้าเคยพิมพ์ (ความจำ)
     * @return ScreeningAnswer enum
     */
    // 🟢 1. แก้ไขให้รับพารามิเตอร์ 2 ตัว
    public ScreeningAnswer interpret(String currentMessage, String lastMessage) {
        try {
            // 🟢 2. รวมประโยคเก่าและใหม่เข้าด้วยกัน (ถ้าไม่มีประโยคเก่าให้บอกว่า "ไม่มี")
            String context = (lastMessage != null && !lastMessage.trim().isEmpty()) ? lastMessage : "ไม่มี";
            String combinedMessage = "ข้อความก่อนหน้า: " + context + " | ข้อความล่าสุด: " + currentMessage;

            log.info("🔍 [Screening] กำลังตีความ: {}", combinedMessage);

            // 🌟 ใช้ Spring AI ถาม AI ตีความ
            String rawResponse = chatClient.prompt()
                    .system(sys -> sys.text(screeningPromptResource))
                    .user(u -> u.text(combinedMessage)) // 🟢 3. ส่งประโยคที่รวมแล้วไปให้ AI
                    .call()
                    .content();

            if (rawResponse == null) {
                log.warn("⚠️ [Screening] AI ไม่ตอบ → ถือว่า UNCLEAR");
                return ScreeningAnswer.UNCLEAR;
            }

            String cleaned = rawResponse.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            String upper = cleaned.toUpperCase();

            // เช็ค UNCLEAR ก่อน เพื่อป้องกันเคสที่ AI ตอบมาว่า "UNCLEAR, it is not YES or NO"
            if (upper.contains("UNCLEAR")) {
                log.info("✅ [Screening] → UNCLEAR");
                return ScreeningAnswer.UNCLEAR;
            }
            if (upper.contains("YES")) {
                log.info("✅ [Screening] → YES");
                return ScreeningAnswer.YES;
            }
            if (upper.contains("NO")) {
                log.info("✅ [Screening] → NO");
                return ScreeningAnswer.NO;
            }

            log.warn("⚠️ [Screening] parse ไม่ได้: {} → ถือว่า UNCLEAR", cleaned);
            return ScreeningAnswer.UNCLEAR;

        } catch (Exception e) {
            log.error("❌ [Screening] Error: ", e);
            return ScreeningAnswer.UNCLEAR; // Safe default
        }
    }
}