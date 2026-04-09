package com.example.lineiphone_easyinstallments.controller;

import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import com.example.lineiphone_easyinstallments.service.flow.LineMessageService;
import com.example.lineiphone_easyinstallments.service.line.ChatFlowManager;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;
import com.linecorp.bot.messaging.model.ReplyMessageRequest;
import com.linecorp.bot.messaging.model.TextMessage;
import com.linecorp.bot.spring.boot.handler.annotation.EventMapping;
import com.linecorp.bot.spring.boot.handler.annotation.LineMessageHandler;
import com.linecorp.bot.webhook.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@RequiredArgsConstructor
@LineMessageHandler
public class LineWebhookController {

    private final ChatFlowManager chatFlowManager;
    private final MessagingApiClient messagingApiClient;
    private final UserStateRepository userStateRepository;
    private final LineMessageService lineMessageService;



    private final ConcurrentHashMap<String, Instant> lastImageReceivedTime = new ConcurrentHashMap<>();

    @EventMapping
    public void handleTextMessageEvent(MessageEvent event) {

        if (event.message() instanceof TextMessageContent textMessageContent) {

            String userMessage = textMessageContent.text().trim();
            String replyToken = event.replyToken();
            String lineUserId = event.source().userId();


            if (event.source() instanceof com.linecorp.bot.webhook.model.GroupSource groupSource) {
                if (userMessage.equalsIgnoreCase("/groupid")) {
                    String groupId = groupSource.groupId();
                    log.info("🎯 มีการเรียกดู Group ID: {}", groupId);
                    messagingApiClient.replyMessage(new ReplyMessageRequest(
                            replyToken,
                            List.of(new TextMessage("Group ID ของกลุ่มนี้คือ:\n" + groupId)),
                            false
                    ));
                } else {
                    log.info("🤫 ได้รับข้อความจากกลุ่มแอดมิน บอทจะไม่อ่านและไม่ตอบกลับครับ");
                }
                return; // 🛑 จบการทำงานทันที ไม่ส่งไปหา AI

            } else if (event.source() instanceof com.linecorp.bot.webhook.model.RoomSource) {
                log.info("🤫 ได้รับข้อความจาก Room บอทจะไม่อ่านและไม่ตอบกลับครับ");
                return; // 🛑 จบการทำงานทันที
            }


            log.info("📩 ได้รับข้อความจากลูกค้า [{}]: {}", lineUserId, userMessage);

            UserState userState = userStateRepository.findByLineUserId(lineUserId)
                    .orElseGet(() -> {
                        UserState newUser = new UserState();
                        newUser.setLineUserId(lineUserId);
                        return newUser;
                    });


            String msg = userMessage.toLowerCase();
            if (msg.contains("แอดมิน") || msg.contains("ติดต่อแอดมิน") || msg.contains("คุยกับคน")) {

                userState.setCurrentState("ADMIN_MODE");
                userStateRepository.save(userState);

                // ดึงชื่อลูกค้า
                String customerName = "ลูกค้า (ไม่ทราบชื่อ)";
                try {
                    var profile = messagingApiClient.getProfile(lineUserId).get();
                    customerName = profile.body().displayName();
                } catch (Exception e) {
                    log.warn("❌ ไม่สามารถดึงชื่อลูกค้าได้");
                }

                String MAIN_ADMIN_GROUP_ID = "C9a256ba28c79d51b09c6a07f51471b25";
                lineMessageService.sendEmergencyCard(MAIN_ADMIN_GROUP_ID, "สอบถามทั่วไป", customerName, "ลูกค้าต้องการคุยกับแอดมิน");

                messagingApiClient.replyMessage(new ReplyMessageRequest(
                        replyToken,
                        List.of(new TextMessage("รับทราบครับ รบกวนรอแอดมินเข้ามาดูแลสักครู่นะครับ ⏳")),
                        false
                ));

                return; // 🛑 จบการทำงาน ไม่ต้องส่งไปหา FlowManager แล้ว
            }

            // ==========================================
            // 🧠 ด่านที่ 4: ส่งเข้า Flow ปกติ (ลูกค้าคุยทั่วไป หรืออยู่ใน Flow ผ่อน)
            // ==========================================
            try {
                String replyText = chatFlowManager.handleTextMessage(lineUserId, userMessage);

                if (replyText != null && !replyText.trim().isEmpty()) {
                    messagingApiClient.replyMessage(new ReplyMessageRequest(
                            replyToken,
                            List.of(new TextMessage(replyText)),
                            false
                    ));
                } else {
                    log.info("🤫 บอทเข้าโหมดเงียบ (ADMIN_MODE) จะไม่มีการตอบกลับอัตโนมัติ");
                }

            } catch (Exception e) {
                log.error("❌ เกิดข้อผิดพลาดในการประมวลผลข้อความ: ", e);
                messagingApiClient.replyMessage(new ReplyMessageRequest(
                        replyToken,
                        List.of(new TextMessage("ขออภัยครับ ระบบขัดข้องชั่วคราว รบกวนรอแอดมินสักครู่นะครับ 🛠️")),
                        false
                ));
            }
        }
    }

