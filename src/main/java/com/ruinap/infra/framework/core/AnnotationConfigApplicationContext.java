package com.ruinap.infra.framework.core;

import cn.hutool.core.util.StrUtil;
import com.ruinap.RcsApplication;
import com.ruinap.infra.config.common.PathUtils;
import com.ruinap.infra.framework.annotation.*;
import com.ruinap.infra.framework.annotation.EventListener;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.framework.boot.SpringBootApplication;
import com.ruinap.infra.framework.config.AopFeatureControl;
import com.ruinap.infra.framework.config.ConfigurableBeanFactory;
import com.ruinap.infra.framework.config.SchedulingFeatureControl;
import com.ruinap.infra.framework.core.event.ApplicationEvent;
import com.ruinap.infra.framework.core.event.ApplicationEventPublisher;
import com.ruinap.infra.framework.core.event.ApplicationListener;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 【核心实现】基于注解的应用上下文 (Lightweight IoC Container)
 * <p>
 * 这是一个极简版的 Spring 容器实现，负责管理 Bean 的全生命周期。
 * </p>
 *
 * <h3>⚙️ 核心生命周期 (Lifecycle)：</h3>
 * <ol>
 * <li><strong>Environment Load:</strong>  扫描 config 目录，加载 YAML/Setting</li>
 * <li><strong>Scan & Instantiate:</strong> 扫描 @Component -> <strong> Check Condition</strong> -> 反射 new</li>
 * <li><strong>Dependency Injection:</strong> 扫描 @Autowired -> 填充属性</li>
 * <li><strong>Aware Callbacks:</strong> 处理 ApplicationContextAware</li>
 * <li><strong>Initialization:</strong> 执行 @PostConstruct</li>
 * <li><strong>Runner:</strong> 执行 CommandLineRunner 启动任务</li>
 * <li><strong>Destroy:</strong> 钩子触发 @PreDestroy</li>
 * </ol>
 *
 * @author qianye
 * @create 2025-12-10 16:00
 */
public class AnnotationConfigApplicationContext implements ApplicationContext {
    /**
     * 单例对象池 (一级缓存)
     * Key: 类对象 (Class), Value: 实例对象 (Object)
     * 所有的 @Component 组件都会以单例形式存在这里。
     */
    private final Map<Class<?>, Object> singletonObjects = new ConcurrentHashMap<>();

    /**
     * 原型 Bean 定义表 (只存 Class，不存实例)
     * Key: 原型类 (Class), Value: 原型类 (Class)
     */
    private final Map<Class<?>, Class<?>> prototypeDefinitions = new ConcurrentHashMap<>();

    /**
     * 原型接口映射定义表
     * Key: 接口类型, Value: 实现类类型
     */
    private final Map<Class<?>, Class<?>> prototypeInterfaceDefinitions = new ConcurrentHashMap<>();

    /**
     * 接口映射表
     * Key: 接口类型 (Interface Class), Value: 实现类实例 (Object)
     * 用于支持“按接口注入”，比如 @Autowired private DatabaseService dbService;
     */
    private final Map<Class<?>, Object> interfaceMap = new ConcurrentHashMap<>();

    /**
     * 存储计算出来的扫描路径
     */
    private final Set<String> basePackages = new HashSet<>();

    /**
     * 容器状态标记，防止重复启动
     */
    private volatile boolean isActive = false;

    /**
     * 事件监听器集合
     * 存储所有扫描到的 ApplicationListener (包括通过 @EventListener 解析出来的)
     */
    private final Set<ApplicationListenerWrapper> applicationListeners = new CopyOnWriteArraySet<>();

    /**
     * JDK 21 虚拟线程专属执行器 (无上限，极轻量)
     * 独立于业务线程池，专门负责事件的极速派发，并且支持优雅停机。
     */
    private final ExecutorService asyncEventExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 【核心组件】环境配置对象
     * 负责加载配置文件，供 @ConditionalOnProperty 和 Bean 绑定使用。
     */
    private final Environment environment;

    /**
     * 容器启动锁 (互斥锁)
     */
    private final RcsLock startupLock = RcsLock.ofReentrant();

