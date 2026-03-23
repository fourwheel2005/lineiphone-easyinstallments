package com.example.lineiphone_easyinstallments.service.flow;


import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EasyInstallmentFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;

    @Override
    public boolean supports(String serviceName) {
        // แผนกนี้รับผิดชอบเฉพาะคนที่เลือก "ไอโฟนผ่อนง่าย"
        return "ไอโฟนผ่อนง่าย".equals(serviceName);
    }

    @Override
    public String getServiceName() {
        return "ไอโฟนผ่อนง่าย";
    }

    @Override
    public String processMessage(UserState userState, String userMessage) {

        // ถ้าเพิ่งเข้ามาครั้งแรก ให้เริ่มที่ STEP 1
        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_SELECT_MODEL";

        switch (state) {
            case "STEP_1_SELECT_MODEL":
                userState.setCurrentState("STEP_2_CHECK_QUALIFICATION");
                userStateRepository.save(userState);
                return "[ไอโฟนผ่อนง่าย - Step 1] ยินดีต้อนรับสู่บริการไอโฟนผ่อนง่ายครับ ✨ อนุมัติไว ไม่ยุ่งยาก!\n" +
                        "ลูกค้าสนใจเป็น iPhone รุ่นไหน และความจุกี่ GB ดีครับ?";

            case "STEP_2_CHECK_QUALIFICATION":
                userState.setCurrentState("STEP_3_OFFER_PLAN");
                userStateRepository.save(userState);
                return "[ไอโฟนผ่อนง่าย - Step 2] รับทราบครับ!\n" +
                        "เพื่อให้แอดมินแนะนำโปรไฟล์ที่ผ่านง่ายที่สุด รบกวนขอทราบ **อาชีพปัจจุบัน** และ **รายได้โดยประมาณ** ของลูกค้าหน่อยนะครับ 💼";

            case "STEP_3_OFFER_PLAN":
                userState.setCurrentState("STEP_4_REQUEST_DOCS");
                userStateRepository.save(userState);
                return "[ไอโฟนผ่อนง่าย - Step 3] โปรไฟล์ดีมากครับ ผ่านฉลุยแน่นอน 🚀\n" +
                        "สำหรับรุ่นนี้ ยอดดาวน์เริ่มต้นที่ X,XXX บาท ส่งเดือนละ Y,YYY บาท\n" +
                        "ลูกค้าสะดวกรับเรทนี้เลยไหมครับ? (พิมพ์ 'ตกลง' เพื่อไปขั้นตอนส่งเอกสาร)";

            case "STEP_4_REQUEST_DOCS":
                // รอแอดมินมาตรวจเอกสาร
                userState.setCurrentState("STEP_5_WAITING_APPROVAL");
                userStateRepository.save(userState);
                return "[ไอโฟนผ่อนง่าย - Step 4] เยี่ยมเลยครับ 🎉\n" +
                        "รบกวนลูกค้าส่ง 1. รูปถ่ายบัตรประชาชน 2. สเตทเมนท์ย้อนหลัง 3 เดือน เข้ามาได้เลยครับ";

            case "STEP_5_WAITING_APPROVAL":
                return "[ไอโฟนผ่อนง่าย - Step 5] (ได้รับเอกสารแล้ว รอแอดมินตรวจสอบและแจ้งผลอนุมัติสักครู่นะครับ ⏳)";

            default:
                // เซฟตี้: กลับไปเริ่มใหม่
                userState.setCurrentState("STEP_1_SELECT_MODEL");
                userStateRepository.save(userState);
                return "เข้าสู่ระบบไอโฟนผ่อนง่ายอีกครั้ง ลูกค้าต้องการประเมินใหม่สำหรับรุ่นไหนดีครับ?";
        }
    }
}