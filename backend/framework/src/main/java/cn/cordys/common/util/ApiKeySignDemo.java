package cn.cordys.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class ApiKeySignDemo {

    // 你提供的 AK/SK
    private static final String ACCESS_KEY = "sNXZqWqfPCNbCawK";
    private static final String SECRET_KEY = "99b1b4274ff0e74c7b9a5d11c31503bc";

    // 与后端 CodingUtils 保持一致：GCM tag length = 128
    private static final int GCM_TAG_LENGTH = 128;

    public static void main(String[] args) throws Exception {
        long ts = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");

        // 后端要求：split("|") 后第1段必须是 accessKey，最后1段是时间戳
        String plain = ACCESS_KEY + "|" + nonce + "|" + ts;

        String signature = aesGcmEncryptToBase64(
                plain,
                SECRET_KEY,
                ACCESS_KEY.getBytes(StandardCharsets.UTF_8) // 与后端 ApiKeyHandler 一致
        );

        String authorizationHeader = ACCESS_KEY + ":" + signature;

        System.out.println("plain           = " + plain);
        System.out.println("signature       = " + signature);
        System.out.println("Authorization   = " + authorizationHeader);

        //  可直接用于 HTTP 头：
        //  Authorization: {accessKey}:{signature}
        //  X-Tenant-ID
        //  签名时间窗口 30 分钟（过期会失败）
        //  若接口有 @RequiresPermissions，权限按 AK 所属用户 判定
        //  API Key 禁用/过期会直接失败
    }

    private static String aesGcmEncryptToBase64(String src, String secretKey, byte[] iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

        byte[] encrypted = cipher.doFinal(src.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }
}