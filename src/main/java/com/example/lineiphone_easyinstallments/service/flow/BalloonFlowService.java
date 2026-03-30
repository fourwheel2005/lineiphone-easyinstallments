package com.example.lineiphone_easyinstallments.service.flow;

import com.example.lineiphone_easyinstallments.dto.ExtractedData;
import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import com.example.lineiphone_easyinstallments.service.ai.AiDataExtractorService;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalloonFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final LineMessageService lineMessageService;
    private final MessagingApiClient messagingApiClient;
    private final AiDataExtractorService aiDataExtractorService;

    private final String ADMIN_GROUP_ID = "C6ba4e7610168308c16d6213243d80f3f";

    public record BalloonPrice(int buyPrice, int m6, int m8, int m10, int m12) {}

    @Override
    public boolean supports(String serviceName) {
        return "ผ่อนบอลลูน".equals(serviceName);
    }

    @Override
    public String getServiceName() {
        return "ผ่อนบอลลูน";
    }

    @Override
    public String processMessage(UserState userState, String userMessage) {
        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_INFO";
        String msg = userMessage.trim();
        String userId = userState.getLineUserId();

        // 🚨 ทางออกฉุกเฉิน: ลูกค้าพิมพ์เรียกแอดมิน
        if (msg.contains("แอดมิน") || msg.contains("คุยกับคน")) {
            userState.setPreviousState(state); // จำ State เก่าไว้เผื่อคืนร่าง
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);

            // ✅ เปลี่ยนมาใช้ Helper Method ดึงชื่อลูกค้า
            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "ผ่อนบอลลูน", getCustomerName(userId), "ลูกค้าเรียกแอดมิน");
            return "รับทราบครับ รบกวนรอแอดมินเข้ามาดูแลสักครู่นะครับ ⏳";
        }

        switch (state) {

            case "STEP_1_INFO":
                userState.setCurrentState("STEP_2_CONDITION");
                userStateRepository.save(userState);
                return "สวัสดีครับ 🙏😊 แอดมินขออนุญาตสอบถามรายละเอียดเบื้องต้นครับ\n" +
                        "👉 1. ลูกค้าใช้ไอโฟนรุ่นไหน ความจุกี่ GB ครับ?\n" +
                        "👉 2. ลูกค้าอยู่จังหวัดอะไร?\n" +
                        "👉 3. ลูกค้าอายุเท่าไหร่ครับ?";

            case "STEP_2_CONDITION":
                log.info("🤖 ส่งข้อความให้ AI วิเคราะห์: {}", msg);
                ExtractedData extractedData = aiDataExtractorService.extractInfo(msg);
                String extractedModel = extractedData.deviceModel();

                if (extractedModel == null || extractedModel.trim().isEmpty() || "unknown".equalsIgnoreCase(extractedModel)) {
                    return "แอดมินจับชื่อรุ่นไม่ทันเลยครับ 😅 รบกวนขอทราบ 'รุ่นไอโฟน' อีกครั้งชัดๆ ได้ไหมครับ เช่น 13 Pro Max, 14, หรือ 15 Pro ครับ 📱";
                }

                userState.setDeviceModel(extractedModel);
                userState.setCurrentState("STEP_3_PHOTOS_AND_NAME");
                userStateRepository.save(userState);

                return "รับทราบครับ รุ่น **iPhone " + extractedModel + "** นะครับ! เยี่ยมเลยครับ ถัดไปขออนุญาตเช็คประวัติเครื่องนิดนึงนะครับ 😊\n" +
                        "👉 เครื่องเคยแกะซ่อมไหมครับ?\n" +
                        "👉 สแกนหน้า (Face ID) ใช้ได้ปกติไหมครับ?\n" +
                        "👉 เครื่องติดผ่อนร้านอื่น หรือติดล็อค iCloud ไหมครับ?";

            case "STEP_3_PHOTOS_AND_NAME":
                if (msg.contains("ติดผ่อน") || msg.contains("ซ่อมบอร์ด") || msg.contains("สแกนหน้าไม่ได้")) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ต้องขออภัยด้วยนะครับ 🙏 ทางร้านไม่สามารถรับเครื่องที่ติดผ่อน หรือสแกนหน้าไม่ได้ครับผม";
                }

                userState.setCurrentState("STEP_4_WAITING_ADMIN_REVIEW");
                userStateRepository.save(userState);
                return "โอเคครับ 👌\n" +
                        "เพื่อให้แอดมินประเมินราคาและเช็คเครดิตทีเดียวเลย รบกวนลูกค้า:\n" +
                        "📸 1. ถ่ายรูปรอบเครื่อง และแคปหน้า 'ตั้งค่า > ทั่วไป > เกี่ยวกับ'\n" +
                        "✍️ 2. พิมพ์ **ชื่อ-นามสกุล** ส่งมาให้แอดมินได้เลยครับ";

            case "STEP_4_WAITING_ADMIN_REVIEW":
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

            case "STEP_5_PRICING":
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
                        "👉 ลูกค้าสนใจรับเป็นระยะเวลา 6, 8, 10 หรือ 12 เดือนดีครับ?";

            case "STEP_6_MONTH_SELECTION":
                if (!msg.contains("6") && !msg.contains("8") && !msg.contains("10") && !msg.contains("12")) {
                    return "ลูกค้ารับเป็นระยะเวลา 6, 8, 10 หรือ 12 เดือนดีครับ? พิมพ์ตัวเลขบอกแอดมินได้เลยครับ";
                }

                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                // ✅ เปลี่ยนมาใช้ Helper Method ดึงชื่อลูกค้า
                lineMessageService.sendEmergencyCard(
                        ADMIN_GROUP_ID, "ผ่อนบอลลูน", getCustomerName(userId), "ลูกค้าเลือกเดือนแล้ว รอสรุปยอดโอนและเอกสาร"
                );

                return "รับทราบครับ! แอดมินได้รับข้อมูลแล้ว 📝\nเดี๋ยวแอดมินตัวจริงจะเข้ามาสรุปยอด แจ้งเงื่อนไข และขอเอกสารทำสัญญาให้นะครับ รบกวนรอสักครู่ครับ ⏳";

            case "ADMIN_MODE":
                return "";

            default:
                userState.setCurrentState("STEP_1_INFO");
                userStateRepository.save(userState);
                return "ระบบเริ่มการทำรายการใหม่ครับ กรุณาแจ้งรุ่นไอโฟน ความจุ จังหวัด และอายุครับ";
        }
    }

    /**
     * Helper Method: ฟังก์ชันช่วยดึงชื่อ Display Name ของลูกค้าจาก LINE API
     */
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
            case "12 mini" -> new BalloonPrice(3500, 1190, 890, 790, 690);
            case "12" -> new BalloonPrice(4000, 1290, 1090, 890, 790);
            case "12 pro" -> new BalloonPrice(4500, 1490, 1190, 990, 890);
            case "12 pro max" -> new BalloonPrice(5000, 1590, 1290, 1090, 990);
            case "13 mini" -> new BalloonPrice(4500, 1490, 1190, 990, 890);
            case "13" -> new BalloonPrice(6000, 1990, 1590, 1290, 1190);
            case "13 pro" -> new BalloonPrice(7000, 2290, 1790, 1590, 1390);
            case "13 pro max" -> new BalloonPrice(9000, 2890, 2290, 1990, 1790);
            case "14" -> new BalloonPrice(8000, 2550, 2050, 1750, 1550);
            case "14 plus" -> new BalloonPrice(9000, 2850, 2250, 1950, 1750);
            case "14 pro" -> new BalloonPrice(11000, 3550, 2750, 2350, 2150);
            case "14 pro max" -> new BalloonPrice(12000, 3850, 3050, 2550, 2350);
            case "15" -> new BalloonPrice(11000, 3550, 2750, 2390, 2150);
            case "15 plus" -> new BalloonPrice(12000, 3850, 3050, 2550, 2350);
            case "15 pro" -> new BalloonPrice(13000, 4190, 3290, 2790, 2490);
            case "15 pro max" -> new BalloonPrice(15000, 4790, 3790, 3290, 2890);
            case "16e" -> new BalloonPrice(10000, 3190, 2590, 2190, 1990);
            case "16" -> new BalloonPrice(13000, 4190, 3290, 2790, 2490);
            case "16 plus" -> new BalloonPrice(15000, 4790, 3750, 3290, 2890);
            case "16 pro" -> new BalloonPrice(17000, 5390, 4290, 3690, 3290);
            case "16 pro max" -> new BalloonPrice(20000, 6390, 4990, 4290, 3890);
            case "17" -> new BalloonPrice(16000, 5090, 3990, 3490, 3090);
            case "17 air" -> new BalloonPrice(20000, 6390, 4990, 4390, 3890);
            case "17 pro" -> new BalloonPrice(22000, 6990, 5490, 4790, 4290);
            case "17 pro max" -> new BalloonPrice(25000, 7990, 6290, 5390, 4790);
            default -> null;
        };
    }
}