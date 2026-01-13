package com.ruinap.infra.command.charge;

/**
 * 充电桩指令库
 *
 * @author qianye
 * @create 2025-09-28 12:59
 */
public class ChargePileCommand {

    /**
     * 获取充电桩状态
     *
     * @return 指令
     */
    public static String getChargePileState() {
        String command = "10 53 FF FF FF FF 01 42 03";
        return command;
    }
}
