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

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeInFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final LineMessageService lineMessageService;
    private final MessagingApiClient messagingApiClient;
    private final AiDataExtractorService aiDataExtractorService;
    private final AiScreeningService aiScreeningService;

    private final String ADMIN_GROUP_ID = "Cef2ceeeb8154fbd5dd92d294d467ecd4";
    private final String EXAMPLE_SETTINGS_IMG_URL = "https://raw.githubusercontent.com/fourwheel2005/image/main/S__8298515.jpg";

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
        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_MODEL";
        String msg = userMessage.trim();
        String userId = userState.getLineUserId();

        // 🚨 ทางออกฉุกเฉิน
        boolean isPanic = msg.contains("แอดมิน") || msg.contains("คุยกับคน") ||
                msg.contains("อ่านดีๆ") || msg.contains("บอกไปแล้ว") ||
                msg.contains("บอท") || msg.contains("ไม่รู้เรื่อง");

        if (isPanic) {
            userState.setPreviousState(state);
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);

            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, getServiceName(), getCustomerName(userId), "ลูกค้าต้องการคุยกับคน หรือเกิดความหงุดหงิดบอท");
            return "รับทราบครับ แอดมินขออภัยในความไม่สะดวกนะครับ 🙏 เดี๋ยวแอดมินตัวจริงรีบเข้ามาดูแลเคสนี้ให้ทันที รบกวนรอสักครู่นะครับ ⏳";
        }

        switch (state) {

            // ══════════════════════════════════════════════════════════
            case "STEP_1_MODEL": // เริ่มต้น -> ถามรุ่น/ความจุ
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_2_ACCESSORIES");
                userStateRepository.save(userState);
                return "ยินดีต้อนรับสู่บริการรับซื้อไอโฟนครับ ให้ราคาสูงแน่นอน! 💸\n\n" +
                        "เริ่มต้นรบกวนลูกค้าแจ้ง **รุ่นไอโฟน และ ความจุ (GB)** ที่ต้องการขายให้แอดมินหน่อยครับ 📱\n" +
                        "(เช่น 13 Pro 256GB หรือ 14 128GB)";

            // ══════════════════════════════════════════════════════════
            case "STEP_2_ACCESSORIES": // สกัดรุ่น -> ถามอุปกรณ์
                // ══════════════════════════════════════════════════════════
                ExtractedData extractedData = aiDataExtractorService.extractInfo(msg, userState.getLastUserMessage());
                String extractedModel = extractedData.deviceModel();
                userState.setCapacity(extractedData.capacity());

                if (extractedModel == null || "unknown".equalsIgnoreCase(extractedModel)) {
                    return "แอดมินจับชื่อรุ่นไม่ทันเลยครับ 😅 รบกวนขอทราบ 'รุ่นไอโฟน' อีกครั้งชัดๆ ได้ไหมครับ";
                }

                userState.setDeviceModel(extractedModel); // บันทึกรุ่นลง DB
                userState.setCurrentState("STEP_3_BATTERY");
                userStateRepository.save(userState);
                return "รับทราบครับ เป็นรุ่น **iPhone " + extractedModel + "** นะครับ! เยี่ยมเลยครับ 😊\n\n" +
                        "👉 สำหรับรุ่นนี้ ลูกค้ามี **กล่อง และ สายชาร์จแท้** มาด้วยไหมครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_3_BATTERY": // สกัดอุปกรณ์ -> ถามแบต
                // ══════════════════════════════════════════════════════════
                ExtractedData accData = aiDataExtractorService.extractInfo(msg, userState.getLastUserMessage());
                userState.setAccessories(accData.accessories());
                // หมายเหตุ: ตรงนี้อาจจะเก็บค่าลง userState ได้ เช่น userState.setHasBox(answerAccessories == ScreeningAnswer.YES);

                userState.setCurrentState("STEP_4_REPAIR");
                userStateRepository.save(userState);
                return "โอเคครับ รับทราบข้อมูลอุปกรณ์ครับ 📦\n\n" +
                        "👉 ถัดไปรบกวนเช็ค **สุขภาพแบตเตอรี่ (Battery Health)** ให้แอดมินหน่อยครับว่าตอนนี้เหลือกี่เปอร์เซ็นต์? 🔋";

            // ══════════════════════════════════════════════════════════
            case "STEP_4_REPAIR": // สกัดแบต -> ถามประวัติซ่อม
                // ══════════════════════════════════════════════════════════
                ExtractedData batteryData = aiDataExtractorService.extractInfo(msg, userState.getLastUserMessage());
                userState.setBatteryHealth(batteryData.batteryHealth()); // 👈 เพิ่มบรรทัดนี้

                userState.setCurrentState("STEP_5_DEVICE_PHOTOS");
                userStateRepository.save(userState);
                return "ขอบคุณครับ 🙏 ขอสอบถามประวัติเครื่องนิดนึงนะครับ 🔍\n\n" +
                        "👉 เครื่องนี้ **เคยผ่านการแกะซ่อม หรือเปลี่ยนชิ้นส่วน** ใดๆ มาก่อนไหมครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_5_DEVICE_PHOTOS": // สกัดซ่อม -> ขอรูปรอบเครื่อง
                // ══════════════════════════════════════════════════════════
                ExtractedData repairData = aiDataExtractorService.extractInfo(msg, userState.getLastUserMessage());
                userState.setRepairHistory(repairData.repairHistory()); // 👈 เพิ่มบรรทัดนี้

                userState.setCurrentState("STEP_6_SETTINGS_PHOTO");
                userStateRepository.save(userState);
                return "ข้อมูลเบื้องต้นครบถ้วนครับ! 🎉\n\n" +
                        "เพื่อให้แอดมินตีราคาได้แม่นยำ รบกวนลูกค้า:\n" +
                        "📸 **ถ่ายรูปรอบเครื่อง** (หน้า, หลัง, ขอบมุม) ส่งมาในแชทนี้ได้เลยครับ (สามารถถ่ายผ่านกระจกเงาได้ครับ)\n" +
                        "📌 **(ถ้าส่งรูปครบแล้ว รบกวนพิมพ์คำว่า 'ครบ' ให้แอดมินหน่อยนะครับ)**";

            // ══════════════════════════════════════════════════════════
            case "STEP_6_SETTINGS_PHOTO": // รอรับคำว่า "ครบ" -> ขอรูปตั้งค่า
                // ══════════════════════════════════════════════════════════
                // ทริคป้องกัน Webhook เด้งรัวๆ: ถ้าระบบส่งคำว่า รูปภาพ/Image มา แปลว่าลูกค้ากำลังทยอยส่งรูป ให้บอทเงียบไว้
                if (msg.contains("รูปภาพ") || msg.contains("image") || msg.contains("photo")) {
                    return null; // เงียบ ปล่อยให้ลูกค้าอัปโหลดรูป
                }

                // ถ้าลูกค้ายังไม่พิมพ์คำที่สื่อว่าส่งครบแล้ว ให้ทวงถามเบาๆ
                if (!msg.contains("ครบ") && !msg.contains("เสร็จ") && !msg.contains("ส่งแล้ว")) {
                    return "ถ้าส่งรูปรอบเครื่องครบแล้ว รบกวนพิมพ์คำว่า **'ครบ'** ให้แอดมินหน่อยนะครับ แอดมินจะได้ไปขั้นตอนสุดท้ายครับ 😊";
                }

                userState.setCurrentState("STEP_7_SUBMIT_DATA");
                userStateRepository.save(userState);

                // 1. ส่งรูปภาพตัวอย่าง
                lineMessageService.sendImage(userId, EXAMPLE_SETTINGS_IMG_URL);

                // 2. ส่งข้อความขอรูปตั้งค่า
                return "สวยงามครับ! ✨\n" +
                        "👉 ขั้นตอนสุดท้าย รบกวนลูกค้า **แคปหน้าจอ 'ตั้งค่า > ทั่วไป > เกี่ยวกับ'**\n" +
                        "(ตามรูปตัวอย่างด้านบน ☝️) ส่งมาให้แอดมินดูรุ่นและความจุที่แน่นอนหน่อยครับ\n\n" +
                        "📌 **(ส่งรูปสุดท้ายเสร็จแล้ว พิมพ์คำว่า 'เสร็จ' ให้แอดมินได้เลยครับ)**";

            // ══════════════════════════════════════════════════════════
            case "STEP_7_SUBMIT_DATA": // รอรับคำว่า "เสร็จ" -> จบงานส่ง Admin
                // ══════════════════════════════════════════════════════════
                if (msg.contains("รูปภาพ") || msg.contains("image") || msg.contains("photo")) {
                    return null; // ลูกค้ากำลังส่งรูปหน้าตั้งค่า บอทเงียบไว้
                }

                if (!msg.contains("เสร็จ") && !msg.contains("เรียบร้อย") && !msg.contains("ครบ")) {
                    return "ส่งรูปตั้งค่าแล้ว พิมพ์คำว่า **'เสร็จ'** ได้เลยนะครับ แอดมินรอประเมินราคาให้อยู่ครับ 💸";
                }

                // จบ Flow ส่งเข้าโหมดแอดมิน
                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                String displayLineName = getCustomerName(userId);
                String model = (userState.getDeviceModel() != null && !userState.getDeviceModel().equals("unknown")) ? userState.getDeviceModel() : "ไม่ระบุ";
                String capacity = (userState.getCapacity() != null && !userState.getCapacity().equals("unknown")) ? userState.getCapacity() : "";
                String battery = (userState.getBatteryHealth() != null && userState.getBatteryHealth() > 0) ? userState.getBatteryHealth() + "%" : "ไม่ระบุ";
                String acc = (userState.getAccessories() != null && !userState.getAccessories().equals("unknown")) ? userState.getAccessories() : "ไม่ระบุ";
                String repair = (userState.getRepairHistory() != null && !userState.getRepairHistory().equals("unknown")) ? userState.getRepairHistory() : "ไม่ระบุ";

                String summaryDetails = String.format("📱 รุ่น: %s %s\n🔋 แบต: %s\n📦 อุปกรณ์: %s\n🔧 ประวัติ: %s",
                        model, capacity, battery, acc, repair);

                lineMessageService.sendAdminApprovalCard(
                        ADMIN_GROUP_ID,
                        "รับซื้อไอโฟน",
                        "trade-in",
                        displayLineName,
                        userId,
                        summaryDetails // 👈 ส่งข้อความที่สรุปแล้วลงไปในการ์ด
                );

                return "ได้รับข้อมูลและรูปภาพครบถ้วนเรียบร้อยครับ 📸✅\n" +
                        "แอดมินกำลังนำข้อมูลทั้งหมดไปประเมินราคาให้ รบกวนรอสักครู่นะครับ ⏳";

            // ══════════════════════════════════════════════════════════
            case "ADMIN_MODE":
                // ══════════════════════════════════════════════════════════
                return null; // บอทเงียบ

            default:
                userState.setCurrentState("STEP_1_MODEL");
                userStateRepository.save(userState);
                return "ระบบเริ่มการทำรายการใหม่ครับ พิมพ์ 'รับซื้อไอโฟน' เพื่อเริ่มประเมินราคาได้เลยครับ";
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