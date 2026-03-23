package com.example.lineiphone_easyinstallments.controller;



import com.example.lineiphone_easyinstallments.service.line.ChatFlowManager;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.ReplyMessageRequest;
import com.linecorp.bot.messaging.model.TextMessage;
import com.linecorp.bot.spring.boot.handler.annotation.EventMapping;
import com.linecorp.bot.spring.boot.handler.annotation.LineMessageHandler;
import com.linecorp.bot.webhook.model.MessageEvent;
import com.linecorp.bot.webhook.model.PostbackEvent;
import com.linecorp.bot.webhook.model.TextMessageContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * คลาสนี้ทำหน้าที่เป็น "หู" และ "ปาก" คอยรับ Event จาก LINE และตอบกลับ
 * (รองรับโครงสร้างใหม่ของ LINE SDK v8+)
 */
@Slf4j
@RequiredArgsConstructor
@LineMessageHandler
public class LineWebhookController {

    private final ChatFlowManager chatFlowManager;
    private final MessagingApiClient messagingApiClient;


    @EventMapping
    public void handleTextMessageEvent(MessageEvent event) {

        // ตรวจสอบว่าเป็นข้อความประเภท Text หรือไม่
        if (event.message() instanceof TextMessageContent textMessageContent) {


            String lineUserId = event.source().userId();
            String userMessage = textMessageContent.text();
            String replyToken = event.replyToken();

            log.info("📩 ได้รับข้อความจาก [{}]: {}", lineUserId, userMessage);

            try {
                String replyText = chatFlowManager.handleTextMessage(lineUserId, userMessage);

                messagingApiClient.replyMessage(new ReplyMessageRequest(
                        replyToken,
                        List.of(new TextMessage(replyText)),
                        false
                ));

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
            if (action.equals("approve")) {
                adminReplyMessage = "✅ เคสของลูกค้า " + targetUserId + " อนุมัติผ่านเรียบร้อย! ระบบแจ้งลูกค้าแล้วครับ";
            } else if (action.equals("reject")) {
                adminReplyMessage = "❌ เคสนี้ถูกตีตกเรียบร้อยครับ";
            }

            messagingApiClient.replyMessage(new ReplyMessageRequest(
                    event.replyToken(),
                    List.of(new TextMessage(adminReplyMessage)),
                    false
            ));

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
}