# MySplitter
![license-badge](https://img.shields.io/badge/license-apache%202-green.svg?style=flat-square)

## 介绍 - Introduce

轻量级数据库读写分离、多数据源、高可用、负载均衡数据库连接池中间件，正在开发中。

Lightweight read / write separation, multiple data sources, high availability, load balancing database connection middleware.
It's Developing now.

## 使用方法 - How to use

使用它非常简单
1. 修改dataSource为com.mysplitter.MySplitterDataSource，由MySplitter管理连接。
2. 在项目resources目录创建mysplitter.yml文件。
3. 参考文档部分进行配置。

It's very easily to use "MySplitter". 
1. Change your dataSource to "com.mysplitter.MySplitterDataSource".
2. Create mysplitter.yml to project resources folder.
3. Configure like the reference document.


## 文档 - Document
文档包含3部分内容
1. 配置文件
2. 已知问题
3. 路由原理

The document contains three parts
1. Configuration
2. Problems
3. MySplitter principle

### 配置文件 - Configuration
```
mysplitter:
  databasesRoutingHandler: com.mysplitter.MyDatabasesRouter # ignore when only one database. com.xxx.xxx # implements com.mysplitter.advise.MySplitterDatabasesRoutingHandlerAdvise
  filters: # implements com.mysplitter.advise.MySplitterFilterAdvise(optional)
    - com.mysplitter.MyDataSourceFilters1
    - com.mysplitter.MyDataSourceFilters2
  common:
    dataSourceClass: com.mchange.v2.c3p0.ComboPooledDataSource
    highAvailable:
      integrate:
        enabled: false
        lazyLoad: true
        detectionSql: SELECT 1
        switchOpportunity: on-error # scheduled on-error-dissolve (support one)
        healthyHeartbeatRate: 1s # s=second, m=minute, h=hour
        illHeartbeatRate: 20s # s=second, m=minute, h=hour
        illAlertHandler: com.mysplitter.MyDataSourceIllAlertHandler # implements com.mysplitter.advise.MySplitterDataSourceIllAlertAdvise(optional)
      read:
        enabled: true
        lazyLoad: true # if true, create dataSource when switch, heartbeat would't check uncreated dataSource.
        detectionSql: SELECT 1
        switchOpportunity: on-error # scheduled on-error-dissolve (support one)
        healthyHeartbeatRate: 1s # s=second, m=minute, h=hour
        illHeartbeatRate: 20s # s=second, m=minute, h=hour
        illAlertHandler: com.mysplitter.MyDataSourceIllAlertHandler # implements com.mysplitter.advise.MySplitterDataSourceIllAlertAdvise(optional)
      write:
        enabled: true
        lazyLoad: false
        detectionSql: SELECT 1
        switchOpportunity: on-error # scheduled on-error-dissolve (support one)
        healthyHeartbeatRate: 1s # s=second, m=minute, h=hour
        illHeartbeatRate: 20s # s=second, m=minute, h=hour
        illAlertHandler: com.mysplitter.MyDataSourceIllAlertHandler # implements com.mysplitter.advise.MySplitterDataSourceIllAlertAdvise(optional)
#      others:
#        enabled: true
#        lazyLoad: false
#        detectionSql: SELECT 1
#        switchOpportunity: on-error # scheduled on-error-dissolve (support one)
#        healthyHeartbeatRate: 1s # s=second, m=minute, h=hour
#        illHeartbeatRate: 20s # s=second, m=minute, h=hour
#        illAlertHandler: com.mysplitter.MyDataSourceIll # implements com.mysplitter.advise.MySplitterDataSourceIllAlertAdvise(optional)
    loadBalance:
      read:
        enabled: true
        strategy: polling # random weight (default 1, change weight in reader or writer.)
      write:
        enabled: true
        strategy: polling # random
  databases:
    database-a: # database-DatabaseName e.g.database-master database-192.168.1.1:3306
#      dataSourceClass: com.alibaba.druid.pool.DruidDataSource
      readers:
        reader-read-slave-1:
          # dataSourceClass: (optional high priority)
          configuration: # dataSource configuration
            url: 192.168.1.101:3306
            username: root
            password: admin
            driverClassName: com.jdbc.mysql.Driver
        reader-read-slave-2:
          # dataSourceClass: (optional high priority)
          configuration: # dataSource configuration
            url: 192.168.1.101:3306
            username: root
            password: admin
            driverClassName: com.jdbc.mysql.Driver
      writers:
        writer-write-master-1:
          dataSourceClass: com.alibaba.druid.pool.DruidDataSource
          configuration: # dataSource configuration
            url: 192.168.1.101:3306
            username: root
            password: admin
            driverClassName: com.jdbc.mysql.Driver
    database-b: # database-DatabaseName e.g.database-master database-192.168.1.1:3306
      # dataSourceClass: (optional)
      integrates:
        integrate-slave-1:
          dataSourceClass: com.zaxxer.hikari.HikariDataSource
          configuration: # dataSource configuration
            url: 192.168.1.101:3306
            username: root
            password: admin
            driverClassName: com.jdbc.mysql.Driver
```

### 已知问题 - Problems

// TODO

### 路由原理 - MySplitter principle

// TODO
