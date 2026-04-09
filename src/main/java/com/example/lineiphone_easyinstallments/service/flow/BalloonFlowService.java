package com.example.lineiphone_easyinstallments.service.flow;

import com.example.lineiphone_easyinstallments.dto.ExtractedData;
import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import com.example.lineiphone_easyinstallments.service.ai.AiDataExtractorService;
import com.example.lineiphone_easyinstallments.service.ai.AiScreeningService;
import com.example.lineiphone_easyinstallments.service.ai.AiScreeningService.ScreeningAnswer;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 🎈 BalloonFlowService — ผ่อนบอลลูน
 *
 * Flow ที่ปรับใหม่ (ถามทีละข้อ แยกกันแบบ Sequential):
 * ─────────────────────────────────────────────────────
 * STEP_1_INFO              → ถามรุ่น/ความจุ
 * STEP_2_PROVINCE          → ตรวจรุ่น → ถามจังหวัด
 * STEP_3_AGE               → รับจังหวัด → ถามอายุ
 * STEP_4_REPAIR            → รับอายุ → ถามแกะซ่อม
 * STEP_5_FACEID            → ตรวจซ่อม → ถาม Face ID
 * STEP_6_INSTALLMENT       → ตรวจ Face ID → ถามติดผ่อน
 * STEP_7_PHOTOS            → ตรวจติดผ่อน → ขอรูปเครื่องและชื่อ
 * STEP_8_SUBMIT_DATA       → รับรูป → บอทแจ้งเตือน Admin โหมด
 * STEP_5_PRICING           → Admin ตั้งราคา (คงชื่อ State เดิมไว้ไม่ให้กระทบระบบอื่น)
 * STEP_6_MONTH_SELECTION   → ลูกค้าเลือกจำนวนงวด
 * ADMIN_MODE               → บอทเงียบ
 * ─────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalloonFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final LineMessageService lineMessageService;
    private final MessagingApiClient messagingApiClient;
    private final AiDataExtractorService aiDataExtractorService;
    private final AiScreeningService aiScreeningService;

    private final String ADMIN_GROUP_ID = "C75eb19ed18cf5a67a1461f785f581e78";

    public record BalloonPrice(int buyPrice, int m6, int m8, int m10, int m12) {}

    @Override
    public boolean supports(String serviceName) { return "ผ่อนบอลลูน".equals(serviceName); }

    @Override
    public String getServiceName() { return "ผ่อนบอลลูน"; }

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
            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "ผ่อนบอลลูน", getCustomerName(userId), "ลูกค้าเรียกแอดมิน");
            return "รับทราบครับ รบกวนรอแอดมินเข้ามาดูแลสักครู่นะครับ ⏳";
        }

        switch (state) {

            // ══════════════════════════════════════════════════════════
            case "STEP_1_INFO": // เริ่มต้น ถามรุ่น
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_2_PROVINCE");
                userStateRepository.save(userState);
                return "สวัสดีครับ 🙏😊 แอดมินขออนุญาตสอบถามรายละเอียดเบื้องต้นครับ\n" +
                        "👉 ลูกค้าใช้ไอโฟนรุ่นไหน ความจุกี่ GB ครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_2_PROVINCE": // ตรวจรุ่น ถามจังหวัด
                // ══════════════════════════════════════════════════════════
                log.info("🤖 [ผ่อนบอลลูน] STEP_2 AI สกัดรุ่นจาก: {}", msg);
                ExtractedData extractedData = aiDataExtractorService.extractInfo(msg);
                String extractedModel = extractedData.deviceModel();

                if (extractedModel == null || "unknown".equalsIgnoreCase(extractedModel)) {
                    return "แอดมินจับชื่อรุ่นไม่ทันเลยครับ 😅 รบกวนขอทราบ 'รุ่นไอโฟน' อีกครั้งชัดๆ ได้ไหมครับ\n" +
                            "เช่น: 13 Pro Max, 14, หรือ 15 Pro ครับ 📱";
                }

                userState.setDeviceModel(extractedModel);
                userState.setCurrentState("STEP_3_AGE");
                userStateRepository.save(userState);
                return "รับทราบครับ รุ่น **iPhone " + extractedModel + "** นะครับ! เยี่ยมเลยครับ 😊\n" +
                        "👉 ลูกค้าอยู่จังหวัดอะไรครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_3_AGE": // รับจังหวัด ถามอายุ
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_4_REPAIR");
                userStateRepository.save(userState);
                return "โอเคครับ 📍\n" +
                        "👉 แล้วลูกค้าอายุเท่าไหร่ครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_4_REPAIR": // รับอายุ ถามซ่อม
                // ══════════════════════════════════════════════════════════
                ExtractedData ageData = aiDataExtractorService.extractInfo(msg);
                Integer extractedAge = ageData.age();

                String ageWarning = "";
                // แจ้งเตือนหากอายุต่ำกว่า 18 ปี ตามเงื่อนไขเอกสาร
                if (extractedAge != null && extractedAge > 0 && extractedAge < 18) {
                    ageWarning = "⚠️ (เนื่องจากลูกค้าอายุต่ำกว่า 18 ปี ในขั้นตอนการทำสัญญาจะต้องใช้ข้อมูลผู้ปกครองที่อายุ 20 ปีขึ้นไปมาเป็นผู้ซื้อให้นะครับ)";
                }

                userState.setCurrentState("STEP_5_FACEID");
                userStateRepository.save(userState);
                return "ขอบคุณสำหรับข้อมูลครับ 🙏\n" + ageWarning +
                        "ถัดไปขออนุญาตเช็คประวัติเครื่องนิดนึงนะครับ 🔍\n" +
                        "👉 เครื่องเคยแกะซ่อม หรือเปลี่ยนชิ้นส่วนใดๆ มาไหมครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_5_FACEID": // ตรวจซ่อม ถาม FaceID
                // ══════════════════════════════════════════════════════════
                ScreeningAnswer answerRepair = aiScreeningService.interpret(msg);
                log.info("🛡️ [ผ่อนบอลลูน] STEP_5 เคยแกะซ่อม? → {}", answerRepair);

                if (answerRepair == ScreeningAnswer.YES) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ต้องขออภัยด้วยนะครับ 🙏\n" +
                            "ทางร้านขอสงวนสิทธิ์รับเครื่องที่ผ่านการแกะซ่อมหรือเปลี่ยนชิ้นส่วนครับ\n" +
                            "หากต้องการสอบถามเพิ่มเติม พิมพ์ 'แอดมิน' ได้เลยนะครับ";
                }
                if (answerRepair == ScreeningAnswer.UNCLEAR) {
                    return "ขออภัยด้วยนะครับ แอดมินยังไม่แน่ใจเลยครับ 😅\n" +
                            "รบกวนตอบให้ชัดขึ้นได้ไหมครับ เช่น 'ไม่เคยแกะเลยครับ' หรือ 'เคยเปลี่ยนจอครับ'";
                }

                userState.setCurrentState("STEP_6_INSTALLMENT");
                userStateRepository.save(userState);
                return "โอเคครับ 👍 แล้ว **Face ID (สแกนหน้า)** ใช้งานได้ปกติไหมครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_6_INSTALLMENT": // ตรวจ FaceID ถามติดผ่อน
                // ══════════════════════════════════════════════════════════
                ScreeningAnswer answerFaceId = aiScreeningService.interpret(msg);
                log.info("🛡️ [ผ่อนบอลลูน] STEP_6 Face ID ปกติ? → {}", answerFaceId);

                // NO = ไม่ปกติ (ตกเงื่อนไข)
                if (answerFaceId == ScreeningAnswer.NO) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ต้องขออภัยด้วยนะครับ 🙏\n" +
                            "ทางร้านไม่สามารถรับเครื่องที่ Face ID ใช้งานไม่ได้ครับ\n" +
                            "หากต้องการสอบถามเพิ่มเติม พิมพ์ 'แอดมิน' ได้เลยนะครับ";
                }
                if (answerFaceId == ScreeningAnswer.UNCLEAR) {
                    return "ขออภัยด้วยนะครับ 😅 รบกวนตอบให้ชัดขึ้นได้ไหมครับ\n" +
                            "เช่น 'Face ID ใช้ได้ปกติครับ' หรือ 'สแกนหน้าไม่ได้ค่ะ'";
                }

                userState.setCurrentState("STEP_7_PHOTOS");
                userStateRepository.save(userState);
                return "เยี่ยมเลยครับ 😊 แล้วเครื่องมี **ติดผ่อนค้างกับร้านอื่น หรือติดล็อค iCloud** ไหมครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_7_PHOTOS": // ตรวจติดผ่อน ขอรูป/ชื่อ
                // ══════════════════════════════════════════════════════════
                ScreeningAnswer answerInstallment = aiScreeningService.interpret(msg);
                log.info("🛡️ [ผ่อนบอลลูน] STEP_7 ติดผ่อน/iCloud? → {}", answerInstallment);

                if (answerInstallment == ScreeningAnswer.YES) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ต้องขออภัยด้วยนะครับ 🙏\n" +
                            "ทางร้านไม่สามารถรับเครื่องที่ติดผ่อนหรือมีการล็อค iCloud ครับ\n" +
                            "หากต้องการสอบถามเพิ่มเติม พิมพ์ 'แอดมิน' ได้เลยนะครับ";
                }
                if (answerInstallment == ScreeningAnswer.UNCLEAR) {
                    return "ขออภัยด้วยนะครับ 😅 รบกวนตอบให้ชัดขึ้นได้ไหมครับ\n" +
                            "เช่น 'ไม่ติดผ่อนค่ะ' หรือ 'ติดผ่อนอยู่ครับ'";
                }

                userState.setCurrentState("STEP_8_SUBMIT_DATA");
                userStateRepository.save(userState);
                return "ผ่านการตรวจสอบเบื้องต้นเรียบร้อยครับ 🎉✅\n\n" +
                        "เพื่อให้แอดมินประเมินราคาและเช็คเครดิตทีเดียวเลย รบกวนลูกค้า:\n" +
                        "📸 1. ถ่ายรูปรอบเครื่อง และแคปหน้า 'ตั้งค่า > ทั่วไป > เกี่ยวกับ'\n" +
                        "✍️ 2. พิมพ์ **ชื่อ-นามสกุล** ส่งมาให้แอดมินได้เลยครับ";

            // ══════════════════════════════════════════════════════════
            case "STEP_8_SUBMIT_DATA": // รับรูป/ชื่อ เข้า Admin Mode
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                lineMessageService.sendAdminApprovalCard(
                        ADMIN_GROUP_ID,
                        "ผ่อนบอลลูน",
                        "balloon",
                        getCustomerName(userId),
                        userId,
                        userState.getDeviceModel() != null ? "รุ่น: " + userState.getDeviceModel() : "รอประเมิน"
                );

                return "รับข้อมูลเรียบร้อยครับ 📸 แอดมินกำลังตรวจสอบสภาพเครื่องและประวัติเครดิต รบกวนรอสักครู่นะครับ ⏳";

            // ══════════════════════════════════════════════════════════
            case "STEP_5_PRICING":
                // ══════════════════════════════════════════════════════════
            {
                userState.setCurrentState("STEP_6_MONTH_SELECTION");
                userStateRepository.save(userState);

                String savedModel = userState.getDeviceModel();
                BalloonPrice price = getPriceForModel(savedModel);

                if (price == null) {
                    userState.setCurrentState("ADMIN_MODE");
                    userStateRepository.save(userState);
                    return "สำหรับรุ่นนี้ รบกวนรอแอดมินเข้ามาประเมินราคาพิเศษให้นะครับ ⏳";
                }

                return "ตรวจสอบผ่านเรียบร้อยครับ! 🎉 แอดมินขอเสนอโปรโมชั่นสำหรับ **iPhone " + savedModel + "** นะครับ\n\n" +
                        "**โปรโมชั่นบอลลูนรายเดือน:**\n" +
                        "- ยอดรับซื้อ: " + String.format("%,d", price.buyPrice()) + " บาท\n" +
                        "- ส่ง 6 เดือน: งวดละ " + String.format("%,d", price.m6()) + " บาท\n" +
                        "- ส่ง 8 เดือน: งวดละ " + String.format("%,d", price.m8()) + " บาท\n" +
                        "- ส่ง 10 เดือน: งวดละ " + String.format("%,d", price.m10()) + " บาท\n" +
                        "- ส่ง 12 เดือน: งวดละ " + String.format("%,d", price.m12()) + " บาท\n\n" +
                        "👉 ลูกค้าสนใจรับเป็นระยะเวลา **6, 8, 10 หรือ 12 เดือน** ดีครับ?";
            }

            // ══════════════════════════════════════════════════════════
            case "STEP_6_MONTH_SELECTION":
                // ══════════════════════════════════════════════════════════
            {
                boolean isValidMonth = msg.contains("6") || msg.contains("8") || msg.contains("10") || msg.contains("12")
                        || msg.contains("หก") || msg.contains("แปด") || msg.contains("สิบ") || msg.contains("สิบสอง");

                if (!isValidMonth) {
                    return "ลูกค้าสะดวกส่งงวดละกี่เดือนดีครับ? 😊\n" +
                            "มีให้เลือก: **6, 8, 10 หรือ 12 เดือน** ครับ พิมพ์ตัวเลขบอกแอดมินได้เลยครับ";
                }

                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                lineMessageService.sendEmergencyCard(
                        ADMIN_GROUP_ID, "ผ่อนบอลลูน", getCustomerName(userId), "ลูกค้าเลือก " + msg + " เดือน รอสรุปยอดโอนและเอกสาร"
                );

                return "รับทราบครับ! แอดมินได้รับข้อมูลแล้ว 📝\n" +
                        "เดี๋ยวแอดมินตัวจริงจะเข้ามาสรุปยอด แจ้งเงื่อนไข และขอเอกสารทำสัญญาให้นะครับ รบกวนรอสักครู่ครับ ⏳";
            }

            // ══════════════════════════════════════════════════════════
            case "ADMIN_MODE":
            case "REJECTED":
                // ══════════════════════════════════════════════════════════
                return null; // บอทเงียบ

            default:
                userState.setCurrentState("STEP_1_INFO");
                userStateRepository.save(userState);
                return "ระบบเริ่มการทำรายการใหม่ครับ กรุณาพิมพ์คำว่า 'ผ่อนบอลลูน' เพื่อเริ่มดำเนินการครับ";
        }
    }

    private String getCustomerName(String userId) {
        try {
            var profile = messagingApiClient.getProfile(userId).get();
            return profile.body().displayName();
        } catch (Exception e) {
            log.warn("❌ ไม่สามารถดึงชื่อโปรไฟล์ของ userId: {} ได้", userId);
            return "ลูกค้า (ไม่ทราบชื่อ)";
        }
    }

    private BalloonPrice getPriceForModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) return null;
        String normalizedModel = modelName.toLowerCase().replace("iphone ", "").trim();
        return switch (normalizedModel) {
            case "12 mini"    -> new BalloonPrice(3500,  1190,  890,  790,  690);
            case "12"         -> new BalloonPrice(4000,  1290, 1090,  890,  790);
            case "12 pro"     -> new BalloonPrice(4500,  1490, 1190,  990,  890);
            case "12 pro max" -> new BalloonPrice(5000,  1590, 1290, 1090,  990);
            case "13 mini"    -> new BalloonPrice(4500,  1490, 1190,  990,  890);
            case "13"         -> new BalloonPrice(6000,  1990, 1590, 1290, 1190);
            case "13 pro"     -> new BalloonPrice(7000,  2290, 1790, 1590, 1390);
            case "13 pro max" -> new BalloonPrice(9000,  2890, 2290, 1990, 1790);
            case "14"         -> new BalloonPrice(8000,  2550, 2050, 1750, 1550);
            case "14 plus"    -> new BalloonPrice(9000,  2850, 2250, 1950, 1750);
            case "14 pro"     -> new BalloonPrice(11000, 3550, 2750, 2350, 2150);
            case "14 pro max" -> new BalloonPrice(12000, 3850, 3050, 2550, 2350);
            case "15"         -> new BalloonPrice(11000, 3550, 2750, 2390, 2150);
            case "15 plus"    -> new BalloonPrice(12000, 3850, 3050, 2550, 2350);
            case "15 pro"     -> new BalloonPrice(13000, 4190, 3290, 2790, 2490);
            case "15 pro max" -> new BalloonPrice(15000, 4790, 3790, 3290, 2890);
            case "16e"        -> new BalloonPrice(10000, 3190, 2590, 2190, 1990);
            case "16"         -> new BalloonPrice(13000, 4190, 3290, 2790, 2490);
            case "16 plus"    -> new BalloonPrice(15000, 4790, 3750, 3290, 2890);
            case "16 pro"     -> new BalloonPrice(17000, 5390, 4290, 3690, 3290);
            case "16 pro max" -> new BalloonPrice(20000, 6390, 4990, 4290, 3890);
            case "17"         -> new BalloonPrice(16000, 5090, 3990, 3490, 3090);
            case "17 air"     -> new BalloonPrice(20000, 6390, 4990, 4390, 3890);
            case "17 pro"     -> new BalloonPrice(22000, 6990, 5490, 4790, 4290);
            case "17 pro max" -> new BalloonPrice(25000, 7990, 6290, 5390, 4790);
            default -> null;
        };
    }
}