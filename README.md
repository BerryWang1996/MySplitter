# MySplitter
![license-badge](https://img.shields.io/badge/license-apache%202-green.svg?style=flat-square)

## 介绍 - Introduce

轻量级数据库读写分离、多数据源、高可用、负载均衡数据库连接池中间件，正在开发中。

Lightweight read / write separation, multiple data sources, high availability, load balancing database connection middleware.
It's Developing now.

## 使用方法 - How to use

使用它非常简单
1. 修改datasource为com.mysplitter.MySplitterDataSourceRouter，由MySplitter管理连接。
2. 在项目resources目录创建mysplitter.yml文件。
3. 参考文档部分进行配置。

It's very easily to use "MySplitter". 
1. Change your datasource to "com.mysplitter.MySplitterDataSourceRouter".
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
  common:
    datasourceClass: com.alibaba.druid.pool.DruidDataSource # datasource factory class
    highAvailable:
      enableLazyLoadingDataSource: true # if true, create datasource when switch, heartbeat would't check uncreated datasource.
      switchOpportunity: on-error # scheduled on-error-dissolve (support one)
      detectionSql: SELECT 1
      heartbeatRate: 1s # s=second, m=minute, h=hour, 0=disabled
      diedAlertHandler: MyDatasourceDiedAlerter # implements com.mysplitter.advise.DatasourceDiedAlerterAdvise(optional)
    loadBalance:
      read:
        enabled: true
        strategy: polling # weight (default 1, disable when lte 0, change weight in reader or writer.)
      write:
        enabled: true
        strategy: polling # weight
  databases:
    database-a: # database-DatabaseName e.g.database-master database-192.168.1.1:3306
      # datasourceClass: (optional)
      integrates:
        integrate-slave-1:
          # datasourceClass: (optional high priority)
          configuration: # datasource configuration
            url: 192.168.1.101:3306
            username: root
            password: admin
            driverClassName: com.jdbc.mysql.Driver
      readers:
        reader-read-slave-1:
          # datasourceClass: (optional high priority)
          configuration: # datasource configuration
            url: 192.168.1.101:3306
            username: root
            password: admin
            driverClassName: com.jdbc.mysql.Driver
        reader-read-slave-2:
          # datasourceClass: (optional high priority)
          configuration: # datasource configuration
            url: 192.168.1.101:3306
            username: root
            password: admin
            driverClassName: com.jdbc.mysql.Driver
      writers:
        writer-write-master-1:
          # datasourceClass: (optional high priority)
          configuration: # datasource configuration
            url: 192.168.1.101:3306
            username: root
            password: admin
            driverClassName: com.jdbc.mysql.Driver
        writer-write-slave-2:
          # datasourceClass: (optional high priority)
          configuration: # datasource configuration
            url: 192.168.1.101:3306
            username: root
            password: admin
            driverClassName: com.jdbc.mysql.Driver
  databasesRouting: # ignore when only one database
    mode: bySqlDatabasePrefix
    # routingHandler: com.xxx.xxx # implements com.mysplitter.advise.MySplitterRoutingAdvise
  log:
    showSql: true
    showSqlPretty: true
```

### 已知问题 - Problems

// TODO

### 路由原理 - MySplitter principle

// TODO
