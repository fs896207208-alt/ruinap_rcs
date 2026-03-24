package com.ruinap.adapter.communicate.listener;

import com.ruinap.adapter.communicate.NettyManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.core.task.event.AgvCommandEvent;
import com.ruinap.infra.command.agv.AgvCommandService;
import com.ruinap.infra.config.LinkYaml;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.enums.task.CurrentPlanStateEnum;
import com.ruinap.infra.framework.annotation.Async;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.EventListener;
import com.ruinap.infra.log.RcsLog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import java.util.Map;

/**
 * AGV 通信网关：统一监听并下发指令
 *
 * @author qianye
 * @create 2026-03-02 14:47
 */
@Component
public class AgvCommandListener {

    @Autowired
    private LinkYaml linkYaml;
    @Autowired
    private NettyManager nettyManager;
    @Autowired
    private AgvCommandService agvCommandService;

    @Async
    @EventListener
    public void onAgvCommandDispatch(AgvCommandEvent event) {
        TaskPath taskPath = event.getTaskPath();
        RcsAgv agv = event.getAgv();
        String agvId = agv.getAgvId();
        AgvCommandEvent.CommandType commandType = event.getCommandType();
        ByteBuf out = null;

        try {
            // 1. 获取协议配置
            ProtocolEnum protocolEnum = getAgvProtocol(agvId);
            if (protocolEnum == null) {
                // 主动抛出异常，进入 catch 块统一处理回调
                throw new RuntimeException("未配置通信协议(LinkYaml)");
            }

            // 2. 申请池化内存 (Zero-GC 核心)
            out = PooledByteBufAllocator.DEFAULT.buffer();
            long mark = System.currentTimeMillis();
            String protocol = protocolEnum.getProtocol();

            // 3. 路由到强大的指令库 (核心策略分发)
            switch (commandType) {
                case MOVE -> agvCommandService.getAgvMove(protocol, out, taskPath, mark);
                case PAUSE -> agvCommandService.getAgvPause(protocol, out, taskPath, mark);
                case RESUME -> agvCommandService.getAgvResume(protocol, out, mark);
                case CANCEL -> agvCommandService.getAgvCancel(protocol, out, taskPath, mark);
                case INTERRUPT -> agvCommandService.getAgvInterrupt(protocol, out, taskPath, mark);
                default -> throw new IllegalArgumentException("未知的控制指令类型: " + commandType);
            }

            // 4. 推入底层网关发送 (Fire-and-Forget 模式)
            // 该方法内部自带查找 Channel 的能力
            nettyManager.sendMessage(protocolEnum, agvId, out);

            RcsLog.algorithmLog.info("{} [协议:{}] {} 指令报文已成功推入底层网关", agvId, protocol, commandType);

            // ==========================================
            // 5. 【核心】：回填 Future，唤醒业务线程！
            // 此时代表报文已经成功交给了 Netty 管道，指令下发环节已无异常。
            // ==========================================
            if (event.getAckFuture() != null) {
                event.getAckFuture().complete(true);
            }

        } catch (Exception e) {
            RcsLog.algorithmLog.error("{} 报文组装或发送时发生严重异常", agvId, e);

            // ==========================================
            // 6. 【核心】：回填异常，解除前端 Controller 的死等！
            // ==========================================
            if (event.getAckFuture() != null) {
                event.getAckFuture().completeExceptionally(e);
            }

            // 7. 补偿事务：回滚调度机状态
            handleSendFailure(agvId, taskPath, commandType, e.getMessage());

            // 8. 防御性内存释放 (极其重要)
            if (out != null && out.refCnt() > 0) {
                out.release();
            }
        }
    }

    /**
     * 解析 AGV 的协议类型
     */
    private ProtocolEnum getAgvProtocol(String agvId) {
        Map<String, Map<String, String>> agvLinks = linkYaml.getAgvLink();
        if (agvLinks != null && agvLinks.containsKey(agvId)) {
            String pact = agvLinks.get(agvId).get("pact");
            return ProtocolEnum.fromProtocol(pact);
        }
        return null;
    }

    /**
     * 补偿事务：精准回滚调度状态
     */
    private void handleSendFailure(String agvId, TaskPath taskPath, AgvCommandEvent.CommandType commandType, String reason) {
        // 只有 MOVE (包含路径下发) 指令失败时，才需要回滚 currentPlan 让状态机重新算路
        if (AgvCommandEvent.CommandType.MOVE.equals(commandType) && taskPath != null) {
            // 让它立刻重算，就用 REQUIRE_PLAN(0)
            int fallbackState = CurrentPlanStateEnum.REQUIRE_PLAN.getCode();
            RcsLog.algorithmLog.warn("{} MOVE指令发送失败: {}, 将任务调度状态回滚为 {}", agvId, reason, fallbackState);
            taskPath.setCurrentPlan(fallbackState);
        } else {
            RcsLog.algorithmLog.error("{} {} 指令发送失败: {}", agvId, commandType, reason);
        }
    }
}