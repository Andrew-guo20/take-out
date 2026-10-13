package com.sky.websocket;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务
 */
@Component
@Slf4j
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    //存放会话对象
    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("客户端：{}建立连接", sid);
        sessionMap.put(sid, session);
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到来自客户端：{}的信息:{}", sid, message);
    }

    /**
     * 连接关闭调用的方法
     *
     * @param sid
     */
    @OnClose
    public void onClose(Session session, @PathParam("sid") String sid) {
        log.info("连接断开:{}", sid);
        sessionMap.remove(sid, session);
    }

    /**
     * 群发
     *
     * @param message
     */
    public void sendToAllClient(String message) {
        for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
            Session session = entry.getValue();
            try {
                // 同一连接上的同步发送必须串行执行。
                synchronized (session) {
                    if (!session.isOpen()) {
                        sessionMap.remove(entry.getKey(), session);
                        continue;
                    }
                    session.getBasicRemote().sendText(message);
                }
            } catch (IOException | IllegalStateException e) {
                log.warn("向客户端：{}发送消息失败", entry.getKey(), e);
            }
        }
    }

}