    @EventMapping
    public void handlePostbackEvent(PostbackEvent event) {

        String postbackData = event.postback().data();
        log.info("🎯 แอดมินกดปุ่ม Postback Data: {}", postbackData);

        try {
            Map<String, String> dataMap = parsePostbackData(postbackData);
            String action = dataMap.get("action");
            String serviceName = dataMap.get("service");
            String targetUserId = dataMap.get("userId"); // ID ของลูกค้าที่ต้องไปอัปเดตสถานะ

            if (targetUserId == null || action == null) return;

            String adminReplyMessage = "";
            String messageToCustomer = null;

            // 🌟 ดึง UserState ของลูกค้าคนนี้ขึ้นมาก่อน
            UserState state = userStateRepository.findByLineUserId(targetUserId).orElse(new UserState());
            state.setLineUserId(targetUserId);

            if (action.equals("approve") || action.equals("approve_doc") || action.equals("approve_credit")) {
                adminReplyMessage = "✅ เคสของลูกค้าอนุมัติผ่านเรียบร้อย! ระบบแจ้งลูกค้าแล้วครับ";
                messageToCustomer = "🎉 ยินดีด้วยครับ! ข้อมูลของคุณได้รับการอนุมัติเรียบร้อยแล้ว แอดมินจะรีบดำเนินการขั้นตอนต่อไปให้นะครับ";

                if ("balloon".equals(serviceName)) {
                    state.setCurrentState("STEP_5_PRICING");
                    userStateRepository.save(state);

                    String nextStepMessage = chatFlowManager.handleTextMessage(targetUserId, "continue");
                    if (nextStepMessage != null) {
                        messageToCustomer += "\n\n" + nextStepMessage;
                    }
                }
            } else if (action.equals("reject") || action.equals("reject_credit")) {
                adminReplyMessage = "❌ เคสนี้ถูกปฏิเสธเรียบร้อยครับ";
                messageToCustomer = "ต้องขออภัยด้วยนะครับ 🙏 จากการตรวจสอบข้อมูล ยังไม่ผ่านเกณฑ์การพิจารณาในครั้งนี้ครับ หากมีข้อสงสัยสามารถพิมพ์สอบถามแอดมินได้เลยครับ";

                // ==========================================
                // 🚨 โหมดจัดการบอท: ปิด/เปิด AI ด้วยปุ่มของแอดมิน
                // ==========================================
            } else if (action.equals("take_case")) {
                adminReplyMessage = "💬 รับเรื่องแล้ว! (ปิดบอทชั่วคราว) คุยกับลูกค้าต่อใน LINE OA ได้เลยครับ";
                messageToCustomer = "แอดมินตัวจริงมารับเรื่องแล้วครับ! พิมพ์สอบถามได้เลยครับ 👇";

                state.setCurrentState("ADMIN_MODE");
                userStateRepository.save(state);

            } else if (action.equals("resume_bot")) {
                adminReplyMessage = "▶️ เปิดบอทให้ดูแลลูกค้าคนนี้ต่อแล้วครับ";
                messageToCustomer = "บอทผู้ช่วย DDMobile กลับมาดูแลต่อแล้วครับ! มีอะไรให้ช่วยกดเมนูด้านล่างได้เลยครับ ✨";

                // 🟢 สั่งเปิดบอท! ล้าง State ทิ้งให้กลับไปเป็นปกติ
                state.setCurrentState(null);
                state.setLineUserId(null);
                userStateRepository.save(state);
            }

            // 1. ตอบกลับแอดมินที่กดปุ่ม (Reply) ในกลุ่ม
            messagingApiClient.replyMessage(new ReplyMessageRequest(
                    event.replyToken(),
                    List.of(new TextMessage(adminReplyMessage)),
                    false
            ));

            // 2. ส่งข้อความเด้งไปหาลูกค้า (Push)
            if (messageToCustomer != null) {
                messagingApiClient.pushMessage(
                        null,
                        new PushMessageRequest(
                                targetUserId,
                                List.of(new TextMessage(messageToCustomer)),
                                false,
                                (List<String>) null
                        )
                );
            }

        } catch (Exception e) {
            log.error("❌ Error processing postback: ", e);
        }
    }

