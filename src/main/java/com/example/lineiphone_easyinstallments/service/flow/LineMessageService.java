package com.example.lineiphone_easyinstallments.service.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.net.URI;
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

    @Value("classpath:flex/emergency_card.json")
    private Resource emergencyCardTemplate;

    @Value("classpath:flex/success_card.json")
    private Resource successCardTemplate;

    @Value("classpath:flex/welcome_card.json")
    private Resource welcomeCardTemplate;

    private String adminCardJsonCache;
    private String emergencyCardJsonCache;
    private String successCardJsonCache;
    private String welcomeCardJsonCache;

    @PostConstruct
    public void initTemplates() {
        try {
            adminCardJsonCache = StreamUtils.copyToString(adminApprovalCardTemplate.getInputStream(), StandardCharsets.UTF_8);
            emergencyCardJsonCache = StreamUtils.copyToString(emergencyCardTemplate.getInputStream(), StandardCharsets.UTF_8);
            successCardJsonCache = StreamUtils.copyToString(successCardTemplate.getInputStream(), StandardCharsets.UTF_8);
            welcomeCardJsonCache = StreamUtils.copyToString(welcomeCardTemplate.getInputStream(), StandardCharsets.UTF_8);
            log.info("✅ โหลด Flex Message Templates (เวอร์ชันใหม่) เข้าหน่วยความจำสำเร็จ");
        } catch (Exception e) {
            log.error("❌ ไม่สามารถโหลด Flex Message Templates ได้: ", e);
        }
    }

    /**
     * 🌟 ส่งการ์ดต้อนรับ
     */
    public void sendWelcomeCard(String toUserId) {
        try {
            executePushMessage(toUserId, "ยินดีต้อนรับสู่ ร้านทันใจ 🙏✨", welcomeCardJsonCache);
        } catch (Exception e) {
            log.error("❌ Error sendWelcomeCard: ", e);
            replyText(toUserId, "สวัสดีครับคุณลูกค้า 🙏✨\nพิมพ์ 'สนใจผ่อน' เพื่อเริ่มทำรายการได้เลยครับ");
        }
    }

    /**
     * 📋 ส่งการ์ดให้แอดมินอนุมัติ (รูปภาพ / ข้อมูล)
     */
    public void sendAdminApprovalCard(String adminGroupId, String serviceNameTh, String serviceNameEn, String customerName, String userId, String extraInfo) {
        sendCardBase(adminGroupId, adminCardJsonCache, "คิวงานใหม่: รอตรวจสอบ", serviceNameTh, serviceNameEn, customerName, userId, extraInfo);
    }

    /**
     * 🚨 ส่งการ์ดฉุกเฉิน (ลูกค้าหงุดหงิด / ข้อมูลผิดพลาด)
     */
    public void sendEmergencyCard(String adminGroupId, String serviceNameTh, String userId, String extraInfo) {
        // เติม "general" และ "ลูกค้า" ให้โดยอัตโนมัติ
        sendCardBase(adminGroupId, emergencyCardJsonCache, "🚨 แจ้งเตือนฉุกเฉิน", serviceNameTh, "general", "ลูกค้า", userId, extraInfo);
    }

    /**
     * 🚨 เวอร์ชัน 2: สำหรับ Flow ใหม่ (รับพารามิเตอร์ 6 ตัว แบบจัดเต็ม)
     * แสดงข้อมูลบน Flex Message ได้ครบถ้วนสมบูรณ์แบบ
     */
    public void sendEmergencyCard(String adminGroupId, String serviceNameTh, String serviceNameEn, String customerName, String userId, String extraInfo) {
        sendCardBase(adminGroupId, emergencyCardJsonCache, "🚨 แจ้งเตือนฉุกเฉิน", serviceNameTh, serviceNameEn, customerName, userId, extraInfo);
    }

    /**
     * 🎉 ส่งการ์ดปิดการขาย (เลือกระยะเวลาผ่อนสำเร็จ)
     */
    public void sendSuccessCard(String adminGroupId, String serviceNameTh, String serviceNameEn, String customerName, String userId, String extraInfo) {
        sendCardBase(adminGroupId, successCardJsonCache, "🎉 ลูกค้ายืนยันจำนวนงวดแล้ว", serviceNameTh, serviceNameEn, customerName, userId, extraInfo);
    }

    // =========================================================
    // Private Helper Methods
    // =========================================================

    private void sendCardBase(String adminGroupId, String jsonTemplate, String altText, String serviceNameTh,
                              String serviceNameEn, String customerName, String userId, String extraInfo) {
        try {
            String finalJson = jsonTemplate
                    .replace("{{SERVICE_NAME}}", escapeJson(serviceNameTh))
                    .replace("{{SERVICE_EN}}", escapeJson(serviceNameEn))
                    .replace("{{CUSTOMER_NAME}}", escapeJson(customerName))
                    .replace("{{USER_ID}}", escapeJson(userId))
                    .replace("{{EXTRA_INFO}}", escapeJson(extraInfo));

            executePushMessage(adminGroupId, altText, finalJson);
        } catch (Exception e) {
            log.error("❌ Error sendCardBase: ", e);
        }
    }

    private void executePushMessage(String targetId, String altText, String jsonPayload) throws Exception {
        FlexContainer flexContainer = objectMapper.readValue(jsonPayload, FlexContainer.class);
        FlexMessage flexMessage = new FlexMessage(altText, flexContainer);

        PushMessageRequest request = new PushMessageRequest(
                targetId, List.of(flexMessage), false, null
        );
        messagingApiClient.pushMessage(UUID.randomUUID(), request);
    }

    private String escapeJson(String input) {
        if (input == null) return "-";
        return input.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    public void replyText(String replyToken, String text) {
        try {
            messagingApiClient.replyMessage(new ReplyMessageRequest(replyToken, List.of(new TextMessage(text)), false));
        } catch (Exception e) {
            log.error("❌ ไม่สามารถตอบกลับข้อความได้: ", e);
        }
    }

    public void sendImage(String to, String imageUrl) {
        try {
            ImageMessage imageMessage = new ImageMessage(URI.create(imageUrl), URI.create(imageUrl));
            PushMessageRequest request = new PushMessageRequest(to, List.of(imageMessage), false, null);
            messagingApiClient.pushMessage(UUID.randomUUID(), request).get();
        } catch (Exception e) {
            log.error("❌ เกิดข้อผิดพลาดในการส่งรูปภาพถึง: {}", to, e);
        }
    }
}