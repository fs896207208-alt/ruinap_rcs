package com.ruinap.core.task.structure;

import com.ruinap.core.algorithm.SlideTimeWindow;
import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.algorithm.search.RcsAstarSearch;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.core.task.structure.auction.BidResult;
import com.ruinap.infra.config.InteractionYaml;
import com.ruinap.infra.config.MapYaml;
import com.ruinap.infra.config.pojo.interactions.*;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.enums.task.DockTaskTypeEnum;
import com.ruinap.infra.enums.task.SubTaskTypeEnum;
import com.ruinap.infra.enums.task.TaskTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TaskSectionManage 单元测试
 * <p>
 * 覆盖分支：
 * 测试模块划分：
 * 第一部分：缓存与状态管理测试 (Cache Management)
 * 第二部分：宏观调度入口综合测试 (getNewTaskSection)
 * 第三部分：电梯微观白盒断点续传测试 (Elevator Section)
 * 第四部分：风淋室微观白盒断点续传测试 (AirShower Section)
 * </p>
 *
 * @author qianye
 * @create 2026-02-04 17:42
 */
@ExtendWith(MockitoExtension.class)
class TaskSectionManagerTest {

    private TaskSectionManager taskSectionManager;

    @Mock
    private MapManager mapManager;
    @Mock
    private SlideTimeWindow slideTimeWindow;
    @Mock
    private AlarmManager alarmManager;
    @Mock
    private InteractionYaml interactionYaml;
    @Mock
    private MapYaml mapYaml;
    @Mock
    private AgvManager agvManager;
    @Mock
    private RcsAstarSearch rcsAstarSearch;

    @Mock
    private RcsTask mockTask;
    @Mock
    private RcsAgv mockAgv;
    @Mock
    private RcsPoint originPoint;
    @Mock
    private RcsPoint destinPoint;
    @Mock
    private RcsPoint currentPoint;

    private final String AGV_ID = "AGV_001";
    private final Integer MAP_ID = 1;
    private final Integer POINT_ID = 100;
    private final String ORIGIN_ALIAS = "P_ORIGIN";
    private final String DESTIN_ALIAS = "P_DESTIN";

    @BeforeEach
    void setUp() throws Exception {
        taskSectionManager = new TaskSectionManager();
        injectField(taskSectionManager, "mapManager", mapManager);
        injectField(taskSectionManager, "slideTimeWindow", slideTimeWindow);
        injectField(taskSectionManager, "alarmManager", alarmManager);
        injectField(taskSectionManager, "interactionYaml", interactionYaml);
        injectField(taskSectionManager, "mapYaml", mapYaml);
        injectField(taskSectionManager, "agvManager", agvManager);
        injectField(taskSectionManager, "rcsAstarSearch", rcsAstarSearch);

        lenient().when(mockAgv.getAgvId()).thenReturn(AGV_ID);
        lenient().when(mockAgv.getMapId()).thenReturn(MAP_ID);
        lenient().when(mockAgv.getPointId()).thenReturn(POINT_ID);

        lenient().when(mockTask.getTaskCode()).thenReturn("TASK_CODE_001");
        lenient().when(mockTask.getTaskGroup()).thenReturn("GROUP_001");
        lenient().when(mockTask.getId()).thenReturn(1001);
        lenient().when(mockTask.getOrigin()).thenReturn(ORIGIN_ALIAS);
        lenient().when(mockTask.getDestin()).thenReturn(DESTIN_ALIAS);
        lenient().when(mockTask.getPalletType()).thenReturn(1);
    }

    // =================================================================================================
    //                                  第一部分：缓存与状态管理测试
    // =================================================================================================

    @Test
    @DisplayName("缓存管理: 测试 putTaskSections(Map) 和 getTaskSections")
    void testCache_PutAndGetMap() {
        Map<String, List<TaskPath>> map = new HashMap<>();
        TaskPath p1 = new TaskPath();
        p1.setTaskId(1);
        map.put("AGV_TEST_1", Collections.singletonList(p1));

        // 放入全量Map
        taskSectionManager.putTaskSections(map);

        // 获取并验证
        List<TaskPath> result = taskSectionManager.getTaskSections("AGV_TEST_1");
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getTaskId());

