package com.ruinap.infra.activation;

import cn.hutool.core.codec.Base32;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.DES;
import cn.hutool.db.Entity;
import cn.hutool.system.OsInfo;
import cn.hutool.system.SystemUtil;
import cn.hutool.system.UserInfo;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.repository.ActivationDB;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

/**
 * 激活系统
 *
 * @author qianye
 * @create 2024-05-30 16:34
 */
@Component
@Order(3)
public class ActivationSystem implements CommandLineRunner {
    @Autowired
    private ActivationDB activationDB;
    /**
     * 密钥
     */
    private final String SECRET_KEY = "司光智融传感器，岚电慧盈AGV";

    /**
     * 启动成功调用
     *
     * @param args 启动参数
     * @throws Exception
     */
    @Override
    public void run(String... args) throws Exception {
        activationVerify();
    }

    /**
     * 激活认证
     */
    public void activationVerify() {
        List<Entity> activationList;
        try {
            activationList = activationDB.query(new Entity());
        } catch (SQLException e) {
            activationList = new ArrayList<>(0);
        }

        String machineCode = getMachineCode();
        boolean isActivation = false;
        for (Entity entity : activationList) {
            String machineCodeH2 = entity.getStr("machine_code");
            String activationCodeH2 = entity.getStr("activation_code");
            String secureCode = entity.getStr("secure_code");
            Date expiredDate = entity.getDate("expired_date");

            if (!machineCode.equalsIgnoreCase(machineCodeH2)) {
                isActivation = false;
                break;
            }
            String md5 = SecureUtil.md5(StrUtil.format("{},{}{}", activationCodeH2, DateUtil.format(expiredDate, DatePattern.NORM_DATETIME_PATTERN), SECRET_KEY));
            if (secureCode.equalsIgnoreCase(md5)) {
                isActivation = isActivation(machineCode, activationCodeH2);
                if (isActivation) {
                    //比较当前时间是否在有效期内
                    if (expiredDate.before(new Date())) {
                        RcsLog.consoleLog.warn("您的激活码已过期，请重新激活");
                        isActivation = false;
                        break;
                    }
                    break;
                }
            }
        }

        if (activationList.isEmpty() || !isActivation) {
            RcsLog.consoleLog.warn("激活校验异常，请重新激活");
            RcsLog.consoleLog.info("您的机器码：" + machineCode);
            //获取键盘输入
            Scanner scanner = new Scanner(System.in);
            do {
                RcsLog.consoleLog.info(RcsLog.getTemplate(2), RcsLog.randomInt(), "请输入激活码（不分大小写）：");
                // 获取用户输入的内容
                String activationStr = scanner.nextLine();
                String decodeStr = Base32.decodeStr(activationStr);
                String[] split = decodeStr.split(",");
                String activationCode = split[0];
                if (split.length == 3) {
                    String expireDateStr = split[1];
                    String activationMd5 = split[2];
                    String md5 = SecureUtil.md5(StrUtil.format("{},{}{}", activationCode, expireDateStr, SECRET_KEY));
                    if (md5.equalsIgnoreCase(activationMd5)) {
                        if (isActivation(machineCode, activationCode)) {
                            Date expireDate = DateUtil.parse(expireDateStr);
                            //判断当前时间是否小于有效期
                            if (expireDate.after(new Date())) {
                                Entity entity = new Entity("RCS_ACTIVATION");
                                entity.set("MACHINE_CODE", machineCode);
                                entity.set("ACTIVATION_CODE", activationCode);
                                entity.set("EXPIRED_DATE", expireDate);
                                entity.set("SECURE_CODE", md5);
                                try {
                                    Integer insert = activationDB.insert(entity);
                                    if (insert > 0) {
                                        RcsLog.consoleLog.info(RcsLog.getTemplate(2), RcsLog.randomInt(), "激活成功");
                                        break;
                                    }
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), StrUtil.format("校验失败，您的激活码已过期：{}", expireDateStr));
                            }
                        }
                    } else {
                        RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), StrUtil.format("校验失败，激活码加密校验失败：{}", activationStr));
                    }
                }
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), StrUtil.format("校验失败，错误的激活码：{}", activationStr));
            } while (true);
        }
    }

    /**
     * 获取机器码
     *
     * @return 机器码
     */
    public String getMachineCode() {
        OsInfo osInfo = SystemUtil.getOsInfo();
        UserInfo userInfo = SystemUtil.getUserInfo();
        InetAddress localHost;
        try {
            localHost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        String osInfoName = osInfo.getName();
        String osInfoVersion = osInfo.getVersion();
        String osInfoArch = osInfo.getArch();
        String userInfoName = userInfo.getName();
        String hostInfoName = localHost.getHostName();
        // 激活盐值
        StringBuilder activeSalt = new StringBuilder();
        activeSalt.append("《翱翔之旅》- 唐珂 《御兽王者》主题曲");
        activeSalt.append("用阳光照亮着前路");
        activeSalt.append("追逐那梦想");
        activeSalt.append("一步一步");
        activeSalt.append("我迈向明天");
        activeSalt.append("用阳光来描写前路");
        activeSalt.append("满怀勇气信心");
        activeSalt.append("在无边无际");
        activeSalt.append("我展翅翱翔");
        activeSalt.append("让雨露风霜考验我的勇敢");
        activeSalt.append("让日月星辰见证我的成长");
        activeSalt.append("无尽困难即将来临");
        activeSalt.append("但我并不害怕");
        activeSalt.append("新的希望也必将来到");
        activeSalt.append("尽管以后有许多挑战");
        activeSalt.append("但我毫不退缩");
        activeSalt.append("用心中的画笔描绘自己的未来");

        String md5 = SecureUtil.md5(osInfoName + osInfoVersion + osInfoArch + userInfoName + hostInfoName + activeSalt + SECRET_KEY);
        String sha1 = SecureUtil.sha1(md5);

        HMac hmacMd5 = SecureUtil.hmacMd5(SECRET_KEY.getBytes());
        String hmacMd5Result = HexUtil.encodeHexStr(hmacMd5.digest((sha1 + osInfoVersion).getBytes()));
        HMac hmacSha1 = SecureUtil.hmacSha1(SECRET_KEY.getBytes());
        String hmacSha1Result = HexUtil.encodeHexStr(hmacSha1.digest(hmacMd5Result.getBytes()));
        HMac hmacSha256 = SecureUtil.hmacSha256(SECRET_KEY.getBytes());
        String hmacSha256Result = HexUtil.encodeHexStr(hmacSha256.digest((hmacSha1Result + osInfoArch).getBytes()));

        byte[] aesKey = HexUtil.decodeHex(hmacSha256Result.substring(0, 32));
        AES aes = new AES(aesKey);
        byte[] aesEncrypted = aes.encrypt(HexUtil.decodeHex(hmacSha256Result));
        DES des = new DES(aesEncrypted);
        byte[] desEncrypted = des.encrypt(aesEncrypted);

        String md5Hex1 = DigestUtil.md5Hex(desEncrypted);
        String md5Hex16 = DigestUtil.md5Hex16(md5Hex1 + userInfoName);
        String sha1Hex = DigestUtil.sha1Hex(md5Hex16 + SECRET_KEY);
        String sha256Hex = DigestUtil.sha256Hex(sha1Hex + hostInfoName);
        String sha512Hex = DigestUtil.sha512Hex(sha256Hex + SECRET_KEY);
        return SecureUtil.md5(sha512Hex).substring(4, 10);
    }

    /**
     * 根据机器码获取激活码
     *
     * @param machineCode 机器码
     * @return 激活码
     */
    public String getActivationCode(String machineCode) {
        String md5 = SecureUtil.md5(machineCode.toLowerCase() + SECRET_KEY);
        String sha1 = SecureUtil.sha1(extractString(md5, 9, 7, 5, 2, 4, 6));

        HMac hmacMd5 = SecureUtil.hmacMd5(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        String hmacMd5Result = HexUtil.encodeHexStr(hmacMd5.digest(extractString(sha1, 3, 2, 4, 1, 8, 3).getBytes(StandardCharsets.UTF_8)));
        HMac hmacSha1 = SecureUtil.hmacSha1(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        String hmacSha1Result = HexUtil.encodeHexStr(hmacSha1.digest(extractString(hmacMd5Result, 6, 5, 9, 4, 2, 8).getBytes(StandardCharsets.UTF_8)));
        HMac hmacSha256 = SecureUtil.hmacSha256(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        String hmacSha256Result = HexUtil.encodeHexStr(hmacSha256.digest(extractString(hmacSha1Result, 1, 8, 9, 3, 6, 1).getBytes(StandardCharsets.UTF_8)));

        byte[] aesKey = HexUtil.decodeHex(hmacSha256Result.substring(0, 32));
        AES aes = new AES(aesKey);
        byte[] aesEncrypted = aes.encrypt(HexUtil.decodeHex(hmacSha256Result));
        DES des = new DES(aesEncrypted);
        byte[] desEncrypted = des.encrypt(aesEncrypted);

        String md5Hex1 = DigestUtil.md5Hex(desEncrypted);
        String md5Hex16 = DigestUtil.md5Hex16(extractString(md5Hex1, 7, 1, 4, 5, 8, 7) + SECRET_KEY);
        String sha1Hex = DigestUtil.sha1Hex(extractString(md5Hex16, 3, 9, 8, 6, 1, 4) + SECRET_KEY);
        String sha256Hex = DigestUtil.sha256Hex(extractString(sha1Hex, 2, 4, 6, 3, 7, 8) + SECRET_KEY);
        String sha512Hex = DigestUtil.sha512Hex(extractString(sha256Hex, 8, 5, 2, 9, 4, 6) + SECRET_KEY);
        String md51 = SecureUtil.md5(extractString(sha512Hex, 3, 2, 7, 1, 9, 4));
        return extractString(md51, 6, 8, 3, 7, 2, 5);
    }

    /**
     * 是否激活成功
     *
     * @param machineCode 机器码
     * @param code        激活码
     */
    public boolean isActivation(String machineCode, String code) {
        String activationCode = getActivationCode(machineCode);
        return activationCode.equalsIgnoreCase(code);
    }

    /**
     * 从字符串中提取指定位数的字符串
     *
     * @param encrypt   字符串
     * @param positions 位数数组
     * @return 字符串
     */
    private String extractString(String encrypt, Integer... positions) {
        StringBuilder sb = new StringBuilder();
        for (int pos : positions) {
            if (pos < encrypt.length()) {
                sb.append(encrypt.charAt(pos));
            }
        }
        return sb.toString();
    }
}
