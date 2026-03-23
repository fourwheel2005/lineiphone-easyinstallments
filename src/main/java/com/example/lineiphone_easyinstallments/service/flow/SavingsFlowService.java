package com.example.lineiphone_easyinstallments.service.flow;


import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;

    @Override
    public boolean supports(String serviceName) {
        // แผนกนี้รับผิดชอบเฉพาะคนที่พิมพ์หรือเลือก "ออมดาวน์" เท่านั้น
        return "ออมดาวน์".equals(serviceName);
    }

    @Override
    public String getServiceName() {
        return "ออมดาวน์";
    }

    @Override
    public String processMessage(UserState userState, String userMessage) {

        // ถ้าเป็นการเรียกครั้งแรกจาก ChatFlowManager (ส่งข้อความว่างๆ มากระตุ้น)
        // หรือ State เป็น STEP_1 ให้เริ่มถามคำถามแรก
        String state = userState.getCurrentState() != null ? userState.getCurrentState() : "STEP_1_SELECT_MODEL";

        switch (state) {
            case "STEP_1_SELECT_MODEL":
                // เลื่อน State ไปรอคำตอบใน Step ถัดไป
                userState.setCurrentState("STEP_2_SAVINGS_PLAN");
                userStateRepository.save(userState);
                return "[ออมดาวน์ - Step 1] ยินดีต้อนรับสู่บริการออมดาวน์ครับ 💰\n" +
                        "ลูกค้าสนใจออมดาวน์เป็น iPhone รุ่นไหน และความจุกี่ GB ดีครับ?";

            case "STEP_2_SAVINGS_PLAN":
                userState.setCurrentState("STEP_3_OPEN_ACCOUNT");
                userStateRepository.save(userState);
                return "[ออมดาวน์ - Step 2] รุ่นนี้สวยมากครับ!\n" +
                        "ลูกค้าสะดวกส่งออมรายวัน รายสัปดาห์ หรือรายเดือนดีครับ? และสะดวกส่งงวดละประมาณเท่าไหร่ครับ?";

            case "STEP_3_OPEN_ACCOUNT":
                // สมมติว่ารอโอนเงินเปิดบิล
                userState.setCurrentState("STEP_4_WAITING_SLIP");
                userStateRepository.save(userState);
                return "[ออมดาวน์ - Step 3] รับทราบเงื่อนไขครับ ✅\n" +
                        "เพื่อเป็นการเปิดบัญชีออมดาวน์ รบกวนลูกค้าโอนยอดเปิดบิลแรก และส่งสลิปมาที่ช่องแชทนี้ได้เลยนะครับ";

            case "STEP_4_WAITING_SLIP":
                return "[ออมดาวน์ - Step 4] (ระบบกำลังรอตรวจสอบสลิป... หากส่งแล้วรอแอดมินสักครู่นะครับ)";

            default:
                // เซฟตี้: ถ้า State ผิดเพี้ยน ให้กลับไปเริ่ม Step 1 ของออมดาวน์ใหม่
                userState.setCurrentState("STEP_1_SELECT_MODEL");
                userStateRepository.save(userState);
                return "เข้าสู่ระบบออมดาวน์อีกครั้ง ลูกค้าต้องการเริ่มต้นทำรายการใหม่ไหมครับ? (พิมพ์รุ่นที่อยากออมได้เลย)";
        }
    }
}