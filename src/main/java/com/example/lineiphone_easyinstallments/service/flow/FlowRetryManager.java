package com.example.lineiphone_easyinstallments.service.flow;

import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowRetryManager {

    private final UserStateRepository userStateRepository;
    private final LineMessageService lineMessageService;
    private final MessagingApiClient messagingApiClient;

    // Mapping สำหรับ Group ID ของแต่ละแผนก
    private static final String GENERAL_ADMIN_GROUP_ID = "C9a256ba28c79d51b09c6a07f51471b25";
    private static final String BALLOON_ADMIN_GROUP_ID = "C75eb19ed18cf5a67a1461f785f581e78";

    public String handleRetryLogic(UserState userState, String userId, String msg, String serviceName, String adminAlertReason, String retryPrompt) {
        int currentRetry = userState.getRetryCount() != null ? userState.getRetryCount() : 0;
        currentRetry++;

        if (currentRetry >= 2) {
            userState.setPreviousState(userState.getCurrentState()); // จำสเต็ปเดิม
            userState.setCurrentState("ADMIN_MODE");
            userState.setRetryCount(0);
            userStateRepository.save(userState);

            String customerName = getCustomerName(userId);
            String groupId = resolveGroupId(serviceName);

            lineMessageService.sendEmergencyCard(
                    groupId,
                    serviceName != null ? serviceName : "สอบถามทั่วไป",
                    "balloon".equals(serviceName) ? "balloon" : "general", // Template type fallback
                    customerName,
                    userId,
                    adminAlertReason + " เกิน 2 ครั้ง (ข้อความล่าสุด: " + msg + ")"
            );

            return "ดูเหมือนระบบอัตโนมัติจะยังไม่เข้าใจข้อมูลส่วนนี้ 😅 เพื่อความรวดเร็วเดี๋ยวให้แอดมินเข้ามาดูแลเคสนี้ให้นะครับ รบกวนรอสักครู่ครับ ⏳";
        }

        userState.setRetryCount(currentRetry);
        userStateRepository.save(userState);
        return retryPrompt;
    }

    public String resolveGroupId(String serviceName) {
        if ("ผ่อนบอลลูน".equals(serviceName) || "balloon".equals(serviceName)) {
            return BALLOON_ADMIN_GROUP_ID;
        }
        // ถ้าเป็นแผนกอื่นที่ยังไม่ได้ระบุ Group ID เฉพาะ ให้เด้งเข้ากลุ่มรวมก่อน
        return GENERAL_ADMIN_GROUP_ID;
    }

    public String getCustomerName(String userId) {
        try {
            return messagingApiClient.getProfile(userId).get().body().displayName();
        } catch (Exception e) {
            log.error("ดึงชื่อลูกค้าไม่ได้: ", e);
            return "ลูกค้า (ไม่ทราบชื่อ)";
        }
    }
}
