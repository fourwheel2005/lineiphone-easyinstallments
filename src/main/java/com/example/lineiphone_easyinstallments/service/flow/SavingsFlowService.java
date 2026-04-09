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

/**
 * 💰 SavingsFlowService — ออมดาวน์
 *
 * Flow ที่ปรับใหม่ (แยกถามทีละข้อแบบ Sequential):
 * ─────────────────────────────────────────────────────
 * STEP_1_MODEL       → แจ้งกติกา / ถามรุ่น
 * STEP_2_AGE         → สกัดรุ่น / ถามอายุ
 * STEP_3_TARGET      → สกัดอายุ / เช็คอายุ 18-60 / ถามเป้ายอดดาวน์
 * STEP_4_FIRST_BILL  → รับเป้ายอดดาวน์ / ถามยอดเปิดบิลแรก
 * STEP_5_HANDOFF     → รับยอดเปิดบิล / ส่งข้อมูลเข้ากลุ่ม Admin
 * ADMIN_MODE         → บอทเงียบ
 * ─────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final LineMessageService lineMessageService;
    private final AiDataExtractorService aiDataExtractorService;
    private final MessagingApiClient messagingApiClient;

    private final String ADMIN_GROUP_ID = "Cecd7b53a894a159778c0555284c943d5";

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
        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_MODEL";
        String msg = userMessage.trim();
        String userId = userState.getLineUserId();

        // 🚨 ทางออกฉุกเฉิน
        if (msg.contains("แอดมิน") || msg.contains("คุยกับคน") || msg.contains("คืนเงิน")) {
            userState.setPreviousState(state);
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);
            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "ออมดาวน์", userId, "ลูกค้าเรียกแอดมิน/สอบถามเรื่องคืนเงิน");
            return "รับทราบครับ รบกวนรอแอดมินเข้ามาดูแลและให้คำปรึกษาสักครู่นะครับ ⏳";
        }

        // ─────────────────────────────────────────────────────────────
        // ✅ GLOBAL: ดัก Keyword ขอเปลี่ยนรุ่น
        // ─────────────────────────────────────────────────────────────
        boolean isChangeModelKeyword =
                msg.contains("เปลี่ยนรุ่น") ||
                        msg.contains("แก้รุ่น")      ||
                        msg.contains("ขอเปลี่ยน")    ||
                        msg.contains("สนใจรุ่น")     ||
                        msg.contains("เปลี่ยนเป็น");

        if (isChangeModelKeyword && !"STEP_1_MODEL".equals(state) && !"ADMIN_MODE".equals(state)) {
            log.info("🔄 ลูกค้าขอเปลี่ยนรุ่น (keyword) จาก state: {}", state);
            // ให้ย้อนกลับไปสเต็ป 2 เพื่อรอรับ "ชื่อรุ่น" อีกครั้ง
            userState.setCurrentState("STEP_2_AGE");
            userState.setDeviceModel(null);
            userStateRepository.save(userState);
            return "รับทราบครับ 😊 รบกวนแจ้ง **รุ่นที่สนใจ** ใหม่อีกครั้งให้แอดมินหน่อยครับ 📱";
        }

        switch (state) {

            // ══════════════════════════════════════════════════════════
            case "STEP_1_MODEL":
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_2_AGE");
                userStateRepository.save(userState);
                return "ยินดีต้อนรับสู่บริการออมดาวน์ / ออมของ ครับ 💰✨\n\n" +
                        "📌 **กติกาเบื้องต้น:**\n" +
                        "- ออมขั้นต่ำเท่าไหร่ก็ได้ เวลาไหนก็ได้ตามสะดวก\n" +
                        "- ไม่สามารถรับเงินคืนได้ทุกกรณี แต่สามารถเปลี่ยนเป็นสินค้าอื่นแทนได้ครับ\n\n" +
                        "👉 ลูกค้าสนใจออมเป็น iPhone **รุ่นไหน** (13-17) หรืออุปกรณ์เสริมอะไรครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_2_AGE":
                // ══════════════════════════════════════════════════════════
                log.info("🤖 ส่งข้อความให้ AI วิเคราะห์หารุ่น (ออมดาวน์ STEP_2): {}", msg);
                ExtractedData extractedModelData = aiDataExtractorService.extractInfo(msg);
                String extractedModel = extractedModelData.deviceModel();

                if (extractedModel == null || "unknown".equalsIgnoreCase(extractedModel)) {
                    return "แอดมินจับชื่อรุ่นไม่ทันครับ 😅 รบกวนขอทราบ 'รุ่นไอโฟน' หรือ 'สินค้า' อีกครั้งชัดๆ ได้ไหมครับ\n" +
                            "เช่น 15 Pro Max ครับ 🙏";
                }

                userState.setDeviceModel(extractedModel);
                userState.setCurrentState("STEP_3_TARGET");
                userStateRepository.save(userState);

                return "รับทราบครับ สนใจออมเป็นรุ่น **" + extractedModel + "** นะครับ 📝\n\n" +
                        "👉 ปัจจุบันลูกค้า **อายุ** เท่าไหร่ครับ? (รับอายุ 18-60 ปี)";

            // ══════════════════════════════════════════════════════════
            case "STEP_3_TARGET":
                // ══════════════════════════════════════════════════════════
                log.info("🤖 ส่งข้อความให้ AI วิเคราะห์หาอายุ (ออมดาวน์ STEP_3): {}", msg);
                ExtractedData extractedAgeData = aiDataExtractorService.extractInfo(msg);
                int extractedAge = extractedAgeData.age() != null ? extractedAgeData.age() : 0;

                if (extractedAge == 0) {
                    return "แอดมินรบกวนขอทราบ **อายุ** ของลูกค้าอีกครั้งนะครับ พิมพ์แค่ตัวเลขก็ได้ครับ 🙏";
                }

                if (extractedAge < 18 || extractedAge > 60) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ต้องขออภัยด้วยนะครับ 🙏 ทางร้านขอสงวนสิทธิ์ให้บริการออมดาวน์และออมของ " +
                            "เฉพาะลูกค้าที่มีอายุระหว่าง 18 - 60 ปีเท่านั้นครับผม ขอบคุณที่สนใจสอบถามนะครับ";
                }

                userState.setCurrentState("STEP_4_FIRST_BILL");
                userStateRepository.save(userState);

                return "อายุ " + extractedAge + " ปี ผ่านเกณฑ์ครับ ✅\n\n" +
                        "สำหรับระยะเวลาการออม จะขึ้นอยู่กับยอดดาวน์ที่ลูกค้าตั้งเป้าไว้นะครับ:\n" +
                        "🔹 ยอดดาวน์ไม่เกิน 10,000 บาท = ระยะออมสูงสุด 3 เดือน\n" +
                        "🔹 ยอดดาวน์ 10,000 - 15,000 บาท = ระยะออมสูงสุด 6 เดือน\n" +
                        "🔹 ยอดดาวน์ 15,000 บาทขึ้นไป = ระยะออมสูงสุด 1 ปี\n\n" +
                        "👉 ลูกค้า **ตั้งเป้ายอดดาวน์** ไว้ที่ประมาณกี่บาทครับ?\n\n" +
                        "_(หากต้องการแก้ไขรุ่น พิมพ์ \"เปลี่ยนรุ่น\" ได้เลยนะครับ)_";

            // ══════════════════════════════════════════════════════════
            case "STEP_4_FIRST_BILL":
                // ══════════════════════════════════════════════════════════
                // 🔍 ตรวจเผื่อลูกค้าพิมพ์ชื่อรุ่นแทรกมาแทนที่จะเป็นตัวเลขยอดเป้าหมาย
                ExtractedData step4Check = aiDataExtractorService.extractInfo(msg);
                boolean hasNewModel = step4Check.deviceModel() != null && !"unknown".equalsIgnoreCase(step4Check.deviceModel());
                boolean isDifferentModel = hasNewModel && !step4Check.deviceModel().equalsIgnoreCase(userState.getDeviceModel());

                if (isDifferentModel) {
                    log.info("✏️ อัปเดตรุ่นใหม่ใน STEP_4: {} → {}", userState.getDeviceModel(), step4Check.deviceModel());
                    userState.setDeviceModel(step4Check.deviceModel());
                    userStateRepository.save(userState);
                    return "รับทราบครับ ขอแก้เป็นรุ่น **" + step4Check.deviceModel() + "** นะครับ ✅\n\n" +
                            "👉 ลูกค้าตั้งเป้ายอดดาวน์ไว้ที่ประมาณกี่บาทครับ?";
                }

                userState.setCurrentState("STEP_5_HANDOFF");
                userStateRepository.save(userState);
                return "โอเคครับ ระยะเวลาการออมอยู่ในเกณฑ์ที่กำหนดครับ ✅\n\n" +
                        "เพื่อเป็นการเปิดบัญชีออมดาวน์ในระบบ...\n" +
                        "👉 วันนี้ลูกค้าสะดวก **โอนยอดเปิดบิลแรก** ที่จำนวนเงินเท่าไหร่ดีครับ? (เริ่มต้นกี่บาทก็ได้ครับ)";

            // ══════════════════════════════════════════════════════════
            case "STEP_5_HANDOFF":
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
            case "REJECTED":
                // ══════════════════════════════════════════════════════════
                return null; // บอทเงียบกริบ ให้แอดมินดูแลต่อ

            // ══════════════════════════════════════════════════════════
            default:
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_1_MODEL");
                userStateRepository.save(userState);
                return "ระบบเริ่มการทำรายการออมดาวน์ใหม่ครับ พิมพ์อะไรก็ได้เพื่อเริ่มพูดคุยครับ";
        }
    }
}