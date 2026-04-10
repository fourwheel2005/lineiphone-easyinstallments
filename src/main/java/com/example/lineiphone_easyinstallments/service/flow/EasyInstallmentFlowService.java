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

/**
 * ✨ EasyInstallmentFlowService — ไอโฟนผ่อนง่าย
 *
 * Flow ที่ปรับใหม่ (ตัดเช็คสภาพเครื่องทิ้ง + แยกถามทีละข้อแบบ Sequential):
 * ─────────────────────────────────────────────────────
 * STEP_1_MODEL              → ถามรุ่น
 * STEP_2_CAPACITY           → รับรุ่น → ถามความจุ
 * STEP_3_CONDITION          → รับความจุ → ถามมือ 1/มือ 2
 * STEP_4_PROVINCE           → รับมือ 1/มือ 2 → ถามจังหวัด
 * STEP_5_AGE                → รับจังหวัด → ถามอายุ
 * STEP_6_PRICE_AND_ID_CARD  → รับอายุ → เช็คราคา/แสดงราคา → ขอบัตรประชาชน
 * STEP_7_NAME               → รับรูปบัตร → ขอชื่อ-นามสกุล
 * STEP_8_FACEBOOK           → รับชื่อ → ขอลิ้งค์ Facebook
 * STEP_9_SUBMIT_DATA        → รับลิ้งค์เฟส → แจ้งเตือนแอดมิน → ADMIN_MODE
 * ─────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EasyInstallmentFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final AiDataExtractorService aiDataExtractorService;
    // 💡 นำ AiScreeningService ออกไปแล้ว เพราะไม่ได้ใช้เช็ค YES/NO ใน Flow นี้แล้ว
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

        // 🚨 ทางออกฉุกเฉิน (ดักคำเรียกแอดมิน และ คำหงุดหงิด)
        boolean isPanic = msg.contains("แอดมิน") || msg.contains("คุยกับคน") ||
                msg.contains("อ่านดีๆ") || msg.contains("บอกไปแล้ว") ||
                msg.contains("บอท") || msg.contains("ไม่รู้เรื่อง") ||
                msg.contains("อะไรเนี่ย");

        if (isPanic) {
            userState.setPreviousState(state);
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);
            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, getServiceName(), getCustomerName(userId), "ลูกค้าระบุต้องการคุยกับคน หรือ เกิดความหงุดหงิดบอท");
            return "รับทราบครับ แอดมินขออภัยในความไม่สะดวกนะครับ 🙏 เดี๋ยวแอดมินตัวจริงรีบเข้ามาดูแลเคสนี้ให้ทันที รบกวนรอสักครู่นะครับ ⏳";
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
                userState.setCurrentState("STEP_6_PRICE_AND_ID_CARD");
                userStateRepository.save(userState);
                return "โอเคครับ 📍\n" +
                        "👉 แล้วลูกค้า **อายุ** เท่าไหร่ครับ?";

            // ══════════════════════════════════════════════════════════
            case "STEP_6_PRICE_AND_ID_CARD": // แสดงราคาแล้วขอบัตร ปชช. เลย
                // ══════════════════════════════════════════════════════════
                ExtractedData ageData = aiDataExtractorService.extractInfo(msg);
                Integer extractedAge = ageData.age();

                String ageWarning = "";
                // แจ้งเตือนหากอายุต่ำกว่า 18 ปี ตามเงื่อนไขเอกสาร
                if (extractedAge != null && extractedAge > 0 && extractedAge < 18) {
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

                userState.setCurrentState("STEP_7_NAME");
                userStateRepository.save(userState);

                String freebies = "ฟิล์มกันรอย, เคส, ฟิล์มกระจกเลนส์กล้อง";
                if (savedCondition != null && savedCondition.contains("มือ 2")) {
                    freebies += ", หัวชาร์จแท้มูลค่า 790 บาท";
                }

                // 🎯 🟢 ปรับประโยค return ตรงของแถมให้ดึงจากตัวแปร freebies 🟢 🎯
                return "เช็คราคาให้แล้วครับ! 🎉 สำหรับ **iPhone " + savedModel + " " + savedCapacity + " (" + savedCondition + ")**\n\n" +
                        buildPriceMessage(price) + "\n" +
                        "🎁 **แถมฟรี:** " + freebies + "\n\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        ageWarning +
                        "การผ่อนกับร้านเรา **ไม่เช็คบูโร** ขอแค่อายุ 18-55 ปี และมีรายได้ครับ\n\n" +
                        "📸 เพื่อดำเนินการต่อ รบกวนลูกค้าถ่ายรูป **หน้าบัตรประชาชน** ส่งมาในแชทนี้ได้เลยครับ";

            // ══════════════════════════════════════════════════════════
            case "STEP_7_NAME": // รับหน้าบัตร (จำลองจากคำว่า [รูปภาพ]) แล้วขอชื่อ
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("STEP_8_FACEBOOK");
                userStateRepository.save(userState);
                return "ได้รับรูปบัตรประชาชนเรียบร้อยครับ 🪪\n\n" +
                        "👉 ถัดไป รบกวนลูกค้าพิมพ์ **ชื่อ-นามสกุล** ส่งมาให้แอดมินหน่อยครับ ✍️";

            // ══════════════════════════════════════════════════════════
            case "STEP_8_FACEBOOK": // รับชื่อ แล้วขอลิ้งค์ Facebook
                // ══════════════════════════════════════════════════════════

                // 🟢 FIX: บันทึกชื่อ-นามสกุลที่ลูกค้าพิมพ์ (msg) ลง Database ก่อน
                // (ถ้าใน UserState ของคุณมีฟิลด์เก็บชื่อ ให้ใช้ฟิลด์นั้น เช่น setFullName, setRealName หรือ setNote ก็ได้ครับ)
                userState.setFullName(msg); // 👈 สมมติว่าใช้ฟิลด์ setFullName นะครับ

                userState.setCurrentState("STEP_9_SUBMIT_DATA");
                userStateRepository.save(userState);
                return "รับทราบข้อมูลครับ 📝\n\n" +
                        "👉 ขั้นตอนสุดท้าย เพื่อใช้ในการประเมินเครดิต รบกวนส่ง **ลิ้งค์เฟสบุ๊ค (Facebook)** ของลูกค้ามาให้แอดมินทีนะครับ 🔗\n" +
                        "(ไปที่หน้าโปรไฟล์เฟสบุ๊ค > กดจุด 3 จุด > คัดลอกลิงก์)";

            // ══════════════════════════════════════════════════════════
            case "STEP_9_SUBMIT_DATA": // รับ Facebook -> ส่งข้อมูลเข้า Admin Mode
                // ══════════════════════════════════════════════════════════
                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                String fullDeviceName = userState.getDeviceModel() + " " + userState.getCapacity() + " (" + userState.getCondition() + ")";
                String displayLineName = getCustomerName(userId);

                // 🟢 FIX: ดึงชื่อจริงที่เซฟไว้จาก Step 8 ออกมา ถ้าไม่มีก็ใช้ชื่อไลน์แทน
                String realName = userState.getFullName() != null ? userState.getFullName() : "ไม่ระบุชื่อ";
                String facebookLink = msg; // สิ่งที่ลูกค้าพิมพ์ล่าสุดคือลิ้งค์เฟส

                lineMessageService.sendAdminApprovalCard(
                        ADMIN_GROUP_ID,
                        "ไอโฟนผ่อนง่าย",
                        "easy_installment_doc",
                        realName + " (LINE: " + displayLineName + ")", // 👈 แนบชื่อพิมพ์ + ชื่อไลน์ให้แอดมินดู
                        userId,
                        "รุ่น: " + fullDeviceName + "\nFB: " + facebookLink
                );

                return "ได้รับเอกสารและข้อมูลครบถ้วนครับ 📝\n" +
                        "แอดมินกำลังตรวจสอบเอกสาร ประวัติ และเฟสบุ๊ค รบกวนรอผลการอนุมัติสักครู่นะครับ ⏳";

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