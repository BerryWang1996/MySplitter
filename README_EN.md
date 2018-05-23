# MySplitter 
[![license](https://img.shields.io/badge/language-%E4%B8%AD%E6%96%87-orange.svg?style=flat-square)](https://github.com/BerryWang1996/MySplitter/blob/master/README.md)
[![license](https://img.shields.io/badge/license-apache%202-green.svg?style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0.html)

## Introduce

Lightweight read / write separation, multiple data sources, high availability, load balancing database connection middleware.
It's Developing now.

## How to use

It's very easily to use "MySplitter". 
1. Change your dataSource to "com.mysplitter.MySplitterDataSource".
2. Create mysplitter.yml to project resources folder.
3. Configure like the reference document.

## Document

The document contains three parts
1. Configuration
2. Problems
3. MySplitter principle

### Configuration
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
        enabled: true
        lazyLoad: false
        detectionSql: SELECT 1
        switchOpportunity: on-error # scheduled on-error-dissolve (support one)
        healthyHeartbeatRate: 10s # s=second, m=minute, h=hour
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

### FAQ

// TODO

### Principle

// TODO
