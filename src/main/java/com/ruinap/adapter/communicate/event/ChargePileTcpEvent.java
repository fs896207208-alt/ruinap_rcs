package com.ruinap.adapter.communicate.event;

import com.slamopto.common.base.BaseConversionUtils;
import com.slamopto.communicate.base.ClientAttribute;
import com.slamopto.communicate.base.event.AbstractClientEvent;
import com.slamopto.db.DbCache;
import com.slamopto.equipment.domain.RcsChargePile;
import com.slamopto.log.RcsLog;

import java.util.List;

/**
 * 充电桩事件
 *
 * @author qianye
 * @create 2025-09-27 15:00
 */
public class ChargePileTcpEvent extends AbstractClientEvent<byte[]> {

    /**
     * 接收消息
     *
     * @param attribute 属性
     * @param frame     消息帧
     */
    @Override
    public void receiveMessage(ClientAttribute attribute, byte[] frame) {
        // 数据转换
        List<String> datas = BaseConversionUtils.bytesToHex(frame);
        if (datas.size() != 10) {
            return;
        }
        // 客户端ID
        String clientId = attribute.getClientId();
        // 请求ID
        long requestId = attribute.getRequestId().incrementAndGet();
        // 记录日志
        RcsLog.communicateLog.error(RcsLog.formatTemplate(clientId, "rec_data", requestId, datas));

        //获取充电桩输出电压
        String hexVoltage = String.join("", datas.subList(2, 4));
        String voltage = BaseConversionUtils.hexStringToDecimal(hexVoltage);
        //获取充电桩输出电流
        String hexCurrent = String.join("", datas.subList(4, 6));
        String current = BaseConversionUtils.hexStringToDecimal(hexCurrent);
        //获取充电桩状态 0待机 1曲线充电 2正常充电 3充电完成 4强制充电 5错误 6其他
        String hexState = String.join("", datas.subList(6, 7));
        String binaryState = BaseConversionUtils.hexStringToDecimal(hexState);
        //获取充电桩错误信息 0没有错误 1电池低压 2电池反接 3电池高压
        String hexError = String.join("", datas.subList(7, 8));
        String binaryhexError = BaseConversionUtils.hexStringToDecimal(hexError);

        //获取充电桩信息
        RcsChargePile rcsChargePile = DbCache.RCS_CHARGE_MAP.get(clientId);
        if (rcsChargePile != null) {
            //模式 -1离线 0自动 1手动
            int mode = -1;
            //空闲状态 -1未知 0空闲 1占用
            int idleState = -1;
            //计算空闲状态
            if ("0".equals(binaryState) || "3".equals(binaryState)) {
                mode = 0;
                idleState = 0;
            } else if ("1".equals(binaryState) || "2".equals(binaryState) || "4".equals(binaryState)) {
                mode = 0;
                idleState = 1;
            }

            //状态 0离线 1在线
            rcsChargePile.setState(1);
            rcsChargePile.setMode(mode);
            rcsChargePile.setIdleState(idleState);
            rcsChargePile.setVoltage(Integer.parseInt(voltage));
            rcsChargePile.setCurrent(Integer.parseInt(current));
        }
    }
}
