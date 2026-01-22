package com.ruinap.core.task.structure.distribution;


import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;

/**
 * 不是新任务状态处理类
 *
 * @author qianye
 * @create 2025-03-11 09:57
 */
@Component
public class NotNewTaskStateHandle implements TaskStateHandle {

    @Autowired
    private AgvManager agvManager;


    /**
     * 处理不是新任务状态
     *
     * @param rcsTask 任务
     * @return AGV
     */
    @Override
    public RcsAgv handle(RcsTask rcsTask) {
        //获取AGV
        RcsAgv rcsAgv = agvManager.getRcsAgvByCode(rcsTask.getEquipmentCode());
//        if (rcsAgv != null) {
//            //校验分段任务和AGV车体任务是否匹配
//            //获取AGV车体任务状态
//            Integer goodsState = rcsAgv.getGoodsState();
//            //获取任务号
//            String taskId = rcsAgv.getTaskId();
//            //获取AGV任务状态 0无任务 1有任务 2已完成 3已取消
//            Integer taskState = rcsAgv.getTaskState();
//            //判断AGV车体任务状态是否匹配
//            if (taskId.contains(rcsTask.getTaskCode())) {
//                //判断AGV任务状态是否完成
//                if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.FINISH, taskState)) {
//
//                }
//                //判断AGV任务状态是否取消
//                if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.CANCEL, taskState)) {
//
//                }
//                //判断AGV任务状态是否有任务
//                if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, taskState)) {
//                    //判断AGV任务执行到哪一步
//
//                }
//            }
//        }
        return rcsAgv;
    }

}