    private Map<String, String> parsePostbackData(String data) {
        Map<String, String> map = new HashMap<>();
        if (data == null || data.isEmpty()) return map;

        String[] pairs = data.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            }
        }
        return map;
    }


    @EventMapping
    public void handleMessageEvent(MessageEvent event) {

        // 1. ดึงข้อมูลแบบใหม่ของ SDK v8.x (ไม่มีคำว่า get นำหน้า)
        String userId = event.source().userId();
        String replyToken = event.replyToken();

        // 2. เช็คว่าลูกค้าส่ง "ข้อความ" มาใช่ไหม?
        if (event.message() instanceof TextMessageContent textMessage) {
            String msg = textMessage.text();
            String responseText = chatFlowManager.handleTextMessage(userId, msg);

            if (responseText != null && !responseText.isEmpty()) {
                lineMessageService.replyText(replyToken, responseText);
            }

            // 3. เช็คว่าลูกค้าส่ง "รูปภาพ" มาใช่ไหม?
        } else if (event.message() instanceof ImageMessageContent) {

            // เก็บเวลาที่ได้รับรูปล่าสุด
            lastImageReceivedTime.put(userId, Instant.now());

            // สร้าง Thread หน่วงเวลารอเผื่อลูกค้าส่งหลายรูป
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // รอ 3 วินาที

                    Instant lastTime = lastImageReceivedTime.get(userId);

                    // ถ้ารูปนี้คือรูปล่าสุดจริงๆ และไม่มีรูปอื่นส่งตามมาใน 2.5 วินาที
                    if (lastTime != null && Instant.now().minusMillis(2500).isAfter(lastTime)) {
                        lastImageReceivedTime.remove(userId);

                        // โยนคำว่า [รูปภาพ] เข้า Flow เพื่อให้บอทเดินหน้าต่อ
                        String dummyMessage = "[รูปภาพ]";
                        String responseText = chatFlowManager.handleTextMessage(userId, dummyMessage);

                        if (responseText != null && !responseText.isEmpty()) {
                            // ใช้ replyToken ของรูปภาพแผ่นสุดท้ายในการตอบกลับ
                            lineMessageService.replyText(replyToken, responseText);
                        }
                    }
                } catch (InterruptedException e) {
                    log.error("Error during image wait", e);
                }
            }).start();
        }
    }
}