    /**
     * 【标准 Spring 构造函数】
     * 传入一个或多个配置类（通常是主启动类），解析扫描路径，并立即启动容器。
     *
     * @param componentClasses 配置类列表
     */
    public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
        // 打印启动横幅
        RcsApplication.printlnStartBanner();
        // 0. 初始化环境配置管理器 (必须在一切开始之前)
        this.environment = new Environment();
        // 自动扫描项目根目录下的 config 文件夹 (使用 PathUtils 获取路径)
        // 这一步会将 YAML/Setting 全部加载到内存
        this.environment.scanAndLoad(PathUtils.CONFIG_DIR.toString());
        // 将 Environment 注册为 Bean，方便其他组件注入
        this.singletonObjects.put(Environment.class, this.environment);

        for (Class<?> clazz : componentClasses) {
            // 1. 尝试直接获取 @ComponentScan 注解
            ComponentScan scan = clazz.getAnnotation(ComponentScan.class);

            // 2. 【智能兼容】如果是 @SpringBootApplication，它本质上也是一个配置类
            // 虽然我们的极简框架不支持递归解析元注解，但我们可以手动识别它。
            // 如果类上有 @SpringBootApplication 且没有 @ComponentScan，我们应当视作需要扫描当前包。
            if (scan == null && clazz.isAnnotationPresent(SpringBootApplication.class)) {
                // 标记一下，虽然 scan 是 null，但我们知道它是主程序，
                // 逻辑会自然流转到下面的“兜底逻辑”，将当前包加入扫描。
            }

            if (scan != null) {
                // 情况 A: 显式指定了 @ComponentScan
                String[] values = scan.value();
                String[] pkgs = scan.basePackages();

                if (values.length > 0 || pkgs.length > 0) {
                    // 如果指定了具体路径，则添加这些路径
                    if (values.length > 0) {
                        basePackages.addAll(Arrays.asList(values));
                    }
                    if (pkgs.length > 0) {
                        basePackages.addAll(Arrays.asList(pkgs));
                    }
                } else {
                    // 情况 B: 有 @ComponentScan 注解但没填值 -> 默认扫描当前类所在的包
                    basePackages.add(clazz.getPackageName());
                }
            } else {
                // 情况 C: 没有 @ComponentScan (或者是 @SpringBootApplication 但没配扫描路径)
                // 兜底策略：默认将配置类所在的包作为扫描路径 (符合 Spring Boot 习惯)
                basePackages.add(clazz.getPackageName());
            }

            // 3. 检查 @EnableAsync
            if (clazz.isAnnotationPresent(EnableAsync.class)) {
                AopFeatureControl.enableAsync();
                RcsLog.sysLog.info(">>> 功能模块: [异步执行] 已启用");
            }

            // 4. 检查 @EnableTransaction
            if (clazz.isAnnotationPresent(EnableTransaction.class)) {
                AopFeatureControl.enableTransaction();
                RcsLog.sysLog.info(">>> 功能模块: [事务管理] 已启用");
            }

            // 5. 检查 @EnableScheduling -> 调度开关
            if (clazz.isAnnotationPresent(EnableScheduling.class)) {
                SchedulingFeatureControl.enable();
                RcsLog.sysLog.info(">>> 功能模块: [定时任务] 已启用");
            }
        }

        // 将容器自身注册到接口映射表中
        this.interfaceMap.put(ApplicationContext.class, this);
        // 顺便把父接口 ApplicationEventPublisher 也注册进去，方便发布事件时注入
        this.interfaceMap.put(ApplicationEventPublisher.class, this);

        // 6. 解析完路径后，自动刷新容器 (启动生命周期)
        refresh();

