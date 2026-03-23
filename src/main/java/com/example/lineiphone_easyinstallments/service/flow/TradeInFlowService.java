package com.example.lineiphone_easyinstallments.service.flow;


import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TradeInFlowService implements ServiceFlowHandler {

    private final UserStateRepository userStateRepository;

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
        switch (userState.getCurrentState()) {
            case "STEP_1_EVALUATE":
                return "[รับซื้อไอโฟน - Step 1] รบกวนแจ้งรุ่นและส่งรูปเครื่องเพื่อตีราคาครับ";

            case "STEP_2_OFFER_PRICE":
                return "[รับซื้อไอโฟน - Step 2] เสนอราคา... รับยอดโอนเลยไหมครับ?";

            default:
                userState.setCurrentState("STEP_1_EVALUATE");
                userStateRepository.save(userState);
                return "ยินดีต้อนรับสู่บริการรับซื้อไอโฟนครับ ให้ราคาสูงแน่นอน!";
        }
    }
}
