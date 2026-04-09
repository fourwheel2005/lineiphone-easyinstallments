package com.example.lineiphone_easyinstallments.service.flow;

import com.example.lineiphone_easyinstallments.dto.ExtractedData;
import com.example.lineiphone_easyinstallments.entity.PromotionPrice;
import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.PromotionPriceRepository;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import com.example.lineiphone_easyinstallments.service.ai.AiDataExtractorService;
import com.example.lineiphone_easyinstallments.service.ai.AiScreeningService;
import com.example.lineiphone_easyinstallments.service.ai.AiScreeningService.ScreeningAnswer;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * ✨ EasyInstallmentFlowService — ไอโฟนผ่อนง่าย
 *
 * Flow ที่ปรับใหม่ (แยกถามทีละข้อแบบ Sequential):
 * ─────────────────────────────────────────────────────
 * STEP_1_MODEL              → ถามรุ่น
 * STEP_2_CAPACITY           → รับรุ่น → ถามความจุ
 * STEP_3_CONDITION          → รับความจุ → ถามมือ 1/มือ 2
 * STEP_4_PROVINCE           → รับมือ 1/มือ 2 → ถามจังหวัด
 * STEP_5_AGE                → รับจังหวัด → ถามอายุ
 * STEP_6_PRICE_AND_REPAIR   → รับอายุ → เช็คราคา/แสดงราคา → ถามแกะซ่อม
 * STEP_7_FACEID             → ตรวจซ่อม → ถาม Face ID
 * STEP_8_INSTALLMENT        → ตรวจ Face ID → ถามติดผ่อน
 * STEP_9_REQUEST_DOCS       → ตรวจติดผ่อน → ขอบัตรประชาชน + Facebook
 * STEP_10_WAITING_APPROVAL  → รับเอกสาร → แจ้งเตือนแอดมิน → ADMIN_MODE
 * ─────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EasyInstallmentFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final AiDataExtractorService aiDataExtractorService;
    private final AiScreeningService aiScreeningService;
    private final PromotionPriceRepository promotionPriceRepository;
    private final LineMessageService lineMessageService;
    private final MessagingApiClient messagingApiClient;

    private final String ADMIN_GROUP_ID = "C222e16a6ce776922dd83ba418d733040";

    @Override
    public boolean supports(String serviceName) { return "ไอโฟนผ่อนง่าย".equals(serviceName); }

    @Override
    public String getServiceName() { return "ไอโฟนผ่อนง่าย"; }

    @Override
    public String processMessage(UserState userState, String userMessage) {
        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_MODEL";
        String msg = userMessage.trim();
        String userId = userState.getLineUserId();

        // 🚨 ทางออกฉุกเฉิน
        if (msg.contains("แอดมิน") || msg.contains("คุยกับคน")) {
            userState.setPreviousState(state);
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);
            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "ไอโฟนผ่อนง่าย", getCustomerName(userId), "ลูกค้าเรียกแอดมิน");
            return "รับทราบครับ รบกวนรอแอดมินเข้ามาดูแลสักครู่นะครับ ⏳";
        }

        switch (state) {

            // ══════════════════════════════════════════════════════════
            case "STEP_1_MODEL":
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_2_CAPACITY");
                userStateRepository.save(userState);
                return "ยินดีต้อนรับสู่บริการไอโฟนผ่อนง่ายครับ ✨ อนุมัติไว ไม่ยุ่งยาก!\n\n" +
                        "เพื่อเช็คโปรโมชั่นที่คุ้มที่สุด แอดมินขอสอบถามข้อมูลทีละข้อนะครับ\n" +
                        "👉 ลูกค้าสนใจ iPhone **รุ่นไหน** ครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_2_CAPACITY":
                // ══════════════════════════════════════════════════════════
                ExtractedData extractedModelData = aiDataExtractorService.extractInfo(msg);
                String extractedModel = extractedModelData.deviceModel();

                if (extractedModel == null || "unknown".equalsIgnoreCase(extractedModel)) {
                    return "แอดมินจับชื่อรุ่นไม่ทันครับ 😅 รบกวนขอทราบ 'รุ่นไอโฟน' อีกครั้งได้ไหมครับ\n" +
                            "เช่น 14, 15 Pro, 16 Pro Max ครับ 📱";
                }

                userState.setDeviceModel(extractedModel);
                userState.setCurrentState("STEP_3_CONDITION");
                userStateRepository.save(userState);
                return "รุ่น **iPhone " + extractedModel + "** นะครับ! 📱\n" +
                        "👉 สนใจความจุ **กี่ GB** ดีครับ? (เช่น 128, 256, 512)";

            // ══════════════════════════════════════════════════════════
            case "STEP_3_CONDITION":
                // ══════════════════════════════════════════════════════════
                ExtractedData extractedCapData = aiDataExtractorService.extractInfo(msg);
                String extractedCapacity = extractedCapData.capacity();

                if (extractedCapacity == null || "unknown".equalsIgnoreCase(extractedCapacity)) {
                    return "รบกวนระบุความจุอีกครั้งนะครับ 🙏\nเช่น 128GB, 256GB ครับ";
                }

                userState.setCapacity(extractedCapacity);
                userState.setCurrentState("STEP_4_PROVINCE");
                userStateRepository.save(userState);
                return "ความจุ **" + extractedCapacity + "** นะครับ!\n" +
                        "👉 ลูกค้ารับเป็นเครื่อง **มือ 1** หรือ **มือ 2** ดีครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_4_PROVINCE":
                // ══════════════════════════════════════════════════════════
                ExtractedData extractedCondData = aiDataExtractorService.extractInfo(msg);
                String extractedCondition = extractedCondData.condition();

                if (extractedCondition == null || "unknown".equalsIgnoreCase(extractedCondition)) {
                    return "รบกวนระบุอีกนิดนะครับว่ารับเป็น **มือ 1** หรือ **มือ 2** ครับ 🙏";
                }

                userState.setCondition(extractedCondition);
                userState.setCurrentState("STEP_5_AGE");
                userStateRepository.save(userState);
                return "รับเป็นเครื่อง **" + extractedCondition + "** นะครับ 👍\n" +
                        "👉 ลูกค้าอยู่ **จังหวัด** อะไรครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_5_AGE":
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_6_PRICE_AND_REPAIR");
                userStateRepository.save(userState);
                return "โอเคครับ 📍\n" +
                        "👉 แล้วลูกค้า **อายุ** เท่าไหร่ครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_6_PRICE_AND_REPAIR":
                // ══════════════════════════════════════════════════════════
                ExtractedData ageData = aiDataExtractorService.extractInfo(msg);
                Integer extractedAge = ageData.age();

                String ageWarning = "";
                // แจ้งเตือนหากอายุต่ำกว่า 18 ปี ตามเงื่อนไขเอกสาร
                if (extractedAge != null && extractedAge < 18) {
                    ageWarning = "⚠️ (เนื่องจากลูกค้าอายุต่ำกว่า 18 ปี ในขั้นตอนการทำสัญญาจะต้องใช้ข้อมูลผู้ปกครองที่อายุ 20 ปีขึ้นไปมาเป็นผู้ซื้อให้นะครับ)\n\n";
                }

                String savedModel = userState.getDeviceModel();
                String savedCapacity = userState.getCapacity();
                String savedCondition = userState.getCondition();

                // 🔍 นำข้อมูล Model + Capacity + Condition ที่เก็บไว้มาหาโปรโมชั่น
                Optional<PromotionPrice> priceOpt = promotionPriceRepository.findByModelAndCapacityAndCondition(savedModel, savedCapacity, savedCondition);

                if (priceOpt.isEmpty()) {
                    userState.setCurrentState("ADMIN_MODE");
                    userStateRepository.save(userState);
                    lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "ไอโฟนผ่อนง่าย", getCustomerName(userId),
                            "ค้นหาราคาไม่เจอ: " + savedModel + " " + savedCapacity + " (" + savedCondition + ")");
                    return "สำหรับรุ่น **" + savedModel + " " + savedCapacity + " (" + savedCondition + ")** ตอนนี้โปรโมชั่นอาจมีการเปลี่ยนแปลง\n\n" +
                            "เดี๋ยวแอดมินตัวจริงรีบเช็คสต๊อกและราคาพิเศษให้นะครับ รบกวนรอสักครู่ครับ ⏳";
                }

                PromotionPrice price = priceOpt.get();
                userState.setCurrentState("STEP_7_FACEID");
                userStateRepository.save(userState);

                return "เช็คราคาให้แล้วครับ! 🎉 สำหรับ **iPhone " + savedModel + " " + savedCapacity + " (" + savedCondition + ")**\n\n" +
                        buildPriceMessage(price) + "\n" +
                        "🎁 **แถมฟรี:** ฟิล์มกันรอย, เคส, ฟิล์มกระจกเลนส์กล้อง\n\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        ageWarning +
                        "ก่อนดำเนินการต่อ ขออนุญาตเช็คประวัติเครื่องนิดนึงนะครับ 🔍\n" +
                        "👉 เครื่องเคยแกะซ่อม หรือเปลี่ยนชิ้นส่วนใดๆ มาไหมครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_7_FACEID":
                // ══════════════════════════════════════════════════════════
                ScreeningAnswer answerRepair = aiScreeningService.interpret(msg);
                log.info("🛡️ [ผ่อนง่าย] STEP_7 เคยแกะซ่อม? → {}", answerRepair);

                if (answerRepair == ScreeningAnswer.YES) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ต้องขออภัยด้วยนะครับ 🙏\n" +
                            "ทางร้านขอสงวนสิทธิ์รับเครื่องที่ผ่านการแกะซ่อมหรือเปลี่ยนชิ้นส่วนครับ\n" +
                            "หากต้องการสอบถามเพิ่มเติม พิมพ์ 'แอดมิน' ได้เลยนะครับ";
                }
                if (answerRepair == ScreeningAnswer.UNCLEAR) {
                    return "ขออภัยด้วยนะครับ 😅 รบกวนตอบให้ชัดขึ้นได้ไหมครับ\n" +
                            "เช่น 'ไม่เคยแกะเลยครับ' หรือ 'เคยเปลี่ยนจอครับ'";
                }

                userState.setCurrentState("STEP_8_INSTALLMENT");
                userStateRepository.save(userState);
                return "โอเคครับ 👍 แล้ว **Face ID (สแกนหน้า)** ใช้งานได้ปกติไหมครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_8_INSTALLMENT":
                // ══════════════════════════════════════════════════════════
                ScreeningAnswer answerFaceId = aiScreeningService.interpret(msg);
                log.info("🛡️ [ผ่อนง่าย] STEP_8 Face ID ปกติ? → {}", answerFaceId);

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

                userState.setCurrentState("STEP_9_REQUEST_DOCS");
                userStateRepository.save(userState);
                return "เยี่ยมเลยครับ 😊 แล้วเครื่องมี **ติดผ่อนค้างกับร้านอื่น หรือติดล็อค iCloud** ไหมครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_9_REQUEST_DOCS":
                // ══════════════════════════════════════════════════════════
                ScreeningAnswer answerInstallment = aiScreeningService.interpret(msg);
                log.info("🛡️ [ผ่อนง่าย] STEP_9 ติดผ่อน/iCloud? → {}", answerInstallment);

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

                // ✅ ผ่านการคัดกรองทั้งหมด
                userState.setCurrentState("STEP_10_WAITING_APPROVAL");
                userStateRepository.save(userState);
                return "ผ่านการตรวจสอบเบื้องต้นครบแล้วครับ 🎉✅\n\n" +
                        "การผ่อนกับร้านเรา **ไม่เช็คบูโร** ขอแค่อายุ 18-55 ปี และมีรายได้ครับ\n\n" +
                        "📸 รบกวนลูกค้าถ่ายรูป **หน้าบัตรประชาชน** พิมพ์ **ชื่อ-นามสกุล** และส่ง **ลิ้งค์เฟสบุ๊ค** มาในแชทนี้ได้เลยครับ\n" +
                        "แอดมินจะรีบประเมินเครดิตและทำเรื่องอนุมัติให้ทันทีครับ";

            // ══════════════════════════════════════════════════════════
            case "STEP_10_WAITING_APPROVAL":
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                String fullDeviceName = userState.getDeviceModel() + " " + userState.getCapacity() + " (" + userState.getCondition() + ")";

                lineMessageService.sendAdminApprovalCard(
                        ADMIN_GROUP_ID, "ไอโฟนผ่อนง่าย", "easy_installment_doc",
                        getCustomerName(userId), userId, "รุ่น: " + fullDeviceName
                );

                return "ได้รับข้อมูลเรียบร้อยครับ 📝\n" +
                        "แอดมินกำลังตรวจสอบเอกสารและประวัติ รบกวนรอผลการอนุมัติสักครู่นะครับ ⏳";

            // ══════════════════════════════════════════════════════════
            case "ADMIN_MODE":
            case "REJECTED":
                // ══════════════════════════════════════════════════════════
                return null;

            default:
                userState.setCurrentState("STEP_1_MODEL");
                userStateRepository.save(userState);
                return "ระบบเริ่มการทำรายการใหม่ครับ สนใจเป็น iPhone รุ่นไหน แจ้งแอดมินได้เลยครับ";
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