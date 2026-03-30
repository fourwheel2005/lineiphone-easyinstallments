package com.example.lineiphone_easyinstallments.service.flow;

import com.example.lineiphone_easyinstallments.dto.ExtractedData;
import com.example.lineiphone_easyinstallments.entity.PromotionPrice;
import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.PromotionPriceRepository;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import com.example.lineiphone_easyinstallments.service.ai.AiDataExtractorService;
import com.linecorp.bot.messaging.client.MessagingApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EasyInstallmentFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final AiDataExtractorService aiDataExtractorService;
    private final PromotionPriceRepository promotionPriceRepository;
    private final LineMessageService lineMessageService;
    private final MessagingApiClient messagingApiClient;

    private final String ADMIN_GROUP_ID = "C8bc47379aa0a51b07e2e0ce58de79aa7";

    @Override
    public boolean supports(String serviceName) {
        return "ไอโฟนผ่อนง่าย".equals(serviceName);
    }

    @Override
    public String getServiceName() {
        return "ไอโฟนผ่อนง่าย";
    }

    @Override
    public String processMessage(UserState userState, String userMessage) {

        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_SELECT_MODEL";
        String msg = userMessage.trim();
        String userId = userState.getLineUserId();

        // 🚨 ทางออกฉุกเฉิน: ลูกค้าพิมพ์เรียกแอดมิน
        if (msg.contains("แอดมิน") || msg.contains("คุยกับคน")) {
            userState.setPreviousState(state);
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);

            // ✅ ดึงชื่อลูกค้าเฉพาะตอนจะส่งการ์ด
            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "ไอโฟนผ่อนง่าย", getCustomerName(userId), "ลูกค้าเรียกแอดมิน");
            return "รับทราบครับ รบกวนรอแอดมินเข้ามาดูแลสักครู่นะครับ ⏳";
        }

        switch (state) {

            case "STEP_1_SELECT_MODEL":
                userState.setCurrentState("STEP_2_CHECK_PRICE_AND_QUALIFICATION");
                userStateRepository.save(userState);
                return "ยินดีต้อนรับสู่บริการไอโฟนผ่อนง่ายครับ ✨ อนุมัติไว ไม่ยุ่งยาก!\n\n" +
                        "เพื่อเช็คโปรโมชั่นที่คุ้มที่สุด รบกวนลูกค้าแจ้ง:\n" +
                        "👉 1. สนใจ **รุ่นไหน**\n" +
                        "👉 2. **ความจุ** กี่ GB\n" +
                        "👉 3. รับเป็น **มือ 1 หรือ มือ 2** ดีครับ?\n" +
                        "(พิมพ์ตอบรวมกันได้เลยครับ เช่น 15 promax 256 มือ 1)";

            case "STEP_2_CHECK_PRICE_AND_QUALIFICATION":
                log.info("🤖 ส่งข้อความให้ AI วิเคราะห์ (ผ่อนง่าย): {}", msg);
                ExtractedData extracted = aiDataExtractorService.extractInfo(msg);

                String model = extracted.deviceModel();
                String capacity = extracted.capacity();
                String condition = extracted.condition();

                if ("unknown".equalsIgnoreCase(model) || "unknown".equalsIgnoreCase(capacity) || "unknown".equalsIgnoreCase(condition)) {
                    return "แอดมินรบกวนขอข้อมูลเพิ่มเติมอีกนิดนะครับ 🙏 \n" +
                            "ช่วยระบุให้ครบทั้ง **'รุ่น', 'ความจุ' และ 'มือ 1/มือ 2'** ให้แอดมินหน่อยนะครับ (เช่น 15 Pro Max 256GB มือ 1) 📱";
                }

                Optional<PromotionPrice> priceOpt = promotionPriceRepository.findByModelAndCapacityAndCondition(model, capacity, condition);

                if (priceOpt.isEmpty()) {
                    userState.setCurrentState("ADMIN_MODE");
                    userStateRepository.save(userState);

                    // ✅ ดึงชื่อลูกค้าเฉพาะตอนจะส่งการ์ด
                    lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "ไอโฟนผ่อนง่าย", getCustomerName(userId), "ค้นหาราคาไม่เจอ: " + model + " " + capacity + " (" + condition + ")");
                    return "สำหรับรุ่น **" + model + " " + capacity + " (" + condition + ")** ตอนนี้โปรโมชั่นอาจมีการเปลี่ยนแปลง หรือสินค้าอาจหมดชั่วคราว \n\nเดี๋ยวแอดมินตัวจริงรีบเช็คสต๊อกและราคาพิเศษให้นะครับ รบกวนรอสักครู่ครับ ⏳";
                }

                PromotionPrice price = priceOpt.get();
                userState.setDeviceModel(model);
                userState.setCapacity(capacity);
                userState.setCondition(condition);
                userState.setCurrentState("STEP_3_REQUEST_DOCS");
                userStateRepository.save(userState);

                return "เช็คราคาให้แล้วครับ! 🎉 สำหรับ **iPhone " + model + " " + capacity + " (" + condition + ")**\n\n" +
                        buildPriceMessage(price) + "\n\n" +
                        "🎁 **แถมฟรี:** ฟิล์มกันรอย, เคส, ฟิล์มกระจกเลนส์กล้อง\n\n" +
                        "👉 เพื่อพิจารณายอดผ่อน รบกวนขอทราบ **อายุ** และ **อาชีพปัจจุบัน** ของลูกค้าหน่อยนะครับ 💼";

            case "STEP_3_REQUEST_DOCS":
                userState.setCurrentState("STEP_4_WAITING_APPROVAL");
                userStateRepository.save(userState);
                return "โปรไฟล์เบื้องต้นโอเคเลยครับ! 🚀\n" +
                        "การผ่อนกับร้านเรา **ไม่เช็คบูโร** ขอแค่อายุ 18-55 ปี และมีรายได้ครับ\n\n" +
                        "📸 รบกวนลูกค้าถ่ายรูป **หน้าบัตรประชาชน** และพิมพ์ **ชื่อ-นามสกุล** ส่งมาในแชทนี้ได้เลยครับ แอดมินจะรีบทำเรื่องอนุมัติให้ทันทีครับ";

            case "STEP_4_WAITING_APPROVAL":
                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                String fullDeviceName = userState.getDeviceModel() + " " + userState.getCapacity() + " (" + userState.getCondition() + ")";

                // ✅ ดึงชื่อลูกค้าเฉพาะตอนจะส่งการ์ด
                lineMessageService.sendAdminApprovalCard(
                        ADMIN_GROUP_ID,
                        "ไอโฟนผ่อนง่าย",
                        "easy_installment_doc",
                        getCustomerName(userId), // เรียกใช้ Helper Method
                        userId,
                        "รุ่น: " + fullDeviceName
                );

                return "ได้รับข้อมูลเรียบร้อยครับ 📝\n" +
                        "แอดมินกำลังตรวจสอบเอกสารและประวัติ รบกวนรอผลการอนุมัติสักครู่นะครับ ⏳";

            case "ADMIN_MODE":
                return null;

            default:
                userState.setCurrentState("STEP_1_SELECT_MODEL");
                userStateRepository.save(userState);
                return "ระบบเริ่มการทำรายการใหม่ครับ สนใจเป็น iPhone รุ่นไหน ความจุเท่าไหร่ แจ้งแอดมินได้เลยครับ";
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

    /**
     * Helper Method: สร้างข้อความราคา
     */
    private String buildPriceMessage(PromotionPrice price) {
        StringBuilder sb = new StringBuilder();
        sb.append("💸 **เงินดาวน์เริ่มต้น:** ").append(String.format("%,d", price.getDownPayment())).append(" บาท\n");
        sb.append("📌 **ยอดส่งรายเดือน:**\n");

        if (price.getMonth10() != null) sb.append("- 10 เดือน: งวดละ ").append(String.format("%,d", price.getMonth10())).append(" บาท\n");
        if (price.getMonth12() != null) sb.append("- 12 เดือน: งวดละ ").append(String.format("%,d", price.getMonth12())).append(" บาท\n");
        if (price.getMonth15() != null) sb.append("- 15 เดือน: งวดละ ").append(String.format("%,d", price.getMonth15())).append(" บาท\n");
        if (price.getMonth18() != null) sb.append("- 18 เดือน: งวดละ ").append(String.format("%,d", price.getMonth18())).append(" บาท\n");
        if (price.getMonth24() != null) sb.append("- 24 เดือน: งวดละ ").append(String.format("%,d", price.getMonth24())).append(" บาท\n");

        return sb.toString();
    }
}