package com.ruinap.core.task.structure.taskmode;


/**
 * 任务模式工厂
 *
 * @author qianye
 * @create 2025-03-11 09:59
 */
public class TaskModeFactory {

    /**
     * 获取任务模式工厂
     *
     * @return 任务模式处理器
     */
    public static TaskModeHandle getTaskModeFactory(Integer mode) {
        //-1自定义 0距离优先 1电量优先 2最近停靠优先 3起点指定 4终点指定 5起点区域指定 6终点区域指定
        return switch (mode) {
            case -1 -> new CustomizeMode();
            case 1 -> new PowerPriorityMode();
            case 2 -> new RecentParkMode();
            case 3 -> new OriginSpecifyMode();
            case 4 -> new DestinSpecifyMode();
            case 5 -> new OriginAreaSpecifyMode();
            case 6 -> new DestinAreaSpecifyMode();
            default -> new DistancePriorityMode();
        };
    }
}
