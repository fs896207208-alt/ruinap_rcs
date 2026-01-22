package com.ruinap.infra.util;


import java.util.ArrayList;
import java.util.List;

/**
 * 进制转换类
 *
 * @author qianye
 * @create 2024-06-12 17:45
 */
public class BaseConversionUtils {

    /**
     * 用于将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    public static List<String> bytesToHex(byte[] bytes) {
        List<String> hexs = new ArrayList<>();
        for (byte b : bytes) {
            hexs.add(String.format("%02X", b));
        }
        return hexs;
    }

    /**
     * 用于将十六进制字符串转换为字节数组
     *
     * @param hexStrList 十六进制字符串
     * @return 字节数组
     */
    public static byte[] hexToBytes(List<String> hexStrList) {
        if (hexStrList == null || hexStrList.isEmpty()) {
            throw new RuntimeException("传入的列表为空");
        }

        int len = hexStrList.size();
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            String hexStr = hexStrList.get(i);
            if (hexStr.length() != 2) {
                throw new RuntimeException("列表中的字符串不是有效的两字符十六进制字符串: " + hexStr);
            }
            data[i] = (byte) ((Character.digit(hexStr.charAt(0), 16) << 4)
                    + Character.digit(hexStr.charAt(1), 16));
        }
        return data;
    }

    /**
     * 将点数转换为十六进制的固定点数字符串
     *
     * @param numberStr 数据
     * @return 十六进制的数据
     */
    public static String numberStringToHex(String numberStr) {
        int number = Integer.parseInt(numberStr);
        return Integer.toHexString(number).toUpperCase();
    }

    /**
     * 将十六进制字符串转换为十进制的字符串
     *
     * @param hexStr 十六进制字符串
     * @return 十进制字符串
     */
    public static String hexStringToDecimal(String hexStr) {
        try {
            int number = Integer.parseInt(hexStr, 16);
            return String.valueOf(number);
        } catch (NumberFormatException e) {
            throw new RuntimeException("无效的十六进制字符串");
        }
    }


    /**
     * 将十六进制字符串转换为二进制的字符串
     *
     * @param hexStr 十六进制字符串
     * @return 二进制字符串
     */
    public static String hexStringToBinary(String hexStr) {
        StringBuilder binary = new StringBuilder();
        for (char c : hexStr.toCharArray()) {
            int decimal = Integer.parseInt(String.valueOf(c), 16);
            binary.append(String.format("%4s", Integer.toBinaryString(decimal)).replace(' ', '0'));
        }
        return binary.toString();
    }
}
