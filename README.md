# MySplitter
Light SQL R/W splitter.It's Developing now.

# Settings
### mysplitter.yml
```
mysplitter:
  mysplitter:
  datasource-class: com.alibaba.druid.pool.DruidDataSource # your datasource (e.g. org.apache.commons.dbcp.BasicDataSource)
  ha-mode:
    switch-opportunity: on-error # scheduled on-error-dissolve (support one)
    heartbeat-model: 
      rate: 1s # be-used (support one)
    detection-sql: SELECT 1
    died-alert-handler: 
      com.xxx.xxx # must implements com.mysplitter.advise.DatasourceDiedAlerterAdvise
  readers:
    reader:
      name: read-slave-1
      #datasource settings
    reader:
      name: read-slave-2
      #datasource settings
  writers:
    writer:
      name: write-master-1
      #datasource settings
    writer:
      name: write-slave-2
      #datasource settings
  log:
    enabled: true
    level: info
    show-sql: true
    show-sql-prttey: true
```
