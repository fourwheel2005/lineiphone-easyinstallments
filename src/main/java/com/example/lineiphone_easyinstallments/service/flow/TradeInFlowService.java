package com.example.lineiphone_easyinstallments.service.flow;

import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
// 🌟 นำเข้า MessagingApiClient สำหรับดึงชื่อ
import com.linecorp.bot.messaging.client.MessagingApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeInFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final LineMessageService lineMessageService;
    private final MessagingApiClient messagingApiClient;

    private final String ADMIN_GROUP_ID = "Cef2ceeeb8154fbd5dd92d294d467ecd4";

    @Override
    public boolean supports(String serviceName) {
        return "รับซื้อไอโฟน".equals(serviceName);
    }

    @Override
    public String getServiceName() {
        return "รับซื้อไอโฟน";
    }

    @Override
    public String processMessage(UserState userState, String userMessage) {
        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_INFO";
        String msg = userMessage.trim();
        String userId = userState.getLineUserId();

        // 🚨 ทางออกฉุกเฉิน
        if (msg.contains("แอดมิน") || msg.contains("คุยกับคน")) {
            userState.setPreviousState(state);
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);

            // ✅ แก้เป็น getCustomerName(userId)
            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "รับซื้อไอโฟน", getCustomerName(userId), "ลูกค้าเรียกแอดมิน");
            return "รับทราบครับ รบกวนรอแอดมินเข้ามาดูแลสักครู่นะครับ ⏳";
        }

        switch (state) {

            case "STEP_1_INFO":
                userState.setCurrentState("STEP_2_WAITING_DATA");
                userStateRepository.save(userState);
                return "ยินดีต้อนรับสู่บริการรับซื้อไอโฟนครับ ให้ราคาสูงแน่นอน! 💸\n\n" +
                        "เพื่อความรวดเร็วในการประเมินราคา รบกวนลูกค้าเตรียมข้อมูลดังนี้นะครับ:\n" +
                        "👉 มีกล่อง มีสายชาร์จแท้ไหม?\n" +
                        "👉 เบต้าแบต (สุขภาพแบต) เท่าไหร่?\n" +
                        "👉 เคยซ่อมมาไหม?\n" +
                        "📸 รบกวนส่ง **สภาพโดยรอบเครื่อง** และหน้า **ตั้งค่า > ทั่วไป > เกี่ยวกับ** ส่งมาให้แอดมินได้เลยครับผม";


            case "STEP_2_WAITING_DATA":
                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                // ✅ แก้เป็น getCustomerName(userId)
                lineMessageService.sendEmergencyCard(
                        ADMIN_GROUP_ID,
                        "รับซื้อไอโฟน",
                        getCustomerName(userId),
                        "ลูกค้ารอประเมินราคารับซื้อ เข้าไปตีราคาได้เลย!"
                );

                return "รับข้อมูลเรียบร้อยครับ 📸\nเดี๋ยวแอดมินจะรีบเข้ามาประเมินราคาให้ รบกวนรอสักครู่นะครับผม ⏳";


            case "ADMIN_MODE":
                return null;


            default:
                userState.setCurrentState("STEP_1_INFO");
                userStateRepository.save(userState);
                return "เริ่มต้นการประเมินราคาใหม่ครับ รบกวนแจ้งรายละเอียดเครื่องที่จะขายได้เลยครับ";
        }
    }

    // ==========================================
    // 🌟 Helper Method: ฟังก์ชันช่วยดึงชื่อลูกค้า
    // ==========================================
    private String getCustomerName(String userId) {
        try {
            var profile = messagingApiClient.getProfile(userId).get();
            return profile.body().displayName();
        } catch (Exception e) {
            log.warn("❌ ไม่สามารถดึงชื่อโปรไฟล์ของ userId: {} ได้", userId);
            return "ลูกค้า (ไม่ทราบชื่อ)";
        }
    }
}