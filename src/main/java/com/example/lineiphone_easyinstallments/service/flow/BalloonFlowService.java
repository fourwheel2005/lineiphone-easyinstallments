package com.example.lineiphone_easyinstallments.service.flow;

import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalloonFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;
    private final LineMessageService lineMessageService;

    private final String ADMIN_GROUP_ID = "C_YOUR_ADMIN_GROUP_ID_HERE";

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
        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_SCREENING";
        String msg = userMessage.trim();
        String userId = userState.getLineUserId();

        // 🚨 ทางออกฉุกเฉิน (ดักคำว่าแอดมิน)
        if (msg.contains("แอดมิน") || msg.contains("คุยกับคน")) {
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);

            // 📲 ส่งการ์ดแจ้งเตือนฉุกเฉินเข้ากลุ่ม
            lineMessageService.sendEmergencyCard(ADMIN_GROUP_ID, "ผ่อนบอลลูน", userId, "ลูกค้ากดเรียกแอดมิน/พิมพ์นอกเรื่อง");

            return "รับทราบครับ รบกวนรอแอดมินเข้ามาดูแลสักครู่นะครับ ⏳";
        }

        switch (state) {
            // ==========================================
            // Step 1: คัดกรองเบื้องต้น
            // ==========================================
            case "STEP_1_SCREENING":
                userState.setCurrentState("STEP_1_WAITING_INFO");
                userStateRepository.save(userState);
                return "สวัสดีครับ 🙏😊 แอดมินขออนุญาตสอบถามรายละเอียดเพิ่มเติม\n" +
                        "👉 ลูกค้าใช้ไอโฟนรุ่นไหน ความจุกี่ GB ครับ\n" +
                        "👉 ลูกค้าอยู่จังหวัดอะไร\n" +
                        "👉 ลูกค้าอายุเท่าไหร่ครับ";

            case "STEP_1_WAITING_INFO":
                // 🌟 [จุดที่ AI จะเข้ามาทำงานในอนาคต] สกัด JSON
                boolean isQualified = true; // จำลองว่าผ่านเงื่อนไขอายุและรุ่น

                if (!isQualified) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ต้องขออภัยด้วยนะครับ 🙏 ทางร้านขอสงวนสิทธิ์ให้บริการเฉพาะลูกค้าอายุ 19 ปีบริบูรณ์ขึ้นไป และรับจัดยอดเฉพาะรุ่น iPhone 12 ขึ้นไปครับผม ขอบคุณที่สนใจสอบถามนะครับ";
                }

                userState.setCurrentState("STEP_1_5_WAITING_CONDITION");
                userStateRepository.save(userState);
                return "เยี่ยมเลยครับ! ขอแอดมินเช็คสภาพเครื่องนิดนึงนะครับ 😊\n" +
                        "👉 เครื่องเคยซ่อมมั้ยครับ\n" +
                        "👉 เครื่องมีการชำรุดมั้ยครับ\n" +
                        "👉 เครื่องติดผ่อนร้านอื่นมั้ยครับ";

            case "STEP_1_5_WAITING_CONDITION":
                // 🌟 [จุดที่ AI จะเข้ามาทำงาน] วิเคราะห์คำตอบลูกค้า
                if (msg.contains("ติดผ่อน") || msg.contains("ไอคลาวด์") || msg.contains("จำนำ")) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ทางร้านไม่รับเครื่องที่ติดผ่อน หรือติดล็อคจากที่อื่นครับผม ต้องขออภัยด้วยนะครับ 😭🙏";
                }
                if (msg.contains("สแกนหน้าไม่ได้") || msg.contains("ซ่อมบอร์ด")) {
                    userState.setCurrentState("REJECTED");
                    userStateRepository.save(userState);
                    return "ต้องขออภัยด้วยนะครับ 🙏 ทางร้านไม่สามารถรับเครื่องที่สแกนหน้าไม่ได้ หรือมีการซ่อมบอร์ดมาครับผม";
                }

                userState.setCurrentState("STEP_2_REQUEST_PHOTOS");
                userStateRepository.save(userState);
                return "โอเคครับ 👌\n" +
                        "รบกวนลูกค้าถ่ายรูปรอบเครื่องส่งแอดมินด้วยครับ 📸 และรบกวนแคปหน้า **การตั้งค่า > ทั่วไป > เกี่ยวกับ** ส่งมาเช็คโมเดลด้วยครับ";

            // ==========================================
            // Step 2 & 3: ขอรูปภาพ & ตรวจสอบอนุมัติ
            // ==========================================
            case "STEP_2_REQUEST_PHOTOS":
                userState.setCurrentState("STEP_3_WAITING_APPROVAL");
                userStateRepository.save(userState);

                // 📲 ส่งการ์ด [ตรวจสอบสภาพเครื่อง] ให้แอดมินกดผ่าน/ไม่ผ่าน
                lineMessageService.sendAdminApprovalCard(
                        ADMIN_GROUP_ID,
                        "ผ่อนบอลลูน",
                        "balloon",
                        "คุณลูกค้า (รอระบบดึงชื่อ)",
                        userId,
                        "iPhone 12 หรือสูงกว่า" // อนาคตใช้ AI สกัดมาใส่
                );

                return "รับรูปภาพเรียบร้อยครับ 📸 รอแอดมินตรวจสอบสภาพเครื่องสักครู่นะครับ ⏳";

            case "STEP_3_WAITING_APPROVAL":
                if (msg.contains("ผ่าน")) { // สมมติแอดมินพิมพ์ว่าผ่าน (ของจริงควรกดปุ่ม Flex)
                    userState.setCurrentState("STEP_3_5_CHECK_CREDIT");
                    userStateRepository.save(userState);
                    return "โอเคครับ สภาพเครื่องเบื้องต้นผ่านครับ ✅\n" +
                            "รบกวนขอทราบ **ชื่อ-นามสกุล** ของลูกค้าด้วยนะครับผม (เพื่อเช็คเครดิตร้านสักครู่ครับ)";
                }
                return "แอดมินกำลังเร่งตรวจสอบให้นะครับ รบกวนรอสักครู่ครับผม ⏳";

            case "STEP_3_5_CHECK_CREDIT":
                // ลูกค้าพิมพ์ชื่อ-นามสกุลมาแล้ว
                userState.setCurrentState("STEP_4_PRICING");
                userStateRepository.save(userState);

                lineMessageService.sendCreditApprovalCard(
                        ADMIN_GROUP_ID,
                        "ผ่อนบอลลูน",
                        userId,
                        msg
                );

                return "รับข้อมูลเรียบร้อยครับ รบกวนรอแอดมินเช็คเครดิตสักครู่นะครับ ⏳";


            case "STEP_4_PRICING":
                userState.setCurrentState("STEP_4_WAITING_MONTH_SELECTION");
                userStateRepository.save(userState);
                return "เช็คเครดิตผ่านเรียบร้อยครับ 🎉 ตอนนี้แอดมินขอเสนอโปรโมชั่นให้เลือกนะครับ\n\n" +
                        "1. **โปรโมชั่นรายเดือน (บอลลูนรายเดือน):**\n" +
                        "- ยอดรับซื้อ: [ดึงจากฐานข้อมูล] บาท\n" +
                        "- ค่างวด 6 เดือน: [ดึงจากฐานข้อมูล] บาท\n" +
                        "- ค่างวด 8 เดือน: [ดึงจากฐานข้อมูล] บาท\n" +
                        "...\n\n" +
                        "👉 ลูกค้าสนใจรับเป็นระยะเวลา 6, 8, 10 หรือ 12 เดือนดีครับ?";

            case "STEP_4_WAITING_MONTH_SELECTION":
                if (msg.contains("6") || msg.contains("8") || msg.contains("10") || msg.contains("12")) {
                    userState.setCurrentState("STEP_5_PREPARATION");
                    userStateRepository.save(userState);
                    return "ลูกค้ามีโทรศัพท์มือถือที่ใช้ได้กี่เครื่องครับ? (ต้องมี 2 เครื่อง: เครื่องทำสัญญา 1 + เครื่องติดต่อ 1 และทั้ง 2 เครื่องต้องเป็น iPhone หรือ iPad)\n\n" +
                            "[SHOW_HOWTO_IMAGE_2]";
                }
                return "ลูกค้ารับเป็นระยะเวลา 6, 8, 10 หรือ 12 เดือนดีครับ? พิมพ์บอกแอดมินได้เลยครับ";

            // ==========================================
            // Step 5: เตรียมความพร้อม
            // ==========================================
            case "STEP_5_PREPARATION":
                if (msg.contains("เครื่องเดียว") || msg.contains("ไม่มี") || msg.equals("1")) {
                    return "ถ้ามีเครื่องเดียว อาจจะต้องรบกวนลูกค้ายืมคนใกล้ตัวก่อนไหมครับผม หรือลูกค้ามีเป็น iPad อีกเครื่องไหมครับ (ใช้แค่ตอนทำรายการแป๊บเดียวครับผม)";
                }

                userState.setCurrentState("STEP_6_DOCUMENTS");
                userStateRepository.save(userState);
                return "โอเคครับ เครื่องที่จะทำสัญญาต้องทำการ **ล้างเครื่อง (Reset)** เป็นเครื่องเปล่านะครับ\n" +
                        "รบกวนลูกค้า **สำรองข้อมูล (Backup)** รูปภาพ, iCloud และประวัติ LINE ไว้ก่อนนะครับ (เพราะข้อมูลจะหายหมด)\n\n" +
                        "พร้อมแล้วแจ้งแอดมินได้เลยนะครับ เดี๋ยวเราไปขั้นตอนส่งเอกสารกัน\n\n" +
                        "[SHOW_HOWTO_IMAGE]";

            // ==========================================
            // Step 6: ขอเอกสาร
            // ==========================================
            case "STEP_6_DOCUMENTS":
                userState.setCurrentState("STEP_7_HANDOFF");
                userStateRepository.save(userState);
                return "รบกวนลูกค้าเตรียมเอกสารดังนี้นะครับ:\n" +
                        "1. บัตรประชาชนตัวจริง (วางคู่เครื่อง เขียน \"ขายเครื่อง\")\n" +
                        "2. Statement ย้อนหลัง 3 เดือน (แคปจากแอพ)\n" +
                        "3. ที่อยู่ปัจจุบัน และ ที่อยู่ที่ทำงาน\n" +
                        "4. เบอร์โทรลูกค้า และ เบอร์คนสนิท 2 คน\n\n" +
                        "รบกวนส่งข้อมูลทั้งหมดเป็น **รูปภาพ** เข้ามาได้เลยครับ 👇\n" +
                        "[SHOW_DOCS_MONTHLY]";

            // ==========================================
            // Step 7: Handoff ส่งต่อแอดมินตรวจสอบเอกสารรอบสุดท้าย
            // ==========================================
            case "STEP_7_HANDOFF":
                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                // 📲 ส่งการ์ด [ตรวจเอกสาร] ให้แอดมิน (ปุ่ม: เอกสารครบ / ขอเอกสารเพิ่ม)
                lineMessageService.sendDocumentApprovalCard(ADMIN_GROUP_ID, "ผ่อนบอลลูน", userId, "ลูกค้าส่งเอกสารและรูปภาพมาแล้ว");

                return "รับเอกสารเรียบร้อยครับ รบกวนรอแอดมินตรวจสอบความถูกต้องสักครู่นะครับ ⏳";

            case "ADMIN_MODE":
                return ""; // บอทเงียบ ให้คนตอบ

            default:
                userState.setCurrentState("STEP_1_SCREENING");
                userStateRepository.save(userState);
                return "เริ่มการประเมินผ่อนบอลลูนใหม่ รบกวนแจ้งรุ่น ความจุ จังหวัด และอายุครับ";
        }
    }
}