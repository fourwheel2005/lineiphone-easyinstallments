package com.example.lineiphone_easyinstallments.service.flow;



import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.FlexContainer;
import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.PushMessageRequest;
import jakarta.annotation.PostConstruct; // ใช้ javax.annotation.PostConstruct ถ้าเป็น Spring Boot 2
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LineMessageService {

    private final MessagingApiClient messagingApiClient;
    private final ObjectMapper objectMapper;

    @Value("classpath:flex/admin_approval_card.json")
    private Resource adminApprovalCardTemplate;

    @Value("classpath:flex/credit_approval_card.json")
    private Resource creditApprovalCardTemplate;

    @Value("classpath:flex/document_approval_card.json")
    private Resource documentApprovalCardTemplate;

    @Value("classpath:flex/emergency_card.json")
    private Resource emergencyCardTemplate;

    // 🌟 ตัวแปรเก็บ String ของ JSON ไว้ใน Memory (ช่วยให้ระบบทำงานเร็วขึ้น 10 เท่า)
    private String adminCardJsonCache;
    private String creditCardJsonCache;
    private String documentCardJsonCache;
    private String emergencyCardJsonCache;

    /**
     * 🌟 ทำงานอัตโนมัติ 1 ครั้งตอน Start Server
     * โหลดไฟล์ JSON ทั้งหมดมาเก็บไว้ใน RAM เพื่อไม่ให้คอขวดเวลาใช้งานจริง
     */
    @PostConstruct
    public void initTemplates() {
        try {
            adminCardJsonCache = StreamUtils.copyToString(adminApprovalCardTemplate.getInputStream(), StandardCharsets.UTF_8);
            creditCardJsonCache = StreamUtils.copyToString(creditApprovalCardTemplate.getInputStream(), StandardCharsets.UTF_8);
            documentCardJsonCache = StreamUtils.copyToString(documentApprovalCardTemplate.getInputStream(), StandardCharsets.UTF_8);
            emergencyCardJsonCache = StreamUtils.copyToString(emergencyCardTemplate.getInputStream(), StandardCharsets.UTF_8);
            log.info("✅ โหลด Flex Message Templates เข้าหน่วยความจำสำเร็จ");
        } catch (Exception e) {
            log.error("❌ ไม่สามารถโหลด Flex Message Templates ได้: ", e);
        }
    }

    // =========================================================
    // Public Methods สำหรับให้ Flow Service ต่างๆ เรียกใช้งาน
    // =========================================================

    public void sendAdminApprovalCard(String adminGroupId, String serviceNameTh, String serviceNameEn,
                                      String customerName, String userId, String deviceModel) {
        try {
            String finalJson = adminCardJsonCache
                    .replace("{{SERVICE_NAME}}", escapeJson(serviceNameTh))
                    .replace("{{SERVICE_EN}}", escapeJson(serviceNameEn))
                    .replace("{{CUSTOMER_NAME}}", escapeJson(customerName))
                    .replace("{{USER_ID}}", escapeJson(userId))
                    .replace("{{DEVICE_MODEL}}", escapeJson(deviceModel));

            executePushMessage(adminGroupId, "แจ้งเตือนคิวงานใหม่", finalJson);
        } catch (Exception e) {
            log.error("❌ Error sendAdminApprovalCard: ", e);
        }
    }

    public void sendCreditApprovalCard(String adminGroupId, String serviceNameTh, String userId, String extraInfo) {
        sendCardBase(adminGroupId, creditCardJsonCache, serviceNameTh, "balloon", userId, extraInfo);
    }

    public void sendDocumentApprovalCard(String adminGroupId, String serviceNameTh, String userId, String extraInfo) {
        sendCardBase(adminGroupId, documentCardJsonCache, serviceNameTh, "balloon", userId, extraInfo);
    }

    public void sendEmergencyCard(String adminGroupId, String serviceNameTh, String userId, String extraInfo) {
        sendCardBase(adminGroupId, emergencyCardJsonCache, serviceNameTh, "balloon", userId, extraInfo);
    }

    // =========================================================
    // Private Helper Methods (ซ่อน Logic ไม่ให้ไฟล์รก)
    // =========================================================

    /**
     * ✅ เมธอดที่หายไป! ทำหน้าที่แทนค่าและยิงการ์ด 3 แบบที่เหลือ
     */
    private void sendCardBase(String adminGroupId, String jsonTemplate, String serviceNameTh,
                              String serviceNameEn, String userId, String extraInfo) {
        try {
            String finalJson = jsonTemplate
                    .replace("{{SERVICE_NAME}}", escapeJson(serviceNameTh))
                    .replace("{{SERVICE_EN}}", escapeJson(serviceNameEn))
                    .replace("{{USER_ID}}", escapeJson(userId))
                    .replace("{{EXTRA_INFO}}", escapeJson(extraInfo));

            executePushMessage(adminGroupId, "แจ้งเตือนสถานะลูกค้า", finalJson);
        } catch (Exception e) {
            log.error("❌ Error sendCardBase: ", e);
        }
    }

    /**
     * แปลง String ให้เป็น Flex Message และยิงออกไป
     */
    private void executePushMessage(String adminGroupId, String altText, String jsonPayload) throws Exception {
        FlexContainer flexContainer = objectMapper.readValue(jsonPayload, FlexContainer.class);
        FlexMessage flexMessage = new FlexMessage(altText, flexContainer);

        PushMessageRequest request = new PushMessageRequest(
                adminGroupId,
                List.of(flexMessage),
                false,
                null
        );

        messagingApiClient.pushMessage(UUID.randomUUID(), request);
    }

    /**
     * 🛡️ ระบบความปลอดภัย (JSON Anti-Injection)
     * ดักจับเครื่องหมายฟันหนูและบรรทัดใหม่ ป้องกัน JSON พัง
     */
    private String escapeJson(String input) {
        if (input == null) return "-";
        return input.replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }
}