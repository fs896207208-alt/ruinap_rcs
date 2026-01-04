package com.ruinap.infra.framework.event;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.framework.core.ApplicationContext;

/**
 * @author qianye
 * @create 2025-12-12 10:30
 */
@Service
public class AgvTaskService {

    @Autowired
    private ApplicationContext context;

    public void updateTaskStatus(String taskCode, String agvCode, AgvTaskEvent.TaskStatus newStatus, String msg) {
        System.out.println(String.format(">> [Service] 数据库更新: 任务 %s -> %s", taskCode, newStatus));

        // 发布事件
        context.publishEvent(new AgvTaskEvent(this, taskCode, agvCode, newStatus, msg));
    }
}
