package com.ruinap.adapter.communicate.event;

import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.event.AbstractServerEvent;
import com.ruinap.core.algorithm.SlideTimeWindow;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.strategy.TrafficService;
import com.ruinap.core.task.TaskManager;
import com.ruinap.infra.command.system.VisualCommand;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.DbManager;

/**
 * AGV可视化服务器事件
 *
 * @author qianye
 * @create 2024-09-18 2:24
 */
@Component
public class VisualWebSocketServerEvent extends AbstractServerEvent {
    @Autowired
    private VisualCommand visualCommand;
    @Autowired
    private SlideTimeWindow slideTimeWindow;
    @Autowired
    private TrafficService trafficService;
    @Autowired
    private DbManager dbManager;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private AgvManager agvManager;

    /**
     * 处理事件
     *
     * @param clientId   客户端id
     * @param jsonObject 数据
     */
    public void receiveMessage(String clientId, JSONObject jsonObject) {
        String event = jsonObject.getStr("event");
        switch (event.toLowerCase()) {
//            case "map_data":
//                getMapData(clientId, jsonObject.getStr("data"));
//                break;
//            case "occupied_points":
//                getOccupiedPoints(clientId, jsonObject);
//                break;
//            case "weight_points":
//                getWeightPoints(clientId, jsonObject);
//                break;
//            case "agv_info":
//                getAgvState(clientId, jsonObject);
//                break;
//            case "third_party":
//                getThirdParty(clientId, jsonObject);
//                break;
//            case "task_list":
//                getTaskList(clientId, jsonObject);
//                break;
//            case "alarm_msg":
//                getAlarmList(clientId, jsonObject);
//                break;
//            case "agv_buffer":
//                getBufferData(clientId, jsonObject);
//                break;
//            case "multi_layer_goods":
//                getMultiLayerGoods(clientId, jsonObject);
//                break;
//            case "playback_list":
//                playbackList(clientId, jsonObject);
//                break;
//            case "playback_data":
//                playbackData(clientId, jsonObject.getJSONObject("data"));
//                break;
            default:
                RcsLog.consoleLog.error("Visual 未知事件：" + event);
                break;
        }
    }

//    /**
//     * 获取多层货物信息
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void getMultiLayerGoods(String clientId, JSONObject data) {
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writeMultiLayerGoods(byteBuf, new JSONArray());
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }
//
//    /**
//     * 获取权重点位
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void getWeightPoints(String clientId, JSONObject data) {
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writeWeightPoints(byteBuf, slideTimeWindow.getWeightMap());
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }
//
//    /**
//     * 获取回放数据
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void playbackData(String clientId, JSONObject data) {
//        String date = data.getStr("date");
//        if (date == null || date.isEmpty()) {
//            RcsLog.consoleLog.error("Visual事件 playbackData 传入的 date 数据不能为空");
//            return;
//        }
//        Integer hour = data.getInt("hour");
//        if (hour == null) {
//            RcsLog.consoleLog.error("Visual事件 playbackData 传入的 hour 数据不能为空");
//            return;
//        }
//
//        //  获取关键帧日志目录
//        String keyFrameLogDirectory = StrUtil.format("{}/{}/{}", RcsLog.getKeyFrameLogDirectory(), date, NumberUtil.decimalFormat("00", hour));
//        File keyFrameFile = FileUtil.newFile(keyFrameLogDirectory);
//        //  获取目录下的所有文件
//        File[] hourFiles = FileUtil.ls(keyFrameFile.getAbsolutePath());
//        // 判断是否存在文件
//        if (hourFiles != null) {
//            //内容集合
//            JSONArray contentArray = new JSONArray();
//            // 遍历文件
//            for (File hourFile : hourFiles) {
//                // 判断任务类型
//                String type = FileTypeUtil.getType(hourFile);
//                if ("gz".equalsIgnoreCase(type)) {
//                    // 读取并解压文件
//                    byte[] gzBytes = FileUtil.readBytes(hourFile);
//                    byte[] unGzipBytes = ZipUtil.unGzip(gzBytes);
//                    // 将 byte[] 转换为 InputStream
//                    InputStream inputStream = IoUtil.toStream(unGzipBytes);
//
//                    // 使用数组包装 firstTime 和 lastTime，使其在 Lambda 中可变
//                    // [0] 为 firstTime, [1] 为 lastTime
//                    Long[] timeValues = new Long[2];
//                    // 标记是否为第一行
//                    boolean[] isFirstLine = new boolean[]{true};
//                    //开始读取每行数据
//                    IoUtil.readLines(
//                            inputStream,
//                            StandardCharsets.UTF_8,
//                            (LineHandler) line -> {
//                                if (line != null && !line.trim().isEmpty() && !",".equalsIgnoreCase(line.trim())) {
//                                    String cleanedLine = line;
//                                    // 非第一行去掉开头的逗号
//                                    if (!isFirstLine[0] && cleanedLine.startsWith(",")) {
//                                        cleanedLine = cleanedLine.substring(1).trim();
//                                    }
//                                    JSONObject jsonObject = JSONUtil.parseObj(cleanedLine);
//                                    Long time = jsonObject.getLong("time");
//                                    if (timeValues[0] == null) {
//                                        // 记录第一行
//                                        timeValues[0] = time;
//                                        // 标记第一行已处理
//                                        isFirstLine[0] = false;
//                                    }
//                                    // 持续更新最后一行
//                                    timeValues[1] = time;
//                                }
//                            }
//                    );
//                    // 关闭输入流
//                    IoUtil.close(inputStream);
//
//                    // 输出结果
//                    if (timeValues[0] != null && timeValues[1] != null) {
//                        JSONObject jsonObject = new JSONObject();
//                        //设置细分的时间段
//                        jsonObject.set("timestamp", StrUtil.format("{}~{}", DateUtil.format(DateUtil.date(timeValues[0]), "HH:mm:ss"), DateUtil.format(DateUtil.date(timeValues[1]), "HH:mm:ss")));
//                        // 将日志文件编码为Base64
//                        String base64Gz = Base64.encode(gzBytes);
//                        jsonObject.set("compressed_data", base64Gz);
//                        contentArray.add(jsonObject);
//                    }
//                }
//            }
//
//            JSONObject logObject = new JSONObject();
//            logObject.set("date", date);
//            logObject.set("hour", hour);
//            // 设置日志文件内容
//            logObject.set("content", contentArray);
//
//            ByteBuf byteBuf = Unpooled.buffer();
//            visualCommand.writePlaybackData(byteBuf, logObject);
//            NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//        }
//    }
//
//    /**
//     * 获取回放列表
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void playbackList(String clientId, JSONObject data) {
//        JSONArray jsonArray = new JSONArray();
//        //  获取关键帧日志目录
//        String keyFrameLogDirectory = RcsLog.getKeyFrameLogDirectory();
//        if (keyFrameLogDirectory != null) {
//            File keyFrameFile = FileUtil.newFile(keyFrameLogDirectory);
//            //  获取目录下的所有文件
//            File[] logDirectorys = FileUtil.ls(keyFrameFile.getAbsolutePath());
//            // 判断是否存在文件
//            if (logDirectorys != null) {
//                // 遍历文件
//                for (File logDirectory : logDirectorys) {
//                    // 判断是否是目录
//                    boolean logFlag = FileUtil.isDirectory(logDirectory);
//                    if (logFlag) {
//                        // 获取日期目录名称
//                        String dateDirectoryName = logDirectory.getName();
//                        // 获取小时集合
//                        List<Integer> hourList = new ArrayList<>();
//                        //  获取目录下的所有文件
//                        File[] hourDirectorys = FileUtil.ls(logDirectory.getAbsolutePath());
//                        // 判断是否存在文件
//                        if (hourDirectorys != null) {
//                            // 遍历文件
//                            for (File hourDirectory : hourDirectorys) {
//                                // 判断是否是目录
//                                boolean hourFlag = FileUtil.isDirectory(hourDirectory);
//                                if (hourFlag) {
//                                    // 获取小时目录名称
//                                    String hourDirectoryName = hourDirectory.getName();
//                                    //  判断目录是否为空
//                                    boolean dirEmpty = FileUtil.isDirEmpty(hourDirectory);
//                                    if (!dirEmpty) {
//                                        hourList.add(Integer.parseInt(hourDirectoryName));
//                                    }
//                                }
//                            }
//                        }
//
//                        // 判断小时集合是否为空
//                        if (!hourList.isEmpty()) {
//                            JSONObject jsonObject = new JSONObject();
//                            jsonObject.set("date", dateDirectoryName);
//                            jsonObject.set("hour", hourList);
//                            jsonArray.add(jsonObject);
//                        }
//                    }
//                }
//            }
//        }
//
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writePlaybackList(byteBuf, jsonArray);
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }
//
//    /**
//     * 获取AGV缓冲区数据
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void getBufferData(String clientId, JSONObject data) {
//        Map<String, Geometry> bufferSnapshot = trafficService.getBufferSnapshot();
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writeBufferList(byteBuf, bufferSnapshot.values().toArray());
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }
//
//    /**
//     * 获取告警信息
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void getAlarmList(String clientId, JSONObject data) {
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writeAlarmList(byteBuf, dbManager.getAlarmListCache());
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }
//
//    /**
//     * 获取任务列表
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void getTaskList(String clientId, JSONObject data) {
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writeTaskList(byteBuf, taskManager.taskCache.values().toArray());
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }
//
//    /**
//     * 获取占用点位
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void getOccupiedPoints(String clientId, JSONObject data) {
//        ByteBuf byteBuf = Unpooled.buffer();
//        Map<String, Set<RcsPointOccupy>> occupys = mapManager.getDeviceOccupyIndex();
//        visualCommand.writeOccupiedPoint(byteBuf, occupys.values().toArray());
//        // 发送消息
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }
//
//    /**
//     * 获取设备状态
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void getThirdParty(String clientId, JSONObject data) {
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writeThirdParty(byteBuf, dbManager.getThirdPartyCache());
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }
//
//    /**
//     * 获取AGV状态
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void getAgvState(String clientId, JSONObject data) {
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writeAgvState(byteBuf, agvManager.getRcsAgvMap().values().toArray());
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }
//
//    /**
//     * 获取地图数据
//     *
//     * @param clientId 客户端id
//     * @param data     数据
//     */
//    private void getMapData(String clientId, String data) {
//        //获取地图
//        Integer mapId = 1;
//        if (data != null && !data.isEmpty()) {
//            try {
//                JSONObject dataJson = new JSONObject(data);
//                mapId = dataJson.getInt("map_id");
//            } catch (Exception e) {
//                RcsLog.consoleLog.error("出现异常，客户端 [" + clientId + "] 传入的参数data不是json格式");
//            }
//        }
//
//        mapManager.getSnapshot().graph();
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writeMapData(byteBuf, mapManager.getSnapshot().pointMap().get(mapId).values().toArray());
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//
//        //获取地图图片
//        getPngData(clientId, mapId);
//    }
//
//    /**
//     * 获取地图图片
//     *
//     * @param clientId 客户端id
//     * @param mapId    地图编号
//     */
//    private void getPngData(String clientId, Integer mapId) {
//        // 指定图片文件所在目录
//        Path imageDirectoryPath = PathUtils.PNG_DIR;
//
//        // 获取所有的地图文件
//        File imageDirectory = new File(imageDirectoryPath.toUri());
//        File[] mapFiles = imageDirectory.listFiles((dir, name) -> name.endsWith(".png"));
//
//        // 构造返回的 JSON 数据
//        JSONObject reJson = new JSONObject();
//        // 默认设置 map_num
//        int mapCount = mapManager.getSnapshot().pointMap().size();
//        reJson.set("map_num", mapCount);
//        // 默认设置 png 为空字符串
//        String base64Image = "";
//        reJson.set("png", base64Image);
//
//        // 如果目录中没有文件，记录错误并直接发送默认 JSON
//        if (mapFiles == null || mapFiles.length == 0) {
//            RcsLog.consoleLog.error("{} 目录下未找到任何地图文件", imageDirectoryPath);
//        } else {
//            // 查找与指定 mapId 匹配的文件
//            String mapFileName = mapId + ".png";
//            File mapFile = new File(imageDirectory, mapFileName);
//
//            // 如果文件不存在，记录错误并使用默认的空 base64Image
//            if (!mapFile.exists()) {
//                RcsLog.consoleLog.error("未找到 MapId 为 " + mapId + " 的地图文件。");
//            } else {
//                // 读取图片文件为字节数组
//                try {
//                    byte[] imageBytes = FileUtil.readBytes(mapFile);
//                    base64Image = Base64.encode(imageBytes);
//                    reJson.set("png", base64Image);
//                } catch (IORuntimeException e) {
//                    RcsLog.consoleLog.error("读取图片文件失败：" + e.getMessage());
//                }
//            }
//        }
//
//        ByteBuf byteBuf = Unpooled.buffer();
//        visualCommand.writePngData(byteBuf, reJson);
//        NettyServer.getServer(VisualWebSocketHandler.getProtocol()).sendMessage(clientId, byteBuf);
//    }

}
