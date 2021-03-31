/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.listener;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.executor.ThreadPoolManager;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.file.FileChangeEvent;
import com.alibaba.nacos.sys.file.FileWatcher;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * init environment config.
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.5.0
 */
public class StartingApplicationListener implements NacosApplicationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartingApplicationListener.class);

    private static final String MODE_PROPERTY_KEY_STAND_MODE = "nacos.mode";

    private static final String MODE_PROPERTY_KEY_FUNCTION_MODE = "nacos.function.mode";

    private static final String LOCAL_IP_PROPERTY_KEY = "nacos.local.ip";

    private static final String NACOS_APPLICATION_CONF = "nacos_application_conf";

    private static final Map<String, Object> SOURCES = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduledExecutorService;

    //注意这里用volatile修饰
    private volatile boolean starting;

    @Override
    public void starting() {
        starting = true;
    }

    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        //创建工作目录logs, conf, data
        makeWorkDir();

        //环境注入,主要为赋值
        injectEnvironment(environment);

        //加载预处理配置
        loadPreProperties(environment);

        //初始化系统配置 nacos.mode  nacos.function.mode 以及本机IP
        initSystemProperty();
    }

    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        //简单cluster配置日志
        logClusterConf();
        //简单打印启动日志
        logStarting();
    }

    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {

    }

    @Override
    public void started(ConfigurableApplicationContext context) {
        starting = false;
        //关闭刚才启动的日志打印线程池
        closeExecutor();

        ApplicationUtils.setStarted(true);
        //判断储存模式,本地磁盘/mysql
        judgeStorageMode(context.getEnvironment());
    }

    @Override
    public void running(ConfigurableApplicationContext context) {
    }

    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        starting = false;

        makeWorkDir();

        LOGGER.error("Startup errors : {}", exception);
        ThreadPoolManager.shutdown();
        WatchFileCenter.shutdown();
        NotifyCenter.shutdown();

        closeExecutor();

        context.close();

        LOGGER.error("Nacos failed to start, please see {} for more details.",
                Paths.get(EnvUtil.getNacosHome(), "logs/nacos.log"));
    }

    private void injectEnvironment(ConfigurableEnvironment environment) {
        EnvUtil.setEnvironment(environment);
    }

    private void loadPreProperties(ConfigurableEnvironment environment) {
        try {
            SOURCES.putAll(EnvUtil.loadProperties(EnvUtil.getApplicationConfFileResource()));
            environment.getPropertySources()
                    .addLast(new OriginTrackedMapPropertySource(NACOS_APPLICATION_CONF, SOURCES));
            //监听器监听文件改变事件,实际默认为nacos/conf文件夹
            registerWatcher();
        } catch (Exception e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }

    private void registerWatcher() throws NacosException {

        WatchFileCenter.registerWatcher(EnvUtil.getConfPath(), new FileWatcher() {
            @Override
            public void onChange(FileChangeEvent event) {
                try {
                    Map<String, ?> tmp = EnvUtil.loadProperties(EnvUtil.getApplicationConfFileResource());
                    SOURCES.putAll(tmp);
                } catch (IOException ignore) {
                    LOGGER.warn("Failed to monitor file {}", ignore);
                }
            }

            @Override
            public boolean interest(String context) {
                return StringUtils.contains(context, "application.properties");
            }
        });

    }

    private void initSystemProperty() {
        if (EnvUtil.getStandaloneMode()) {
            System.setProperty(MODE_PROPERTY_KEY_STAND_MODE, "stand alone");
        } else {
            System.setProperty(MODE_PROPERTY_KEY_STAND_MODE, "cluster");
        }
        if (EnvUtil.getFunctionMode() == null) {
            System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, "All");
        } else if (EnvUtil.FUNCTION_MODE_CONFIG.equals(EnvUtil.getFunctionMode())) {
            System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, EnvUtil.FUNCTION_MODE_CONFIG);
        } else if (EnvUtil.FUNCTION_MODE_NAMING.equals(EnvUtil.getFunctionMode())) {
            System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, EnvUtil.FUNCTION_MODE_NAMING);
        }

        System.setProperty(LOCAL_IP_PROPERTY_KEY, InetUtils.getSelfIP());
    }

    private void logClusterConf() {
        if (!EnvUtil.getStandaloneMode()) {
            try {
                List<String> clusterConf = EnvUtil.readClusterConf();
                LOGGER.info("The server IP list of Nacos is {}", clusterConf);
            } catch (IOException e) {
                LOGGER.error("read cluster conf fail", e);
            }
        }
    }

    private void closeExecutor() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }
    }

    private void makeWorkDir() {
        String[] dirNames = new String[] {"logs", "conf", "data"};
        for (String dirName : dirNames) {
            LOGGER.info("Nacos Log files: {}", Paths.get(EnvUtil.getNacosHome(), dirName).toString());
            try {
                DiskUtils.forceMkdir(new File(Paths.get(EnvUtil.getNacosHome(), dirName).toUri()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void logStarting() {
        if (!EnvUtil.getStandaloneMode()) {

            scheduledExecutorService = ExecutorFactory
                    .newSingleScheduledExecutorService(new NameThreadFactory("com.alibaba.nacos.core.nacos-starting"));

            scheduledExecutorService.scheduleWithFixedDelay(() -> {
                if (starting) {
                    LOGGER.info("Nacos is starting...");
                }
            }, 1, 1, TimeUnit.SECONDS);
        }
    }

    private void judgeStorageMode(ConfigurableEnvironment env) {

        // External data sources are used by default in cluster mode
        boolean useExternalStorage = ("mysql".equalsIgnoreCase(env.getProperty("spring.datasource.platform", "")));

        // must initialize after setUseExternalDB
        // This value is true in stand-alone mode and false in cluster mode
        // If this value is set to true in cluster mode, nacos's distributed storage engine is turned on
        // default value is depend on ${nacos.standalone}

        if (!useExternalStorage) {
            boolean embeddedStorage = EnvUtil.getStandaloneMode() || Boolean.getBoolean("embeddedStorage");
            // If the embedded data source storage is not turned on, it is automatically
            // upgraded to the external data source storage, as before
            if (!embeddedStorage) {
                useExternalStorage = true;
            }
        }

        LOGGER.info("Nacos started successfully in {} mode. use {} storage",
                System.getProperty(MODE_PROPERTY_KEY_STAND_MODE), useExternalStorage ? "external" : "embedded");
    }
}
