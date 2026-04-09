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
     * ตีความคำตอบของลูกค้าว่าเป็น YES / NO / UNCLEAR
     *
     * @param userMessage ข้อความที่ลูกค้าพิมพ์
     * @return ScreeningAnswer enum
     */
    public ScreeningAnswer interpret(String userMessage) {
        try {
            log.info("🔍 [Screening] กำลังตีความ: \"{}\"", userMessage);

            // 🌟 ใช้ Spring AI ถาม AI ตีความ
            String rawResponse = chatClient.prompt()
                    .system(sys -> sys.text(screeningPromptResource))
                    .user(u -> u.text(userMessage))
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

            // Parse JSON {"answer": "YES"/"NO"/"UNCLEAR"}
            // ลบ 3 บรรทัดเดิมออก แล้วใช้ชุดนี้แทน
            String upper = cleaned.toUpperCase();

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
            return ScreeningAnswer.UNCLEAR; // Safe default: ถามซ้ำแทนที่จะ reject
        }
    }
}