/**
 * 调度系统延迟检测脚本 (CORS 修复版)
 * 功能：
 * 1. 自动扫描页面上的链接列表。
 * 2. 使用 fetch API 发起异步请求检测延迟。
 * 3. 智能判断：
 * - 同源链接 (9090): 精准判断 200 OK，404/500 报红。
 * - 跨域链接 (8563): 使用 no-cors 模式，只要网络通(端口开)就显示延迟，不报红。
 */

var autourl = [];

/**
 * 1. 解析页面 DOM，提取待测速的 URL 和对应的显示元素 ID
 */
function updateAutourl() {
    var speedlistItems = document.querySelectorAll('.speedlist li');
    autourl = [];
    for (var i = 0; i < speedlistItems.length; i++) {
        var aTag = speedlistItems[i].querySelector('a');
        var idTag = speedlistItems[i].querySelector('i[id^="lineMs"]');

        if (aTag && idTag) {
            autourl.push({
                url: aTag.getAttribute("href"),
                elementId: idTag.id
            });
        }
    }
}

/**
 * 2. 渲染测速结果到页面
 */
function renderResult(elementId, timeStr, isError) {
    var el = document.getElementById(elementId);
    if (el) {
        el.innerHTML = timeStr;
        if (isError) {
            el.style.color = '#ff4d4f'; // 红色
            el.style.fontWeight = 'bold';
        } else {
            var ms = parseInt(timeStr);
            el.style.color = ms > 500 ? '#ff4d4f' : '#52c41a'; // 绿色
            el.style.fontWeight = 'normal';
        }
    }
}

/**
 * 3. 执行核心测速逻辑
 */
function run() {
    updateAutourl();

    autourl.forEach(item => {
        var startTime = Date.now();
        var targetUrl = item.url;
        // 添加时间戳防止缓存
        var fetchUrl = targetUrl.indexOf('?') > -1 ? targetUrl + '&t=' + startTime : targetUrl + '?t=' + startTime;

        // 判断是否跨域 (端口不同也算跨域)
        var isCrossOrigin = false;
        try {
            var currentOrigin = window.location.origin; // e.g., http://127.0.0.1:9090
            // 处理相对路径的情况，统一转为绝对路径比较
            var targetOrigin = new URL(targetUrl, window.location.href).origin;
            isCrossOrigin = currentOrigin !== targetOrigin;
        } catch (e) {
            // 如果 URL 解析失败，默认不跨域
        }

        // 配置 Fetch 选项
        var fetchOptions = {
            method: 'GET',
            signal: (new AbortController()).signal // 稍后绑定超时
        };

        // 【关键修复】如果是跨域请求 (如 8563)，启用 no-cors 模式
        // 这样浏览器就不会拦截响应，也不会报 Console 错误
        if (isCrossOrigin) {
            fetchOptions.mode = 'no-cors';
        }

        // 创建超时控制器 (3秒超时)
        const controller = new AbortController();
        fetchOptions.signal = controller.signal;
        const timeoutId = setTimeout(() => controller.abort(), 3000);

        fetch(fetchUrl, fetchOptions)
            .then(response => {
                clearTimeout(timeoutId);
                var endTime = Date.now();
                var elapsedTime = endTime - startTime;

                // 【分类处理】

                // 情况 A: 跨域请求 (opaque)
                // no-cors 模式下，response.type 为 'opaque'，status 为 0
                // 我们无法读取具体是 200 还是 404，但能走到这里说明网络是通的 (Port Open)
                if (response.type === 'opaque') {
                    renderResult(item.elementId, elapsedTime + 'ms', false);
                    return;
                }

                // 情况 B: 同源请求 (9090)
                // 可以精准判断状态码
                if (response.status !== 200) {
                    renderResult(item.elementId, '999ms', true);
                } else {
                    renderResult(item.elementId, elapsedTime + 'ms', false);
                }
            })
            .catch(error => {
                // 捕获异常：网络不通、DNS失败、超时、或目标端口关闭
                renderResult(item.elementId, '999ms', true);
            });
    });
}

window.onload = function () {
    run();
};