        // 获取不存在的AGV，应返回空集合而不是null
        List<TaskPath> emptyResult = taskSectionManager.getTaskSections("AGV_TEST_NONE");
        assertNotNull(emptyResult);
        assertTrue(emptyResult.isEmpty());
    }

    @Test
    @DisplayName("缓存管理: 测试 removeTaskSections (移除全量任务)")
    void testCache_RemoveTaskSections() {
        taskSectionManager.putTaskSections(AGV_ID, Arrays.asList(new TaskPath(), new TaskPath()));
        assertEquals(2, taskSectionManager.getTaskSections(AGV_ID).size());

        List<TaskPath> removed = taskSectionManager.removeTaskSections(AGV_ID);
        assertNotNull(removed);
        assertEquals(2, removed.size());

        // 再次获取应该为空
        assertTrue(taskSectionManager.getTaskSections(AGV_ID).isEmpty());
    }

    @Test
    @DisplayName("缓存管理: 测试 removeFirst (JDK21 List新特性及Entry清理)")
    void testCache_RemoveFirst() {
        TaskPath p1 = new TaskPath();
        p1.setSubTaskNo(1);
        TaskPath p2 = new TaskPath();
        p2.setSubTaskNo(2);

        // 放入两条任务
        taskSectionManager.putTaskSections(AGV_ID, new ArrayList<>(Arrays.asList(p1, p2)));

        // 第一次移除
        TaskPath removed1 = taskSectionManager.removeFirst(AGV_ID);
        assertNotNull(removed1);
        assertEquals(1, removed1.getSubTaskNo());
        assertEquals(1, taskSectionManager.getTaskSections(AGV_ID).size(), "缓存中应只剩1条");

        // 第二次移除（触发自动清理空Entry逻辑）
        TaskPath removed2 = taskSectionManager.removeFirst(AGV_ID);
        assertNotNull(removed2);
        assertEquals(2, removed2.getSubTaskNo());

        // 缓存中应已被完全清理
        assertNull(taskSectionManager.getTaskSectionCache().get(AGV_ID), "空List所在的Entry应被移除");

        // 第三次移除（防空指针保护测试）
        TaskPath removed3 = taskSectionManager.removeFirst(AGV_ID);
        assertNull(removed3);
    }

    @Test
    @DisplayName("缓存管理: 测试 getTaskSection (仅获取第一条不移除)")
    void testCache_GetTaskSection() {
        assertNull(taskSectionManager.getTaskSection(AGV_ID), "空缓存获取应返回null");

        TaskPath p1 = new TaskPath();
        p1.setSubTaskNo(1);
        taskSectionManager.putTaskSections(AGV_ID, Collections.singletonList(p1));

        TaskPath get1 = taskSectionManager.getTaskSection(AGV_ID);
        assertNotNull(get1);
        assertEquals(1, get1.getSubTaskNo());
        // 确保没有被移除
        assertEquals(1, taskSectionManager.getTaskSections(AGV_ID).size());
    }

    // =================================================================================================
    //                                  第二部分：宏观调度入口综合测试 (getNewTaskSection)
    // =================================================================================================

    @Test
    @DisplayName("宏观调度: 缓存命中短路机制 - AGV已有任务直接返回第一条")
    void testGetNewTaskSection_CacheHit() {
        TaskPath cachedTask = new TaskPath();
        cachedTask.setAgvId(AGV_ID);
        cachedTask.setSubTaskNo(999);
        taskSectionManager.putTaskSections(AGV_ID, Collections.singletonList(cachedTask));

        BidResult bidResult = new BidResult(mockAgv, mockTask, 0, null);
        TaskPath result = taskSectionManager.getNewTaskSection(bidResult);

        assertNotNull(result);
        assertEquals(999, result.getSubTaskNo());
        verify(mapManager, never()).getPointByAlias(anyString());
    }

    @Test
    @DisplayName("宏观调度: 异常防御 - 起点丢失触发 E11001 告警")
    void testGetNewTaskSection_NullOrigin() {
        when(mapManager.getPointByAlias(ORIGIN_ALIAS)).thenReturn(null);

        BidResult bidResult = new BidResult(mockAgv, mockTask, 0, null);
        TaskPath result = taskSectionManager.getNewTaskSection(bidResult);

        assertNull(result);
        verify(alarmManager).triggerAlarm(AGV_ID, "GROUP_001", "TASK_CODE_001", AlarmCodeEnum.E11001, ORIGIN_ALIAS, "rcs");
    }

    @Test
    @DisplayName("宏观调度: 异常防御 - 终点丢失触发 E11001 告警")
    void testGetNewTaskSection_NullDestin() {
        when(mapManager.getPointByAlias(ORIGIN_ALIAS)).thenReturn(originPoint);
        when(mapManager.getPointByAlias(DESTIN_ALIAS)).thenReturn(null);

        BidResult bidResult = new BidResult(mockAgv, mockTask, 0, null);
        TaskPath result = taskSectionManager.getNewTaskSection(bidResult);

        assertNull(result);
        verify(alarmManager).triggerAlarm(AGV_ID, "GROUP_001", "TASK_CODE_001", AlarmCodeEnum.E11001, DESTIN_ALIAS, "rcs");
    }

    @Test
    @DisplayName("宏观调度: 常规搬运任务降级使用通用拆分 (Fallback)")
    void testGetNewTaskSection_CommonCarryFallback() {
        setupBasicPoints();
        when(mockTask.getTaskType()).thenReturn(TaskTypeEnum.CARRY.code);
        // 当前位置不同于起点
        when(mapManager.getRcsPoint(MAP_ID, POINT_ID)).thenReturn(currentPoint);
        mockEmptyAstarAndInteractions();

        BidResult bidResult = new BidResult(mockAgv, mockTask, 0, null);
        TaskPath result = taskSectionManager.getNewTaskSection(bidResult);

        assertNotNull(result);
        List<TaskPath> paths = taskSectionManager.getTaskSections(AGV_ID);
        assertEquals(2, paths.size(), "不同起点，应生成2段通用任务");
        assertEquals(SubTaskTypeEnum.ORIGIN.code, paths.get(0).getSubTaskType());
        assertEquals(SubTaskTypeEnum.DESTIN.code, paths.get(1).getSubTaskType());
    }

    @Test
    @DisplayName("宏观调度: 输送线对接 - 卸货模式(Destin)触发拆分")
    void testGetNewTaskSection_ConveyorDocking() {
        setupBasicPoints();
        when(mockTask.getTaskType()).thenReturn(TaskTypeEnum.CARRY.code);
        when(mapManager.getRcsPoint(MAP_ID, POINT_ID)).thenReturn(originPoint);

        mockEmptyAstarAndInteractions();

        HandoverDeviceEntity conveyor = new HandoverDeviceEntity();
        conveyor.setCode("CV_01");
        conveyor.setDockingPoint("P_DOCK");
        conveyor.setUnloadMode(1); // 开启卸货拆分
        conveyor.setDockingAdvance(500);

        // 模拟终点是输送线
        when(interactionYaml.getHandoverDeviceByRelevancyPoint(destinPoint)).thenReturn(conveyor);

        RcsPoint dockPoint = createPoint(9001, "P_DOCK", 1);
        when(mapManager.getPointByAlias("P_DOCK")).thenReturn(dockPoint);
        when(agvManager.getRcsAgvByCode(AGV_ID)).thenReturn(mockAgv);

        BidResult bidResult = new BidResult(mockAgv, mockTask, 0, null);
        taskSectionManager.getNewTaskSection(bidResult);

        List<TaskPath> paths = taskSectionManager.getTaskSections(AGV_ID);
        // 预期生成2段：移动到P_DOCK，执行CV_01对接
        assertEquals(2, paths.size());
        assertEquals("CV_01", paths.get(1).getDockDevice().getEquipmentId());
        assertEquals(DockTaskTypeEnum.CONVEYORLINE.code, paths.get(1).getDockDevice().getDockType());
    }

    // =================================================================================================
    //                                  第三部分：电梯微观白盒断点续传测试
    // =================================================================================================

    private List<RcsPoint> setupElevatorEnvironment(RcsPoint currentPos, RcsPoint waitPoint, RcsPoint doorPoint,
                                                    RcsPoint innerStart, RcsPoint innerEnd, RcsPoint finalPoint) {
        lenient().when(mapManager.getPointByAlias("P_Wait")).thenReturn(waitPoint);
        // [修复点] 与 occupancys 完全一致
        lenient().when(mapManager.getPointByAlias("1-P_Door")).thenReturn(doorPoint);
        lenient().when(mapManager.getPointByAlias("P_Inner_Start")).thenReturn(innerStart);
        lenient().when(mapManager.getPointByAlias("P_Inner_End")).thenReturn(innerEnd);

        LinkedHashMap<String, String> bridgeMap = new LinkedHashMap<>();
        bridgeMap.put("equipment_code", "ELEVATOR_01");
        bridgeMap.put("bridge_id", "teleport_1_2");
        bridgeMap.put("origin", "P_Wait");
        bridgeMap.put("destin", "P_Wait");
        lenient().when(mapManager.getTargetBridgePoint(anyList())).thenReturn(Collections.singletonList(bridgeMap));

        ElevatorEntity elevator = new ElevatorEntity();
        elevator.setCode("ELEVATOR_01");
        elevator.setOccupancys(Collections.singletonList("front_door,1-P_Door"));
        lenient().when(interactionYaml.getElevatorByCode("ELEVATOR_01")).thenReturn(elevator);

        InteractionsBody interactionsBody = new InteractionsBody();
        InteractionBody startBody = new InteractionBody();
        startBody.setPoint("P_Inner_Start");
        startBody.setDoor(Collections.singletonList("front_door"));
        interactionsBody.setStart(startBody);

        InteractionBody endBody = new InteractionBody();
        endBody.setPoint("P_Inner_End");
        endBody.setDoor(Collections.singletonList("front_door"));
        interactionsBody.setEnd(endBody);

        Map<String, InteractionsBody> interactionsMap = new HashMap<>();
        interactionsMap.put("teleport_1_2", interactionsBody);
        elevator.setInteractions(interactionsMap);

        return Arrays.asList(currentPos, waitPoint, innerStart, innerEnd, finalPoint);
    }

    @Test
    @DisplayName("电梯白盒: 正常流程 - AGV在远处，需生成[移动->电梯->移动]")
    void testSectionElevator_NormalFlow() throws Exception {
        RcsPoint pFar = createPoint(1001, "P_Far", 1);
        RcsPoint pWait = createPoint(1002, "P_Wait", 1);
        RcsPoint pInnerStart = createPoint(1003, "P_Inner_Start", 1);
        RcsPoint pInnerEnd = createPoint(2001, "P_Inner_End", 2);
        RcsPoint pFinal = createPoint(2002, "P_Final", 2);

        List<RcsPoint> fullPaths = setupElevatorEnvironment(pFar, pWait, new RcsPoint(), pInnerStart, pInnerEnd, pFinal);

        List<TaskPath> result = invokeSectionElevatorTask(pFar, pFinal, fullPaths);

        assertNotNull(result);
        assertEquals(3, result.size(), "应生成3段任务: 移到门口 -> 坐电梯 -> 移到终点");

        TaskPath step1 = result.get(0);
        assertEquals(pWait, step1.getTaskDestin());
        assertFalse(step1.getExpectRoutes().isEmpty());

        TaskPath step2 = result.get(1);
        assertEquals(pWait, step2.getTaskOrigin());
        assertNotNull(step2.getDockDevice());
        assertEquals(DockTaskTypeEnum.ELEVATOR.code, step2.getDockDevice().getDockType());

        TaskPath step3 = result.get(2);
        assertEquals(pFinal, step3.getTaskDestin());
    }

    @Test
    @DisplayName("电梯白盒: 断点续传 - AGV已在电梯口(WaitPoint)，跳过移动")
    void testSectionElevator_ResumeAtEntrance() throws Exception {
        RcsPoint pWait = createPoint(1002, "P_Wait", 1);
        RcsPoint pInnerStart = createPoint(1003, "P_Inner_Start", 1);
        RcsPoint pInnerEnd = createPoint(2001, "P_Inner_End", 2);
        RcsPoint pFinal = createPoint(2002, "P_Final", 2);

        List<RcsPoint> fullPaths = setupElevatorEnvironment(pWait, pWait, new RcsPoint(), pInnerStart, pInnerEnd, pFinal);

        List<TaskPath> result = invokeSectionElevatorTask(pWait, pFinal, fullPaths);

        assertEquals(2, result.size(), "已在等待点，应只有2段任务");
        assertEquals(pWait, result.get(0).getTaskOrigin());
    }

    @Test
    @DisplayName("电梯白盒: 断点续传 - AGV已在门区(Occupancy)，跳过移动并动态修正起点")
    void testSectionElevator_ResumeAtDoor() throws Exception {
        RcsPoint pDoor = createPoint(1005, "P_Door", 1);
        RcsPoint pWait = createPoint(1002, "P_Wait", 1);
        RcsPoint pInnerStart = createPoint(1003, "P_Inner_Start", 1);
        RcsPoint pInnerEnd = createPoint(2001, "P_Inner_End", 2);
        RcsPoint pFinal = createPoint(2002, "P_Final", 2);

        List<RcsPoint> fullPaths = setupElevatorEnvironment(pDoor, pWait, pDoor, pInnerStart, pInnerEnd, pFinal);

        List<TaskPath> result = invokeSectionElevatorTask(pDoor, pFinal, fullPaths);

        assertEquals(2, result.size());
        assertEquals(pDoor, result.get(0).getTaskOrigin(), "起点必须动态修正为AGV所处的门点");
    }

    @Test
    @DisplayName("电梯白盒: 断点续传 - AGV已在轿厢内(InnerStart)，跳过移动并动态修正起点")
    void testSectionElevator_ResumeInside() throws Exception {
        RcsPoint pWait = createPoint(1002, "P_Wait", 1);
        RcsPoint pInnerStart = createPoint(1003, "P_Inner_Start", 1);
        RcsPoint pInnerEnd = createPoint(2001, "P_Inner_End", 2);
        RcsPoint pFinal = createPoint(2002, "P_Final", 2);

        List<RcsPoint> fullPaths = setupElevatorEnvironment(pInnerStart, pWait, new RcsPoint(), pInnerStart, pInnerEnd, pFinal);

        List<TaskPath> result = invokeSectionElevatorTask(pInnerStart, pFinal, fullPaths);

        assertEquals(2, result.size());
        assertEquals(pInnerStart, result.get(0).getTaskOrigin(), "起点必须动态修正为轿厢内点");
    }

    @Test
    @DisplayName("电梯白盒: 防御机制 - 楼层相同直接短路返回空集合")
    void testSectionElevator_SameFloor() throws Exception {
        RcsPoint p1 = createPoint(1001, "P1", 1);
        RcsPoint p2 = createPoint(1002, "P2", 1);

        List<TaskPath> result = invokeSectionElevatorTask(p1, p2, Arrays.asList(p1, p2));
        assertTrue(result.isEmpty(), "楼层一致(MapId=1)应该直接跳过电梯拆分");
    }

    // =================================================================================================
    //                                  第四部分：风淋室微观白盒断点续传测试
    // =================================================================================================

    private List<RcsPoint> setupAirShowerEnvironment(RcsPoint currentPos, RcsPoint startPoint, RcsPoint centerPoint, RcsPoint endPoint, RcsPoint finalPoint) {
        lenient().when(mapManager.getPointByAlias("P_AS_Start")).thenReturn(startPoint);
        lenient().when(mapManager.getPointByAlias("P_AS_Center")).thenReturn(centerPoint);
        lenient().when(mapManager.getPointByAlias("P_AS_End")).thenReturn(endPoint);

        lenient().when(mapManager.getPointByAlias("1-P_Door_Front_Occupy")).thenReturn(startPoint);
        lenient().when(mapManager.getPointByAlias("1-P_Door_Back_Occupy")).thenReturn(endPoint);

        AirShowerEntity airShower = new AirShowerEntity();
        airShower.setCode("AS_DEVICE_01");
        airShower.setPoint("P_AS_Center");
        airShower.setOccupancys(Arrays.asList(
                "door_front,1-P_Door_Front_Occupy",
                "door_back,1-P_Door_Back_Occupy"
        ));

        Map<String, AirShowerInteractionBody> interactions = new HashMap<>();
        AirShowerInteractionBody body = new AirShowerInteractionBody();
        InteractionBody startBody = new InteractionBody();
        startBody.setPoint("P_AS_Start");
        startBody.setDoor(Collections.singletonList("door_front"));
        body.setStart(startBody);

        InteractionBody endBody = new InteractionBody();
        endBody.setPoint("P_AS_End");
        endBody.setDoor(Collections.singletonList("door_back"));
        body.setEnd(endBody);

        interactions.put("AS_Interaction_Front", body);
        airShower.setInteractions(interactions);

        lenient().when(interactionYaml.getAirShowers()).thenReturn(Collections.singletonList(airShower));

        List<RcsPoint> pathList;
        if (currentPos.equals(centerPoint)) {
            pathList = Arrays.asList(centerPoint, endPoint, finalPoint);
        } else if (currentPos.equals(startPoint)) {
            pathList = Arrays.asList(startPoint, centerPoint, endPoint, finalPoint);
        } else {
            pathList = Arrays.asList(currentPos, startPoint, centerPoint, endPoint, finalPoint);
        }
        return pathList;
    }

    @Test
    @DisplayName("风淋室白盒: 正常流程 - AGV从远处过来 -> 移动到门口 -> 穿越 -> 移动到终点")
    void testSectionAirShower_NormalFlow() throws Exception {
        RcsPoint pFar = createPoint(3001, "P_Far", 1);
        RcsPoint pStart = createPoint(3002, "P_AS_Start", 1);
        RcsPoint pCenter = createPoint(3003, "P_AS_Center", 1);
        RcsPoint pEnd = createPoint(3004, "P_AS_End", 1);
        RcsPoint pFinal = createPoint(3005, "P_Final", 1);

        List<RcsPoint> expectedPaths = setupAirShowerEnvironment(pFar, pStart, pCenter, pEnd, pFinal);

        TaskPath originalPath = new TaskPath();
        originalPath.setAgvId("AGV_TEST");
        originalPath.setSubTaskType(SubTaskTypeEnum.DESTIN.code);
        originalPath.setTaskOrigin(pFar);
        originalPath.setTaskDestin(pFinal);
        originalPath.setExpectRoutes(new ArrayList<>(expectedPaths)); // 注入全路径切片

        List<TaskPath> result = invokeSectionAirShowersTask(mockTask, Collections.singletonList(originalPath));

        assertNotNull(result);
        assertEquals(3, result.size(), "应生成3段: 移动至门口 -> 穿越风淋室 -> 移动至终点");

        assertEquals(pStart, result.get(0).getTaskDestin());
        TaskPath showerTask = result.get(1);
        assertEquals(DockTaskTypeEnum.AIRSHOWER.code, showerTask.getDockDevice().getDockType());
        assertEquals(pCenter, showerTask.getDockDevice().getEquipmentPoint());
        assertFalse(showerTask.getExpectRoutes().isEmpty(), "风淋室段应携带专属的路径切片");
    }

    @Test
    @DisplayName("风淋室白盒: 断点续传 - AGV在内部重启 -> 跳过移动段")
    void testSectionAirShower_ResumeInside() throws Exception {
        RcsPoint pStart = createPoint(5043, "1-43", 1);
        RcsPoint pCenter = createPoint(5025, "1-25", 1); // 当前位置
        RcsPoint pEnd = createPoint(5023, "1-23", 1);
        RcsPoint pFinal = createPoint(6001, "P_Final", 1);

        List<RcsPoint> expectedPaths = setupAirShowerEnvironment(pCenter, pStart, pCenter, pEnd, pFinal);

        TaskPath originalPath = new TaskPath();
        originalPath.setAgvId("AGV_TEST");
        originalPath.setSubTaskType(SubTaskTypeEnum.DESTIN.code);
        originalPath.setTaskOrigin(pCenter);
        originalPath.setTaskDestin(pFinal);
        originalPath.setExpectRoutes(new ArrayList<>(expectedPaths));

        List<TaskPath> result = invokeSectionAirShowersTask(mockTask, Collections.singletonList(originalPath));

        assertEquals(2, result.size(), "已在内部，应跳过移动段");
        assertEquals(pStart, result.get(0).getTaskOrigin(), "风淋室任务标准起点应回溯为配置入口点");
    }

    @Test
    @DisplayName("风淋室白盒: 防御机制 - 非前往起/终点的中间任务不拆分")
    void testSectionAirShower_SkipMiddleTask() throws Exception {
        TaskPath path = new TaskPath();
        path.setSubTaskType(999); // 非ORIGIN/DESTIN

        List<TaskPath> result = invokeSectionAirShowersTask(mockTask, Collections.singletonList(path));
        assertEquals(1, result.size(), "非主线任务直接原样返回");
        assertEquals(999, result.get(0).getSubTaskType());
    }

    // =================================================================================================
    //                                  底层辅助方法区
    // =================================================================================================

    private RcsPoint createPoint(int id, String name, int mapId) {
        RcsPoint p = new RcsPoint();
        p.setId(id);
        p.setName(name);
        p.setMapId(mapId);
        return p;
    }

    private void setupBasicPoints() {
        when(mapManager.getPointByAlias(ORIGIN_ALIAS)).thenReturn(originPoint);
        when(mapManager.getPointByAlias(DESTIN_ALIAS)).thenReturn(destinPoint);
        Map<String, Integer> mockParams = new HashMap<>();
        mockParams.put("action", 0);
        mockParams.put("parameter", 0);
        lenient().when(mapManager.getOriginActionParameter(anyString(), anyString(), anyInt(), anyInt())).thenReturn(mockParams);
        lenient().when(mapManager.getDestinActionParameter(anyString(), anyString(), anyInt(), anyInt())).thenReturn(mockParams);
    }

    private void mockEmptyAstarAndInteractions() {
        RouteResult routeResult = new RouteResult(true, 10, Arrays.asList(originPoint, destinPoint));
        lenient().when(rcsAstarSearch.aStarSearch(anyString(), any(), any())).thenReturn(routeResult);
        lenient().when(mapManager.getTargetBridgePoint(anyList())).thenReturn(Collections.emptyList());
        lenient().when(interactionYaml.getAirShowers()).thenReturn(Collections.emptyList());
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private List<TaskPath> invokeSectionElevatorTask(RcsPoint origin, RcsPoint destin, List<RcsPoint> fullPaths) throws Exception {
        java.lang.reflect.Method method = TaskSectionManager.class.getDeclaredMethod(
                "sectionElevatorTask",
                String.class, RcsTask.class, RcsPoint.class, RcsPoint.class, SubTaskTypeEnum.class, List.class
        );
        method.setAccessible(true);
        return (List<TaskPath>) method.invoke(taskSectionManager, "AGV_TEST", mockTask, origin, destin, SubTaskTypeEnum.DESTIN, fullPaths);
    }

    @SuppressWarnings("unchecked")
    private List<TaskPath> invokeSectionAirShowersTask(RcsTask task, List<TaskPath> paths) throws Exception {
        java.lang.reflect.Method method = TaskSectionManager.class.getDeclaredMethod(
                "sectionAirShowersTask",
                RcsTask.class, List.class
        );
        method.setAccessible(true);
        return (List<TaskPath>) method.invoke(taskSectionManager, task, paths);
    }
}