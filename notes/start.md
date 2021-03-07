1. 先看nacos是什么,   
``
Service is a first-class citizen in Nacos. Nacos supports discovering, configuring, and managing almost all types of services:
Kubernetes Service
gRPC & Dubbo RPC Service
Spring Cloud RESTful Service
``

官方文档表示 服务是nacos的一等公民,nacos支持服务发现,服务配置,以及大多数类型的服务管理
   包括以下的服务: k8s, grpc ,dubbo ,sc restful 服务
   SpringCloud 体系里 eureka 做服务发现,一直都缺少服务配置集中管理项目,我公司是自研的配置服务中间件,用拉的方式来实现,但是最近也在转nacos,这是潮流和趋势,应当学习学习

源码搭建
1. fork https://github.com/alibaba/nacos 到自己仓库
2. git clone 自己的nacos到本地  此处使用2.0.0-BETA分支
3. idea打开,依据BUILDING里的内容来构建  mvn -Prelease-nacos -Dmaven.test.skip=true clean install -U
5. 自己加了note 打包会报错 上面命令直接加-Drat.skip=true 跳过license检测
4. 依据构建顺序,大致知道module依赖关系 推测

```[INFO] Alibaba NACOS 2.0.0-BETA                   [pom]
   [INFO] nacos-api 2.0.0-BETA                       [jar]//定义api接口
   [INFO] nacos-common 2.0.0-BETA                    [jar]//通用
   [INFO] nacos-consistency 2.0.0-BETA               [jar]//持久化模块(推测是配置信息存库相关)
   [INFO] nacos-sys 2.0.0-BETA                       [jar]//系统相关
   [INFO] nacos-auth 2.0.0-BETA                      [jar]//权限相关
   [INFO] nacos-core 2.0.0-BETA                      [jar]//核心
   [INFO] nacos-config 2.0.0-BETA                    [jar]//配置
   [INFO] nacos-cmdb 2.0.0-BETA                      [jar]
   [INFO] nacos-naming 2.0.0-BETA                    [jar]//命名相关
   [INFO] nacos-address 2.0.0-BETA                   [jar]//地址相关
   [INFO] nacos-client 2.0.0-BETA                    [jar]//nacos客户端
   [INFO] nacos-istio 2.0.0-BETA                     [jar]
   [INFO] nacos-console 2.0.0-BETA                   [jar]//控制台
   [INFO] nacos-test 2.0.0-BETA                      [jar]//测试
   [INFO] nacos-example 2.0.0-BETA                   [jar]//demo
   [INFO] nacos-distribution 2.0.0-BETA              [pom]//发布
```

官方让用startup.sh 来启动nacos 可以看到startup.sh 脚本里实际上执行的是nacos-server.jar
```mvn -Prelease-nacos -Dmaven.test.skip=true -Drat.skip=true clean install -U```

mvn 构建命令表示使用的配置是release-nacos  ,可以直接看这个配置
```<file>
       <!--打好的jar包名称和放置目录-->
       <source>../console/target/nacos-server.jar</source>
       <outputDirectory>target/</outputDirectory>
   </file> 
```

直接找到target 看看打的包的路径,可以直接看到 
生成的tar包为 nacos/distribution/target/nacos-server-2.0.0-BETA.tar.gz 解压之后看到里面还有一层target下的jar包nacos-server 
直接解压,看MANIFEST.MF 看到如下内容

```
Manifest-Version: 1.0
Implementation-Title: nacos-console 2.0.0-BETA
Implementation-Version: 2.0.0-BETA
Archiver-Version: Plexus Archiver
Built-By: leon
Spring-Boot-Layers-Index: BOOT-INF/layers.idx
Specification-Vendor: Alibaba Group
Specification-Title: nacos-console 2.0.0-BETA
Implementation-Vendor-Id: com.alibaba.nacos
Spring-Boot-Version: 2.5.0-M1
Implementation-Vendor: Alibaba Group
Main-Class: org.springframework.boot.loader.PropertiesLauncher
Spring-Boot-Classpath-Index: BOOT-INF/classpath.idx
Start-Class: com.alibaba.nacos.Nacos
Spring-Boot-Classes: BOOT-INF/classes/
Spring-Boot-Lib: BOOT-INF/lib/
Created-By: Apache Maven 3.6.0
Build-Jdk: 1.8.0_201
Specification-Version: 2.0.0-BETA
```
** com.alibaba.nacos.Nacos ** 为启动类 再看上面maven脚本构建的顺序

接下来可以通过example 里面提供的demo来找找入口







