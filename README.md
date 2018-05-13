# MySplitter
![license-badge](https://img.shields.io/badge/license-apache%202-green.svg?style=flat-square)

## 介绍 - Introduce

轻量级数据库读写分离与多数据源中间件，正在开发中。

Lightweight database read / write separation and multi data source middleware. It's Developing now.

## 使用方法 - How to use

使用它非常简单
1. 修改datasource为com.mysplitter.MySplitterDataSourceRouter，由MySplitte管理连接。
2. 在项目resources目录创建mysplitter.yml文件。
3. 参考下面配置文件进行配置。

It's very easily to use "MySplitter". 
1. Change your datasource to "com.mysplitter.MySplitterDataSourceRouter".
2. Create mysplitter.yml to project resources floder.
3. Configure mysplitter.yml like "Configuration".

## 配置文件 - Configuration
### mysplitter.yml
```
mysplitter:
  common:
    datasourceClass: com.alibaba.druid.pool.DruidDataSource # datasource factory class
    highAvailable:
      enableLazyLoadingDataSource: true
      switchOpportunity: on-error # scheduled on-error-dissolve (support one)
      heartbeatRate: 1s # s=second, m=minute, h=hour, 0=disabled
      detectionSql: SELECT 1
      diedAlertHandler: com.xxx.xxx # implements com.mysplitter.advise.DatasourceDiedAlerterAdvise(optional)
    loadBalance:
      read:
        enabled: true
        strategy: polling # weight (default 1, min 1, change weight in reader or writer.)
        datasourceName: default
      write:
        enabled: true
        strategy: polling # weight
        datasourceName: default
  databases:
    database-default: # database-DatabaseName e.g.database-master database-192.168.1.1:3306
      # datasourceClass: (optional)
      readers:
        reader:
          name: read-slave-1
          # datasourceClass: (optional high priority)
          configuration: # datasource configuration
        reader:
          name: read-slave-2
          # datasourceClass: (optional high priority)
          configuration: # datasource configuration
      writers:
        writer:
          name: write-master-1
          # datasourceClass: (optional high priority)
          configuration: # datasource configuration
        writer:
          name: write-slave-2
          # datasourceClass: (optional high priority)
          configuration: # datasource configuration
    # database-other: ...
  databasesRouting: # ignore when only one database
    mode: bySqlDatabasePrefix
    # routingHandler: com.xxx.xxx # implements com.mysplitter.advise.MySplitterRoutingAdvise
  log:
    enabled: true
    level: info
    showSql: true
    showSqlPretty: true
```