        // 7. 【注册钩子】优雅停机
        // 当程序被 kill 或 Ctrl+C 关闭时，容器会自动调用所有 Bean 的 @PreDestroy 方法释放资源
        registerShutdownHook();
    }

    /**
     * 核心启动方法：容器的生命周期引擎
     * <p>
     * ⚠️ <strong>注意：顺序不可随意更改！</strong><br>
     * 例如：必须先注入(Inject)才能初始化(PostConstruct)；
     * 必须先 Aware 才能让 Utils 工具类在后续步骤中可用。
     * </p>
     */
    @Override
    public void refresh() {
        // 加锁，防止多线程并发启动导致混乱
        startupLock.runInWrite(() -> {
            if (isActive) {
                RcsLog.sysLog.warn("容器已经是活动状态，忽略重复启动请求");
                return;
            }
            long start = System.currentTimeMillis();
            RcsLog.sysLog.info("Framework 容器开始初始化...");

            try {
                // 1. 扫描与实例化 (也就是 Bean 的 Creation 阶段)
                doScanAndInstantiate();

                // 2. 依赖注入 (Population 阶段)
                // 此时 Bean 已经被创建，属性将在这里被填充
                doDependencyInjection();

                // 3. 【关键步骤】执行 Aware 回调
                // 这一步必须在注入之后、初始化之前。
                // 确保 SpringContextHolder 能在 @PostConstruct 之前拿到 context。
                doAwareCallbacks();

                // 4. 初始化事件广播器 (扫描 @EventListener)
                // 必须在 Bean 初始化完成后进行，确保 Bean 已经是完整的
                initApplicationEventMulticaster();

                // 5. 初始化 (Initialization 阶段)
                // 执行 @PostConstruct。此时 Bean 属性已填充，Context 已就绪。
                doPostConstruct();

                // 6. 运行器 (Startup 阶段)
                // 所有 Bean 准备就绪后，执行业务启动逻辑
                doRunners();

                isActive = true;
                RcsLog.sysLog.info("Framework 容器启动完成，Singleton组件: {}, Prototype组件: {}, 耗时: {} ms",
                        singletonObjects.size(), prototypeDefinitions.size(), System.currentTimeMillis() - start);

            } catch (Exception e) {
                // 🛑 严重错误处理
                // 1. 必须打印堆栈到控制台 (System.err)，因为此时 Log 系统可能已经崩了
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                System.err.println("!!! Framework 容器启动严重失败 !!!");
                e.printStackTrace();
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

                // 2. 必须回滚资源
                close();

                // 3. 抛出 RuntimeException，让上层 (如 JUnit) 感知并报错
                // ❌ 严禁调用 System.exit(1)，否则单元测试会静默退出！
                throw new RuntimeException("容器启动失败", e);
            }
        });
    }

    /**
     * 阶段一：扫描与实例化
     * <p>
     * 核心逻辑：
     * 1. 扫描指定包下的所有 @Component 组件。
     * 2. <strong>AspectJ 切面接管：</strong> 对于 @Aspect 类，不进行 new，而是通过 aspectOf() 获取单例。
     * 3. <strong>普通组件实例化：</strong> 对于普通类，反射调用构造函数 newInstance。
     * </p>
     */
    private void doScanAndInstantiate() throws Exception {
        // 1. 防御性检查：如果没有扫描路径，直接返回
        if (basePackages.isEmpty()) {
            RcsLog.sysLog.warn("IoC 容器未配置任何扫描路径 (basePackages 为空)，跳过扫描。");
            return;
        }

        // 2. 将 Set<String> 转换为 Object[] 数组
        // Reflections 的构造函数接受 Object... params，我们需要传入包名数组
        Object[] packageNames = basePackages.toArray();

        RcsLog.sysLog.info("正在扫描包路径: {}", Arrays.toString(packageNames));

        // 3. 初始化反射扫描器
        // 传入配置的包名数组，Reflections 会扫描这些包及其子包
        Reflections reflections = new Reflections(packageNames);

        // 4. 扫描所有带有 @Component 的类
        // 注意：@Service, @Repository 上面也有 @Component，所以也能被扫到
        Set<Class<?>> components = reflections.getTypesAnnotatedWith(Component.class);
        if (components.isEmpty()) {
            RcsLog.sysLog.warn("在路径 {} 下未扫描到任何组件，请检查 @ComponentScan 配置！", Arrays.toString(packageNames));
        }

        for (Class<?> clazz : components) {
            // 跳过接口和抽象类，因为它们不能被 new
            if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            if (!checkCondition(clazz)) {
                RcsLog.sysLog.debug("组件 [{}] 被 @ConditionalOnProperty 跳过。", clazz.getSimpleName());
                continue;
            }

            // =======================================================
            // 检查 @Scope 作用域
            // =======================================================
            boolean isPrototype = false;
            if (clazz.isAnnotationPresent(Scope.class)) {
                Scope scope = clazz.getAnnotation(Scope.class);
                if (ConfigurableBeanFactory.SCOPE_PROTOTYPE.equalsIgnoreCase(scope.value())) {
                    isPrototype = true;
                }
            }

            if (isPrototype) {
                // 原型模式：不实例化，只注册定义，留待 getBean 时按需创建
                prototypeDefinitions.put(clazz, clazz);
                for (Class<?> iface : clazz.getInterfaces()) {
                    prototypeInterfaceDefinitions.putIfAbsent(iface, clazz);
                }
                RcsLog.sysLog.debug("注册 Prototype 组件定义: {}", clazz.getSimpleName());
                // 跳过后续的单例实例化流程
                continue;
            }

            try {
                // 【修改】调用提取的实例化方法 (逻辑与原代码一致，只是为了复用)
                Object instance = createBeanInstance(clazz);

                // 后续逻辑保持不变：放入单例池
                singletonObjects.put(clazz, instance);
                // 建立接口映射
                for (Class<?> iface : clazz.getInterfaces()) {
                    interfaceMap.putIfAbsent(iface, instance);
                }
            } catch (Exception e) {
                RcsLog.sysLog.error("实例化组件 [{}] 失败", clazz.getName(), e);
                // 实例化失败属于严重错误，必须抛出终止启动
                throw e;
            }
        }
    }

    /**
     * 创建 Bean 实例 (抽取自原 doScanAndInstantiate，支持复用)
     */
    private Object createBeanInstance(Class<?> clazz) throws Exception {
        // 检查是否存在无参构造函数
        try {
            clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            String errorMsg = StrUtil.format("初始化失败: 组件 [{}] 缺少 public 无参构造函数。LCLM框架暂不支持构造器注入，请使用 @Autowired 字段注入。", clazz.getName());
            // (注：原代码这里是打印并抛异常，保留原逻辑)
            // RcsLog.sysLog.error(errorMsg, e); // 为了避免重复日志，这里可以省略，由外层捕获或直接抛出
            throw new RuntimeException(errorMsg, e);
        }

        Object instance;

        // =======================================================
        // 【核心机制】 AspectJ 切面实例接管 (AspectJ Integration)
        // =======================================================
        // 原理：使用 AspectJ 编译器 (AJC) 织入的切面是单例的，且由 JVM 类加载触发初始化。
        // AJC 会自动生成 public static Aspect aspectOf() 方法。
        // 我们必须通过这个方法获取"真身"，而不能自己 new，否则会有两个实例。
        if (clazz.isAnnotationPresent(org.aspectj.lang.annotation.Aspect.class)) {
            try {
                Method aspectOf = clazz.getMethod("aspectOf");
                instance = aspectOf.invoke(null);
                RcsLog.sysLog.debug("已接管 AspectJ 切面实例: {}", clazz.getSimpleName());
            } catch (NoSuchMethodException e) {
                // 降级：如果未织入 (IDE 运行且未配置 ajc)，则回退到普通实例化
                instance = clazz.getDeclaredConstructor().newInstance();
            }
        } else {
            // 普通组件：直接实例化
            instance = clazz.getDeclaredConstructor().newInstance();
        }
        return instance;
    }

    /**
     * 创建完整的 Prototype Bean (包含注入和初始化)
     */
    private Object createPrototypeBean(Class<?> clazz) {
        try {
            // 1. 实例化
            Object instance = createBeanInstance(clazz);

            // 2. 依赖注入
            populateBean(instance);

            // 3. Aware 回调
            if (instance instanceof ApplicationContextAware) {
                ((ApplicationContextAware) instance).setApplicationContext(this);
            }

            // 4. PostConstruct 初始化
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    method.setAccessible(true);
                    method.invoke(instance);
                }
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("创建 Prototype Bean [" + clazz.getName() + "] 失败", e);
        }
    }

    /**
     * 统一 Bean 查找逻辑 (支持 Singleton + Prototype)
     */
    private Object getBeanOrNull(Class<?> targetType) {
        // 1. 查单例池
        Object bean = singletonObjects.get(targetType);
        if (bean != null) return bean;

        // 2. 查单例接口池
        bean = interfaceMap.get(targetType);
        if (bean != null) return bean;

        // 3. 查原型定义池 (Prototype)
        Class<?> implClass = prototypeDefinitions.get(targetType);
        if (implClass == null) {
            // 4. 查原型接口定义池
            implClass = prototypeInterfaceDefinitions.get(targetType);
        }

        if (implClass != null) {
            // 【关键】现用现做：创建一个新的 Prototype 实例
            return createPrototypeBean(implClass);
        }

        return null;
    }

    /**
     * 检查 @ConditionalOnProperty
     */
    private boolean checkCondition(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(ConditionalOnProperty.class)) {
            return true;
        }

        ConditionalOnProperty condition = clazz.getAnnotation(ConditionalOnProperty.class);
        String key = condition.name();
        String havingValue = condition.havingValue();
        boolean matchIfMissing = condition.matchIfMissing();

        boolean exists = environment.containsProperty(key);

        if (!exists) {
            return matchIfMissing;
        }

        String realValue = environment.getProperty(key);
        if (StrUtil.isEmpty(havingValue)) {
            return !"false".equalsIgnoreCase(realValue);
        }
        return havingValue.equalsIgnoreCase(realValue);
    }

    /**
     * 阶段二：依赖注入
     * 遍历所有 Bean 的字段，如果有 @Autowired，就从容器里找对象赋给它。
     */
    private void doDependencyInjection() throws IllegalAccessException {
        for (Object bean : singletonObjects.values()) {
            // 调用抽取的通用注入方法
            populateBean(bean);
        }
    }

    /**
     * 【核心能力】向外部对象注入依赖
     * <p>
     * <strong>作用：</strong><br>
     * 这不是给容器内部 Bean 用的，而是专门给 <strong>JUnit 测试类</strong> 用的。<br>
     * 因为测试类是由 JUnit 创建的，不是容器 new 的，所以容器需要“后补”注入。
     * </p>
     *
     * @param existingBean 外部创建的对象 (如测试类实例)
     */
    public void autowireBean(Object existingBean) {
        try {
            populateBean(existingBean);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("外部对象依赖注入失败", e);
        }
    }

    /**
     * 【核心逻辑】为对象填充 @Autowired 属性
     */
    private void populateBean(Object bean) throws IllegalAccessException {
        Class<?> clazz = bean.getClass();
        // 循环向上遍历父类，直到 Object
        while (clazz != null && clazz != Object.class) {
            // 获取当前类声明的所有字段
            for (Field field : clazz.getDeclaredFields()) {
                // 只处理贴了 @Autowired 的字段
                if (field.isAnnotationPresent(Autowired.class)) {
                    // 暴力反射：允许访问 private/protected 字段
                    field.setAccessible(true);
                    Class<?> targetType = field.getType();
                    Object dependency = null;

                    // =======================================================
                    //支持 List<T> 集合注入，(仅支持单例聚合)
                    // =======================================================
                    if (List.class.isAssignableFrom(targetType)) {
                        Type genericType = field.getGenericType();
                        if (genericType instanceof ParameterizedType) {
                            Type[] params = ((ParameterizedType) genericType).getActualTypeArguments();
                            // 获取泛型类型，例如 List<IServerHandler> 中的 IServerHandler
                            if (params.length > 0 && params[0] instanceof Class) {
                                Class<?> elementType = (Class<?>) params[0];
                                List<Object> beanList = new ArrayList<>();
                                // 遍历容器中所有 Bean，找到符合泛型类型的实例
                                for (Object singleton : singletonObjects.values()) {
                                    if (elementType.isAssignableFrom(singleton.getClass())) {
                                        beanList.add(singleton);
                                    }
                                }
                                // 如果找到了，就赋值（允许为空列表）
                                dependency = beanList;
                            }
                        }
                    }

                    // =======================================================
                    // 标准单例注入逻辑
                    // =======================================================
                    // 跳过Environment (已在 Environment 加载时放入 singletonObjects，其实可以直接查)
                    if (dependency == null && targetType == Environment.class) {
                        dependency = this.environment;
                    }

                    // 统一调用 getBeanOrNull 获取依赖
                    // 原有代码是分步查 singletonObjects 和 interfaceMap，现在逻辑已封装到 getBeanOrNull 中
                    // 这样无论是单例还是原型，都能在这里被正确获取
                    if (dependency == null) {
                        dependency = getBeanOrNull(targetType);
                    }

                    // 检查 required
                    if (dependency == null && field.getAnnotation(Autowired.class).required()) {
                        // 如果是 List 且没找到任何 Bean，如果是 required=true 且 dependency 为空，说明真的没找到
                        // 但对于 List 来说，空 List 通常是可以接受的，视业务而定。
                        // 这里保持严格检查：如果是普通 Bean 没找到则报错；如果是 List 但没找到元素，暂不报错(dependency为ArrayList)，除非泛型解析失败。

                        // 修正逻辑：如果是 List 注入，dependency 此时是一个空 List 或 填充了的 List，不为 null。
                        // 只有非 List注入且没找到时，dependency 才会为 null。
                        throw new RuntimeException(StrUtil.format("依赖注入失败: 类 [{}] (继承自 {}) 需要依赖 [{}]，但在容器中未找到。",
                                bean.getClass().getSimpleName(), clazz.getSimpleName(), targetType.getSimpleName()));
                    }

                    if (dependency != null) {
                        field.set(bean, dependency);
                    }
                }
            }
            // 【关键】继续处理父类
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 阶段三：执行 Aware 回调
     * 处理 ApplicationContextAware 接口
     * 遍历所有 Bean，如果实现了该接口，就调用 setApplicationContext
     */
    private void doAwareCallbacks() {
        for (Object bean : singletonObjects.values()) {
            if (bean instanceof ApplicationContextAware) {
                ((ApplicationContextAware) bean).setApplicationContext(this);
            }
        }
    }

    /**
     * 阶段四：【核心实现】初始化事件广播机制
     * <p>
     * 职责：
     * 1. 扫描所有 Bean，识别并注册实现了 ApplicationListener 接口的类。
     * 2. 扫描所有带有 @EventListener 注解的方法，并创建适配器将其注册为监听器。
     * </p>
     */
    private void initApplicationEventMulticaster() {
        for (Object bean : singletonObjects.values()) {
            Class<?> beanClass = bean.getClass();

            // =======================================================
            // 逻辑 1：处理接口式监听器 (实现 ApplicationListener 接口)
            // =======================================================
            if (bean instanceof ApplicationListener) {
                // 提取泛型参数
                Class<?> eventType = resolveEventType(beanClass);
                this.applicationListeners.add(new ApplicationListenerWrapper(eventType, (ApplicationListener<?>) bean));
                RcsLog.sysLog.debug("注册接口式监听器: {}, 监听事件: {}", beanClass.getSimpleName(), eventType.getSimpleName());
            }

            // =======================================================
            // 逻辑 2：处理注解式监听器 (标注 @EventListener 的方法)
            // =======================================================
            Method[] methods = beanClass.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(EventListener.class)) {
                    // 1. 校验参数个数：必须有且只有一个参数
                    if (method.getParameterCount() != 1) {
                        RcsLog.sysLog.warn("方法 {} 标注了 @EventListener 但参数个数不为 1，已忽略。", method.getName());
                        continue;
                    }

                    // 2. 校验参数类型：必须是 ApplicationEvent 的子类
                    Class<?> eventType = method.getParameterTypes()[0];
                    if (!ApplicationEvent.class.isAssignableFrom(eventType)) {
                        RcsLog.sysLog.warn("方法 {} 监听的参数类型不是 ApplicationEvent，已忽略。", method.getName());
                        continue;
                    }

                    // 3. 构造适配器：将 method 调用包装为标准的 ApplicationListener 接口调用
                    // 使用 lambda 表达式创建一个适配器，并在调用前进行类型检查
                    ApplicationListener<?> adapter = event -> {
                        try {
                            method.setAccessible(true);
                            method.invoke(bean, event);
                        } catch (Exception e) {
                            RcsLog.sysLog.error("注解事件监听器执行失败: {}#{}", beanClass.getSimpleName(), method.getName(), e);
                        }
                    };

                    this.applicationListeners.add(new ApplicationListenerWrapper(eventType, adapter));
                    RcsLog.sysLog.debug("注册注解式监听器适配器: {}#{}", beanClass.getSimpleName(), method.getName());
                }
            }
        }
    }

    /**
     * 辅助方法：解析 ApplicationListener<E> 中的泛型 E
     */
    private Class<?> resolveEventType(Class<?> listenerClass) {
        Type[] interfaces = listenerClass.getGenericInterfaces();
        for (Type type : interfaces) {
            if (type instanceof ParameterizedType paramType) {
                if (paramType.getRawType() == ApplicationListener.class) {
                    return (Class<?>) paramType.getActualTypeArguments()[0];
                }
            }
        }
        // 兜底：如果无法解析，默认接收所有事件
        return ApplicationEvent.class;
    }

    /**
     * 【核心实现】发布事件 (支持同步/异步控制)
     */
    @Override
    public void publishEvent(ApplicationEvent event, boolean isSync) {
        if (event == null) {
            return;
        }

        for (ApplicationListenerWrapper wrapper : applicationListeners) {
            // 【过滤前置】：在开线程和执行之前，进行精准且极其廉价的类型判断！
            if (wrapper.supports(event)) {
                if (isSync) {
                    // 同步阻塞模式
                    try {
                        wrapper.invoke(event);
                    } catch (Exception e) {
                        RcsLog.sysLog.error("同步分发事件 [{}] 异常", event.getClass().getSimpleName(), e);
                    }
                } else {
                    // 异步协程模式：只有真正需要处理该事件的监听器，才会为它开启虚拟线程！
                    asyncEventExecutor.execute(() -> {
                        try {
                            wrapper.invoke(event);
                        } catch (Exception e) {
                            // 必须捕获，防止虚拟线程静默死亡
                            RcsLog.sysLog.error("异步分发事件 [{}] 至监听器异常", event.getClass().getSimpleName(), e);
                        }
                    });
                }
            }
        }
    }

    /**
     * 阶段五：初始化回调
     * 执行 @PostConstruct 方法
     */
    private void doPostConstruct() throws Exception {
        List<Object> beans = new ArrayList<>(this.singletonObjects.values());
        //排序：Order 值小的在前 (升序)
        beans.sort((b1, b2) -> Integer.compare(getOrderValue(b1), getOrderValue(b2)));
        for (Object bean : beans) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    // 允许访问 private 方法
                    method.setAccessible(true);
                    // 执行方法
                    method.invoke(bean);
                }
            }
        }
    }

    /**
     * 获取 Bean 的优先级顺序
     * 用于 refresh 方法中的初始化排序
     */
    private int getOrderValue(Object bean) {
        if (bean == null) {
            return Integer.MAX_VALUE;
        }
        // 优先查找类上的 @Order 注解
        Order order = bean.getClass().getAnnotation(Order.class);
        if (order != null) {
            return order.value();
        }
        // 没有注解的 Bean，优先级最低，排在最后
        return Integer.MAX_VALUE;
    }

    /**
     * 阶段六：执行启动器
     * 找到所有实现了 CommandLineRunner 的类，按 @Order 排序后执行。
     */
    private void doRunners() throws Exception {
        List<CommandLineRunner> runners = singletonObjects.values().stream()
                // 筛选出实现了 CommandLineRunner 接口的 Bean
                .filter(bean -> bean instanceof CommandLineRunner)
                .map(bean -> (CommandLineRunner) bean)
                // 排序逻辑：读取 @Order 注解，值越小越靠前
                .sorted((r1, r2) -> {
                    int o1 = r1.getClass().isAnnotationPresent(Order.class) ?
                            r1.getClass().getAnnotation(Order.class).value() : Integer.MAX_VALUE;
                    int o2 = r2.getClass().isAnnotationPresent(Order.class) ?
                            r2.getClass().getAnnotation(Order.class).value() : Integer.MAX_VALUE;
                    return Integer.compare(o1, o2);
                })
                .toList();

        // 依次执行 run 方法
        for (CommandLineRunner runner : runners) {
            long t1 = System.currentTimeMillis();
            runner.run();
            // 如果执行时间超过 50ms，记录一下，方便优化启动速度
            long cost = System.currentTimeMillis() - t1;
            if (cost > 50) {
                RcsLog.sysLog.info("启动任务 [{}] 执行完毕，耗时: {} ms", runner.getClass().getSimpleName(), cost);
            }
        }
    }

    /**
     * 提供给外部获取 Bean 的方法
     */
    @Override
    public <T> T getBean(Class<T> requiredType) {
        Object bean = getBeanOrNull(requiredType);
        if (bean == null) {
            throw new RuntimeException("没有找到类： " + requiredType.getName());
        }
        return requiredType.cast(bean);
    }

    /**
     * 获取容器内所有单例 Bean
     *
     * @return 不可修改的 Bean 映射表
     */
    public Map<Class<?>, Object> getAllBeans() {
        // 返回只读视图，防止外部意外修改容器内部结构
        return Collections.unmodifiableMap(this.singletonObjects);
    }

    /**
     * 获取容器内所有指定类型的 Bean
     *
     * @param type 接口或父类类型
     * @param <T>  泛型类型
     * @return Bean 映射表
     */
    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> type) {
        Map<String, T> result = new HashMap<>();
        for (Map.Entry<Class<?>, Object> entry : singletonObjects.entrySet()) {
            Class<?> beanClass = entry.getKey();
            Object beanInstance = entry.getValue();

            // 判断 beanClass 是否实现了 type 接口，或者是 type 的子类
            if (type.isAssignableFrom(beanClass)) {
                result.put(beanClass.getSimpleName(), type.cast(beanInstance));
            }
        }
        return result;
    }

    /**
     * 获取容器内所有带指定注解的 Bean
     *
     * @param annotationType 注解类型
     * @return Bean 映射表
     */
    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<Class<?>, Object> entry : singletonObjects.entrySet()) {
            if (entry.getKey().isAnnotationPresent(annotationType)) {
                result.put(entry.getKey().getSimpleName(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * 注册 JVM 关闭钩子，确保程序退出时释放资源
     */
    @Override
    public void registerShutdownHook() {
        // 当 JVM 收到 kill 信号时，会启动这个线程
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    /**
     * 容器关闭逻辑
     */
    @Override
    public void close() {
        if (!isActive) {
            return;
        }
        RcsLog.sysLog.info("正在关闭 Framework 容器...");

        // 1. 提取所有 Bean 并进行排序
        // 销毁顺序应该与启动顺序相反：Order 值大的先销毁 (业务层 -> 基础层)
        List<Object> beans = new ArrayList<>(singletonObjects.values());
        beans.sort((b1, b2) -> {
            int o1 = b1.getClass().isAnnotationPresent(Order.class) ?
                    b1.getClass().getAnnotation(Order.class).value() : Integer.MIN_VALUE;
            int o2 = b2.getClass().isAnnotationPresent(Order.class) ?
                    b2.getClass().getAnnotation(Order.class).value() : Integer.MIN_VALUE;
            // 降序排序：Order(100) 排在 Order(0) 前面
            return Integer.compare(o2, o1);
        });

        // 2. 按顺序执行销毁回调
        for (Object bean : beans) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(PreDestroy.class)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(bean);
                    } catch (Exception e) {
                        RcsLog.sysLog.error("组件销毁异常: {}", bean.getClass().getSimpleName(), e);
                    }
                }
            }
        }

        // 3. 清理缓存
        singletonObjects.clear();
        interfaceMap.clear();
        //清理原型定义
        prototypeDefinitions.clear();
        prototypeInterfaceDefinitions.clear();
        // 关闭虚拟线程事件分发器
        asyncEventExecutor.shutdown();
        isActive = false;
        RcsLog.sysLog.info("Framework 容器已安全关闭");
        // 关闭日志系统
        LogManager.shutdown();
    }

    /**
     * 监听器包装类：缓存泛型类型，实现 O(1) 的无异常事件过滤
     */
    @Data
    private static class ApplicationListenerWrapper {
        private final Class<?> eventType;
        private final ApplicationListener<ApplicationEvent> delegate;

        @SuppressWarnings("unchecked")
        public ApplicationListenerWrapper(Class<?> eventType, ApplicationListener<?> delegate) {
            this.eventType = eventType;
            this.delegate = (ApplicationListener<ApplicationEvent>) delegate;
        }

        // 核心：无异常匹配
        public boolean supports(ApplicationEvent event) {
            return eventType.isAssignableFrom(event.getClass());
        }

        public void invoke(ApplicationEvent event) {
            delegate.onApplicationEvent(event);
        }
    }
}
