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
**com.alibaba.nacos.Nacos** 为启动类 再看上面maven脚本构建的顺序

直接看Nacos类,发现配置了包扫描,需要根据springboot的扩展来一个个看
看了com.alibaba.nacos.core.code.SpringApplicationRunListener 发现里面没什么,就单纯的是一些日志打印,工作目录准备,环境准备之类
猜测应该启动相关的东西都主要在nacos-core 的module里  继续寻找一下,在resource/META-INFO/spring.factories 下面找到了另外一个扩展点
com.alibaba.nacos.core.code.StandaloneProfileApplicationListener

com.alibaba.nacos.core.code.StandaloneProfileApplicationListener 里也没东西,线索断了
继续看core下面的包和类,在remote下看到serverMemberManager,猜测可能服务实例管理有关系,先标记一把
偶然看到NodeState,用屁股想想,这个应该是每个node的状态枚举类,必定跟服务的注册,下线有关系,于是进去看看start的引用,主要还是在serverMemberManager
于是继续研究它 

先读注解,发现这些方法必定和集群的节点有关

* nacos 的集群节点管理
* init() 集群节点管理器初始化
* shutdown() 集群节点管理器销毁
* getServerList() 获取健康成员节点的地址信息
* allMembers() 获取成员信息的对象列表
* allMembersWithoutSelf() 获取除自己外的所有集群成员节点列表
稍微找到点线索,之后可以具体分析


官方网站的quick start 
https://nacos.io/zh-cn/docs/quick-start.html 可以看到
4.服务注册&发现和配置管理
服务注册
curl -X POST 'http://127.0.0.1:8848/nacos/v1/ns/instance?serviceName=nacos.naming.serviceName&ip=20.18.7.10&port=8080'

服务发现
curl -X GET 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=nacos.naming.serviceName'

发布配置
curl -X POST "http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=nacos.cfg.dataId&group=test&content=HelloWorld"

获取配置
curl -X GET "http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=nacos.cfg.dataId&group=test"

先看服务注册部分

猜想服务注册应该是调用nacos-server 提供的接口来进行注册,并且将信息放入server的注册表中去




