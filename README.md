# 介绍 - Introduce

轻量级数据库读写分离与多数据源中间件，正在开发中。

Lightweight database read / write separation and multi data source middleware. It's Developing now.

# 使用方法 - How to use

使用它非常简单
1. 修改datasource为com.mysplitter.MySplitterDataSource，由DataSource管理连接。
2. 在项目resources目录创建mysplitter.yml文件。
3. 参考下面配置文件进行配置。

It's very easily to use "MySplitter". 
1. Change your datasource to "com.mysplitter.MySplitterDataSource".
2. Create mysplitter.yml to project resources floder.
3. Configure mysplitter.yml like "Configuration".

# 配置文件 - Configuration
### mysplitter.yml
```
mysplitter:
  common-datasource-class: com.alibaba.druid.pool.DruidDataSource # datasource factory class
  ha-mode:
    switch-opportunity: on-error # scheduled on-error-dissolve (support one)
    heartbeat-model: 
      rate: 1s # be-used (support one)
    detection-sql: SELECT 1
    died-alert-handler: com.xxx.xxx # implements com.mysplitter.advise.DatasourceDiedAlerterAdvise(optional)
  datasources: 
    datasource: # mulitple datasource
      name: default # do not change default datasource name
      # datasource-class: (optional)
      readers:
        reader:
          name: read-slave-1
          # datasource-class: (optional high priority)
          # datasource settings
        reader:
          name: read-slave-2
          # datasource-class: (optional high priority)
          # datasource settings
      writers:
        writer:
          name: write-master-1
          # datasource-class: (optional high priority)
          # datasource settings
        writer:
          name: write-slave-2
          # datasource-class: (optional high priority)
          # datasource settings
  log:
    enabled: true
    level: info
    show-sql: true
    show-sql-prttey: true
```
