package com.example.lineiphone_easyinstallments.service.flow;

import com.example.lineiphone_easyinstallments.dto.ExtractedData;
import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import com.example.lineiphone_easyinstallments.service.ai.AiDataExtractorService;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;
import com.linecorp.bot.messaging.model.TextMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final LineMessageService lineMessageService;
    private final AiDataExtractorService aiDataExtractorService;
    private final MessagingApiClient messagingApiClient;

    private final String ADMIN_GROUP_ID = "C7c79cdda1b97da92c07a5c45bda0ab0f";

    @Override
    public boolean supports(String serviceName) {
        return "ออมดาวน์".equals(serviceName);
    }

    @Override
    public String getServiceName() {
        return "ออมดาวน์";
    }

    @Override
    public String processMessage(UserState userState, String userMessage) {
        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_WELCOME";
        String msg = userMessage.trim();
        String userId = userState.getLineUserId();


        if (msg.contains("แอดมิน") || msg.contains("คุยกับคน") || msg.contains("คืนเงิน")) {
            userState.setPreviousState(state);
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);
            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "ออมดาวน์", userId, "ลูกค้าเรียกแอดมิน/สอบถามเรื่องคืนเงิน");
            return "รับทราบครับ รบกวนรอแอดมินเข้ามาดูแลและให้คำปรึกษาสักครู่นะครับ ⏳";
        }

        // ─────────────────────────────────────────────────────────────
        // ✅ GLOBAL: ลูกค้าต้องการเปลี่ยน/แก้ไขรุ่นที่สนใจ
        // รองรับทั้งกรณีพิมพ์ keyword ชัดเจน และกรณีอยู่ใน STEP_3
        // ─────────────────────────────────────────────────────────────
        boolean isChangeModelKeyword =
                msg.contains("เปลี่ยนรุ่น") ||
                        msg.contains("แก้รุ่น")      ||
                        msg.contains("ขอเปลี่ยน")    ||
                        msg.contains("สนใจรุ่น")     ||
                        msg.contains("เปลี่ยนเป็น");

        if (isChangeModelKeyword && !"STEP_1_WELCOME".equals(state) && !"ADMIN_MODE".equals(state)) {
            log.info("🔄 ลูกค้าขอเปลี่ยนรุ่น (keyword) จาก state: {}", state);
            userState.setCurrentState("STEP_2_CHECK_TARGET");
            userState.setDeviceModel(null);
            userStateRepository.save(userState);
            return "รับทราบครับ 😊 รบกวนแจ้งรุ่นที่สนใจใหม่ พร้อมอายุของลูกค้าด้วยนะครับ\n" +
                    "เช่น \"อายุ 25 สนใจ iPhone 16 Pro Max ครับ\" 🙏";
        }


        switch (state) {

            // ══════════════════════════════════════════════════════════
            case "STEP_1_WELCOME":
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_2_CHECK_TARGET");
                userStateRepository.save(userState);
                return "ยินดีต้อนรับสู่บริการออมดาวน์ / ออมของ ครับ 💰✨\n\n" +
                        "📌 **กติกาเบื้องต้น:**\n" +
                        "- ออมขั้นต่ำเท่าไหร่ก็ได้ เวลาไหนก็ได้ตามสะดวก\n" +
                        "- ไม่สามารถรับเงินคืนได้ทุกกรณี แต่สามารถเปลี่ยนเป็นสินค้าอื่นแทนได้ครับ\n\n" +
                        "👉 1. ลูกค้าสนใจออมเป็น iPhone รุ่นไหน (13-17) หรืออุปกรณ์เสริมอะไรครับ?\n" +
                        "👉 2. ปัจจุบันลูกค้าอายุเท่าไหร่ครับ? (รับอายุ 18-60 ปี)";

            // ══════════════════════════════════════════════════════════
            case "STEP_2_CHECK_TARGET":
                // ══════════════════════════════════════════════════════════
                log.info("🤖 ส่งข้อความให้ AI วิเคราะห์ (ออมดาวน์ STEP_2): {}", msg);
                ExtractedData extractedData = aiDataExtractorService.extractInfo(msg);

                int extractedAge   = extractedData.age() != null ? extractedData.age() : 0;
                String extractedModel = extractedData.deviceModel();

                // กรณีข้อมูลไม่ครบ — ถามซ้ำ
                if (extractedAge == 0 || extractedModel == null || "unknown".equalsIgnoreCase(extractedModel)) {
                    return "แอดมินรบกวนขอทราบ 'รุ่นที่สนใจ' และ 'อายุ' อีกครั้งให้ชัดเจนได้ไหมครับ\n" +
                            "เช่น อายุ 22 สนใจ 15 Pro Max ครับ 🙏";
                }

                if (extractedAge < 18 || extractedAge > 60) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ต้องขออภัยด้วยนะครับ 🙏 ทางร้านขอสงวนสิทธิ์ให้บริการออมดาวน์และออมของ " +
                            "เฉพาะลูกค้าที่มีอายุระหว่าง 18 - 60 ปีเท่านั้นครับผม ขอบคุณที่สนใจสอบถามนะครับ";
                }

                // บันทึกรุ่นและไปต่อ
                userState.setDeviceModel(extractedModel);
                userState.setCurrentState("STEP_3_OPEN_BILL");
                userStateRepository.save(userState);

                return "รับทราบครับ สนใจออมเป็นรุ่น **" + extractedModel + "** นะครับ 📝\n\n" +
                        "สำหรับระยะเวลาการออม จะขึ้นอยู่กับยอดดาวน์ที่ลูกค้าตั้งเป้าไว้นะครับ:\n" +
                        "🔹 ยอดดาวน์ไม่เกิน 10,000 บาท = ระยะออมสูงสุด 3 เดือน\n" +
                        "🔹 ยอดดาวน์ 10,000 - 15,000 บาท = ระยะออมสูงสุด 6 เดือน\n" +
                        "🔹 ยอดดาวน์ 15,000 บาทขึ้นไป = ระยะออมสูงสุด 1 ปี\n\n" +
                        "👉 ลูกค้าตั้งเป้ายอดดาวน์ไว้ที่ประมาณกี่บาทครับ?\n\n" +
                        "_(หากต้องการแก้ไขรุ่น พิมพ์ \"เปลี่ยนรุ่น\" ได้เลยนะครับ)_";

            case "STEP_3_OPEN_BILL":

                // 🔍 ตรวจว่าลูกค้าส่งชื่อรุ่นใหม่มาแทนตัวเลขยอดเงินหรือเปล่า
                log.info("🤖 STEP_3 ตรวจ AI หารุ่นใหม่ในข้อความ: {}", msg);
                ExtractedData step3Check = aiDataExtractorService.extractInfo(msg);

                boolean hasNewModel = step3Check.deviceModel() != null
                        && !"unknown".equalsIgnoreCase(step3Check.deviceModel());

                boolean isDifferentModel = hasNewModel
                        && !step3Check.deviceModel().equalsIgnoreCase(userState.getDeviceModel());

                if (isDifferentModel) {
                    log.info("✏️ อัปเดตรุ่นใหม่: {} → {}", userState.getDeviceModel(), step3Check.deviceModel());
                    userState.setDeviceModel(step3Check.deviceModel());
                    userStateRepository.save(userState);
                    return "รับทราบครับ ขอแก้เป็นรุ่น **" + step3Check.deviceModel() + "** นะครับ ✅\n\n" +
                            "👉 ลูกค้าตั้งเป้ายอดดาวน์ไว้ที่ประมาณกี่บาทครับ?";
                }

                userState.setCurrentState("STEP_4_HANDOFF");
                userStateRepository.save(userState);
                return "โอเคครับ ระยะเวลาการออมอยู่ในเกณฑ์ที่กำหนดครับ ✅\n\n" +
                        "เพื่อเป็นการเปิดบัญชีออมดาวน์ในระบบ...\n" +
                        "👉 วันนี้ลูกค้าสะดวก **โอนยอดเปิดบิลแรก** ที่จำนวนเงินเท่าไหร่ดีครับ? (เริ่มต้นกี่บาทก็ได้ครับ)";

            // ══════════════════════════════════════════════════════════
            case "STEP_4_HANDOFF":
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                String customerName = "ลูกค้า (ไม่ทราบชื่อ)";
                try {
                    var profile = messagingApiClient.getProfile(userId).get();
                    customerName = profile.body().displayName();
                } catch (Exception e) {
                    log.warn("❌ ไม่สามารถดึงชื่อโปรไฟล์ของ userId: {} ได้", userId);
                }

                String finalAdminCardText =
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                                "📋 แจ้งเตือน: ลูกค้ารอเปิดบิลออมดาวน์\n" +
                                "━━━━━━━━━━━━━━━━━━━━\n" +
                                "👤 ชื่อ LINE : " + customerName + "\n" +
                                "🪪 รหัส     : " + userId + "\n" +
                                "📱 รุ่นที่สนใจ: " + (userState.getDeviceModel() != null ? userState.getDeviceModel() : "-") + "\n" +
                                "💸 ยอดเปิดบิล: " + msg + " บาท\n" +
                                "━━━━━━━━━━━━━━━━━━━━\n" +
                                "👆 กรุณาติดต่อลูกค้ากลับด้วยครับ";

                List<Message> pushMessages = List.of(new TextMessage(finalAdminCardText));

                messagingApiClient.pushMessage(
                        null,
                        new PushMessageRequest(
                                ADMIN_GROUP_ID,
                                pushMessages,
                                false,
                                (List<String>) null
                        )
                );

                return "รับทราบครับ ยอดเปิดบิลแรก " + msg + " 💸\n\n" +
                        "เดี๋ยวแอดมินตัวจริงจะเข้ามาสรุปเงื่อนไข ส่งเลขบัญชี และทำตารางออมให้นะครับ รบกวนรอแอดมินสักครู่ครับ ⏳";

            // ══════════════════════════════════════════════════════════
            case "ADMIN_MODE":
                // ══════════════════════════════════════════════════════════
                return null; // บอทเงียบกริบ ให้แอดมินดูแลต่อ

            // ══════════════════════════════════════════════════════════
            default:
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_1_WELCOME");
                userStateRepository.save(userState);
                return "ระบบเริ่มการทำรายการออมดาวน์ใหม่ครับ รบกวนแจ้งรุ่นที่สนใจ และอายุของลูกค้าครับ";
        }
    }
}