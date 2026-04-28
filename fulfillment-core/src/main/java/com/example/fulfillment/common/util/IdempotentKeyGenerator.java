package com.example.fulfillment.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 幂等键生成器
 */
public class IdempotentKeyGenerator {
    
    /**
     * 生成幂等键
     * 格式：sceneCode:hash(bizOrderNo:paymentNo)
     * 
     * @param sceneCode 场景码
     * @param bizOrderNo 业务订单号
     * @param paymentNo 支付流水号（可为空）
     * @return 幂等键
     */
    public static String generate(String sceneCode, String bizOrderNo, String paymentNo) {
        String source = bizOrderNo;
        if (paymentNo != null && !paymentNo.isEmpty()) {
            source = bizOrderNo + ":" + paymentNo;
        }
        String hash = md5(source);
        return sceneCode + ":" + hash;
    }
    
    /**
     * 生成简单幂等键（用于无支付场景）
     */
    public static String generateSimple(String sceneCode, String bizOrderNo) {
        return sceneCode + ":" + md5(bizOrderNo);
    }

    /**
     * 生成带动作编码的幂等键（用于生命周期动作场景）
     * <p>
     * 格式：sceneCode:hash(actionCode:bizOrderNo)
     * <p>
     * 同一订单的不同动作（如 GRANT / REVOKE）会产生不同的幂等键，
     * 避免不同动作之间互相冲突。
     *
     * @param sceneCode  场景码（如 "LIFECYCLE_ACTION"）
     * @param bizOrderNo 业务订单号
     * @param actionCode 动作编码（如 "GRANT"、"REVOKE"、"RENEW"）
     * @return 幂等键
     */
    public static String generateLifecycle(String sceneCode, String bizOrderNo, String actionCode) {
        String source = actionCode + ":" + bizOrderNo;
        return sceneCode + ":" + md5(source);
    }
    
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 应该总是可用，如果不可用则使用简单拼接
            return String.valueOf(input.hashCode());
        }
    }
}
